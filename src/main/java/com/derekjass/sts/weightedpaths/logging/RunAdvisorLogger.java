package com.derekjass.sts.weightedpaths.logging;

import com.derekjass.sts.weightedpaths.card.ScoreBreakdown;
import com.derekjass.sts.weightedpaths.ui.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 决策日志：开启后在 {@code ~/RunAdvisorLogs/} 写入每局 run_summary.json，供离线分析。
 */
public final class RunAdvisorLogger {

    private static final Logger logger = LogManager.getLogger(RunAdvisorLogger.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static RunLogModels.RunSummary current;
    private static BufferedWriter writer;
    private static String currentFilePath;
    /** 最近一次记录的卡奖（供玩家选择后回填 playerChosen）。 */
    private static RunLogModels.CardRewardLog lastReward;

    private RunAdvisorLogger() {
    }

    public static boolean isEnabled() {
        return Config.enableDecisionLog();
    }

    public static void ensureSession() {
        if (!isEnabled() || AbstractDungeon.player == null || Settings.seed == null) {
            return;
        }
        String seed = Settings.seed.toString();
        long ts = Settings.seedSourceTimestamp;
        // 用 seed + seedSourceTimestamp 识别对局（与 SL 检测同一指纹）：
        // 仅比 seed 会在「同 seed 弃局后重开」时复用上一局日志，数据串局
        if (current == null || !seed.equals(current.seed) || ts != current.seedSourceTimestamp) {
            onRunStart();
        }
    }

    public static void onRunStart() {
        if (!isEnabled() || AbstractDungeon.player == null) {
            return;
        }
        closeWriterQuietly();
        current = new RunLogModels.RunSummary();
        lastReward = null;
        current.runId = UUID.randomUUID().toString().substring(0, 8);
        current.startedAtMs = System.currentTimeMillis();
        current.seed = Settings.seed == null ? "" : Settings.seed.toString();
        current.seedSourceTimestamp = Settings.seedSourceTimestamp;
        current.ascension = AbstractDungeon.ascensionLevel;
        current.character = characterName(AbstractDungeon.player);
        current.meta.put("modVersion", "1.5.0");

        try {
            File dir = logDirectory();
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Could not create RunAdvisorLogs directory: {}", dir.getAbsolutePath());
                current = null;
                return;
            }
            com.derekjass.sts.weightedpaths.patches.CardRewardRenderPatch.resetRewardLogCache();
            File file = new File(dir, "run_" + current.seed + "_" + current.runId + ".json");
            currentFilePath = file.getAbsolutePath();
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8));
            flushSummary();
            logger.info("RunAdvisor decision log started: {}", currentFilePath);
        } catch (IOException e) {
            logger.error("Failed to open run log file.", e);
            current = null;
            closeWriterQuietly();
        }
    }

    public static void logMapDecision(
            String chosenNodeType,
            String recommendedNext,
            double pathValue,
            boolean act1EliteReady,
            double hpRatio,
            int estimatedRestAhead,
            Map<String, Double> roomWeights,
            Map<String, String> weightNotes) {
        if (!isEnabled() || current == null || AbstractDungeon.player == null) {
            return;
        }
        RunLogModels.NodeDecision node = new RunLogModels.NodeDecision();
        node.floor = AbstractDungeon.floorNum;
        node.act = AbstractDungeon.actNum;
        node.nodeType = chosenNodeType == null ? "" : chosenNodeType;
        node.recommendedNext = recommendedNext == null ? "" : recommendedNext;
        node.pathValue = pathValue;
        node.act1EliteReady = act1EliteReady;
        node.hpRatio = hpRatio;
        node.estimatedRestAhead = estimatedRestAhead;
        if (roomWeights != null) {
            node.roomWeights.putAll(roomWeights);
        }
        if (weightNotes != null) {
            node.weightNotes.putAll(weightNotes);
        }
        current.nodeDecisions.add(node);
        flushSummary();
    }

    public static void logCardReward(
            boolean skipAll,
            List<RunLogModels.CardChoiceLog> choices) {
        if (!isEnabled() || current == null || AbstractDungeon.player == null) {
            return;
        }
        RunLogModels.CardRewardLog reward = new RunLogModels.CardRewardLog();
        reward.floor = AbstractDungeon.floorNum;
        reward.act = AbstractDungeon.actNum;
        reward.hpRatio = AbstractDungeon.player.maxHealth > 0
                ? (double) AbstractDungeon.player.currentHealth / AbstractDungeon.player.maxHealth
                : 1.0;
        reward.recommendedSkipAll = skipAll;
        if (choices != null) {
            reward.choices.addAll(choices);
        }
        current.cardRewards.add(reward);
        lastReward = reward;
        flushSummary();
    }

    /**
     * 记录玩家在最近一次卡奖的实际选择（卡奖关闭时调用，回填 playerChosen/playerSkipped）。
     *
     * @param cardId 玩家实际抓的卡 ID；跳过时传空串。
     * @param skipped 玩家是否跳过了本次卡奖。
     */
    public static void logPlayerCardChoice(String cardId, boolean skipped) {
        if (!isEnabled() || current == null || lastReward == null) {
            return;
        }
        lastReward.playerChosen = cardId == null ? "" : cardId;
        lastReward.playerSkipped = skipped;
        flushSummary();
    }

    /**
     * 标记最近一次卡奖为「记仇使坏」，并把日志中 recommended 重排为实际展示的使坏推荐卡。
     * 供离线分析排除/单列使坏卡奖，避免污染「推荐 vs 违推荐」统计（日志记录的是规则推荐，
     * 而屏幕展示的是使坏推荐，两口径必须对齐）。
     *
     * @param mischiefCardId 使坏推荐的（最差）卡 ID
     */
    public static void markLastRewardMischief(String mischiefCardId) {
        if (!isEnabled() || current == null || lastReward == null) {
            return;
        }
        lastReward.mischief = true;
        if (mischiefCardId != null && !mischiefCardId.isEmpty()) {
            for (RunLogModels.CardChoiceLog c : lastReward.choices) {
                c.recommended = mischiefCardId.equals(c.cardId);
            }
        }
        flushSummary();
    }

    /**
     * 取当前局玩家实际抓过的卡 ID（结算长期陪伴记忆用）。
     * 必须在 {@link #onRunEnd} 清空 current 之前调用。
     *
     * @return 玩家抓过的卡 ID 列表（含玩家选择时回填的 playerChosen，跳过没抓的）
     */
    public static java.util.List<String> currentPickedCardIds() {
        if (!isEnabled() || current == null || current.cardRewards == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> picked = new java.util.ArrayList<>();
        for (RunLogModels.CardRewardLog r : current.cardRewards) {
            if (r != null && r.playerChosen != null && !r.playerChosen.trim().isEmpty()) {
                picked.add(r.playerChosen.trim());
            }
        }
        return picked;
    }

    public static RunLogModels.CardChoiceLog choiceLog(
            int index,
            String cardId,
            String grade,
            double score,
            boolean recommended,
            ScoreBreakdown breakdown) {
        RunLogModels.CardChoiceLog c = new RunLogModels.CardChoiceLog();
        c.rewardIndex = index;
        c.cardId = cardId == null ? "" : cardId;
        c.grade = grade == null ? "" : grade;
        c.finalScore = score;
        c.recommended = recommended;
        if (breakdown != null) {
            RunLogModels.ScoreBreakdownLog b = new RunLogModels.ScoreBreakdownLog();
            b.baseScore = breakdown.baseScore;
            b.survivalBonus = breakdown.survivalBonus;
            b.portMult = breakdown.portMult;
            b.synergyMult = breakdown.synergyMult;
            b.pollutionMult = breakdown.pollutionMult;
            b.calibrationMult = breakdown.calibrationMult;
            c.breakdown = b;
        }
        return c;
    }

    public static void onRunEnd(boolean victory, String reason) {
        if (!isEnabled() || current == null) {
            closeWriterQuietly();
            current = null;
            return;
        }
        current.endedAtMs = System.currentTimeMillis();
        current.victory = victory;
        current.endReason = reason == null ? "" : reason;
        if (AbstractDungeon.player != null) {
            current.floorReached = AbstractDungeon.floorNum;
            current.endHp = AbstractDungeon.player.currentHealth;
            current.maxHp = AbstractDungeon.player.maxHealth;
        }
        current.actReached = AbstractDungeon.actNum;
        flushSummary();
        closeWriterQuietly();
        logger.info("RunAdvisor decision log finished: {} victory={}", currentFilePath, victory);
        current = null;
    }

    public static File logDirectory() {
        return new File(System.getProperty("user.home"), "RunAdvisorLogs");
    }

    public static String currentLogPath() {
        return currentFilePath == null ? "" : currentFilePath;
    }

    private static void flushSummary() {
        if (current == null || currentFilePath == null || currentFilePath.isEmpty()) {
            return;
        }
        // 修复：每次 flush 用覆盖模式重写整个文件，避免 BufferedWriter 多次 write 在
        // 同一句柄上从上次偏移继续写导致 JSON 拼接（Extra data 解析失败）。
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(currentFilePath), StandardCharsets.UTF_8))) {
            w.write(GSON.toJson(current));
            w.flush();
        } catch (IOException e) {
            logger.error("Failed to write run log.", e);
        }
    }

    private static void closeWriterQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    private static String characterName(AbstractPlayer player) {
        if (player == null || player.chosenClass == null) {
            return "Unknown";
        }
        return player.chosenClass.name();
    }
}
