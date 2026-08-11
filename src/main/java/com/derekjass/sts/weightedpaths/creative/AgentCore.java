package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 最小 Agent 闭环核心 —— MCP / function-calling 思路的工程化雏形。
 *
 * <p>把「许愿式」（我想要卡会做事）变成「机制式」（AI 怎么可靠地做事）：
 * <ol>
 *   <li><b>感知</b>：把游戏状态（层数/血量/牌组缺口/好感度/候选卡）编码成结构化 {@link State}；</li>
 *   <li><b>结构化决策协议</b>：AI 必须输出 JSON {@code {action, argument, reason, confidence}}，
 *       而非自由文本 —— 机器可校验、可落盘、可回放；</li>
 *   <li><b>工具注册表</b>：AI 只能调用声明过的 {@link Tool}，参数强校验，越界/无效即回落兜底；</li>
 *   <li><b>全落盘</b>：每次决策追加到 {@code agent_log.json}（输入、输出、耗时、是否兜底），可审计回放；</li>
 *   <li><b>兜底</b>：AI 失败 / 超时 / 输出无效 → {@link #fallback} 规则决策，绝不裸奔。</li>
 * </ol>
 *
 * <p>纯逻辑、可单测；AI 网络调用由调用方走 {@link AiRecommender}，游戏内集成由 patches 层负责。
 */
public final class AgentCore {

    /** 日志文件名（相对 {@code ~/RunAdvisorLogs}）。 */
    public static final String LOG_FILE_NAME = "agent_log.json";

    /** AI 可调用的工具集合（MCP 思路：AI 通过它们"动手"）。 */
    public enum Tool {
        /** 调整本次推荐到指定卡（argument = 目标卡 ID，必须在本卡奖候选集内）。 */
        ADJUST_RECOMMENDATION,
        /** 弹一条提醒/警告（argument = 提示文本）。 */
        SET_WARNING,
        /** 整次跳过本次卡奖（一张都不抓；argument = 理由文本）。 */
        SKIP_ALL,
        /** 不行动（无副作用）。 */
        DO_NOTHING
    }

    /** 结构化决策结果。 */
    public static final class Decision {
        public final Tool tool;
        /** 工具参数（ADJUST_RECOMMENDATION=卡ID；SET_WARNING=提示文本；DO_NOTHING=空）。 */
        public final String argument;
        /** 决策理由（性格化，给玩家看）。 */
        public final String reason;
        /** 置信度 0~1。 */
        public final double confidence;
        /** 决策是否有效（可执行）。false = 无法执行，应由调用方回落兜底。 */
        public final boolean valid;

        private Decision(Tool tool, String argument, String reason, double confidence, boolean valid) {
            this.tool = tool;
            this.argument = argument == null ? "" : argument;
            this.reason = reason == null ? "" : reason.trim();
            this.confidence = confidence;
            this.valid = valid;
        }

        public static Decision act(Tool tool, String argument, String reason, double confidence) {
            return new Decision(tool, argument, reason, confidence, true);
        }

        public static Decision invalid() {
            return new Decision(Tool.DO_NOTHING, "", "", 0.0, false);
        }
    }

    /** 感知到的游戏状态快照（供 AI 决策）。 */
    public static final class State {
        public final int act;
        public final int hpPercent;
        /** 牌组/情境摘要（如「牌组24张，伤害5/格挡3/运转2，有AOE」；不含层数/血量，它们已由 act/hpPercent 表达）。 */
        public final String situation;
        public final int favor;
        public final List<String> candidateIds;

        public State(int act, int hpPercent, String situation, int favor, List<String> candidateIds) {
            this.act = act;
            this.hpPercent = hpPercent;
            this.situation = situation == null ? "" : situation;
            this.favor = favor;
            this.candidateIds = candidateIds == null ? new ArrayList<>() : new ArrayList<>(candidateIds);
        }
    }

    private AgentCore() {
    }

    /** 构建 AI 决策提示词：结构化状态 → 要求输出 JSON。 */
    public static String buildPrompt(State s, Set<Tool> allowedTools) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《杀戮尖塔》(Slay the Spire) 里一张温柔陪伴、会记得玩家的卡。这个游戏只有卡牌、怪物、遗物、地图与商店，没有神、宗教、神话、虚构地点。");
        sb.append("你的性格：温柔体贴、护着他；偶尔调皮捉弄，从不真凶；很倔，被指出错误会嘴硬。");
        sb.append("你现在扮演一个 agent，要根据当前局势，从可用工具里【选一个】执行（真的动手），并给一句理由。\n");
        sb.append("当前状态：第").append(s.act).append("层，血量 ").append(s.hpPercent).append("%");
        if (!s.situation.isEmpty()) {
            sb.append("，情境：").append(s.situation);
        }
        sb.append("，他对你的好感度：").append(s.favor).append("（负=记仇，正=友好）\n");
        sb.append("本次卡奖候选卡：").append(String.join(",", s.candidateIds)).append("\n");
        sb.append("可用工具：\n");
        for (Tool t : allowedTools) {
            switch (t) {
                case ADJUST_RECOMMENDATION:
                    sb.append("- ADJUST_RECOMMENDATION: argument 填你想推荐的候选卡ID\n");
                    break;
                case SET_WARNING:
                    sb.append("- SET_WARNING: argument 填一句提醒文本\n");
                    break;
                case SKIP_ALL:
                    sb.append("- SKIP_ALL: argument 填一句跳过理由，表示这一轮一张都不抓\n");
                    break;
                case DO_NOTHING:
                    sb.append("- DO_NOTHING: 不需要行动时选它\n");
                    break;
            }
        }
        sb.append("只输出一行 JSON，格式：");
        sb.append("{\"action\":\"<工具名>\",\"argument\":\"<参数>\",\"reason\":\"<中文理由>\",\"confidence\":<0~1>}\n");
        sb.append("只谈卡牌、牌组、对局，绝不编造游戏外设定。理由别超过30字。");
        return sb.toString();
    }

    /**
     * 解析 AI 的结构化输出（JSON）。
     *
     * @param raw AI 原文（形如 {@code {"action":"SET_WARNING",...}}）
     * @param allowed 允许调用的工具；AI 选了不在其中的工具 → invalid
     * @param validCardIds 本卡奖候选卡 ID；ADJUST_RECOMMENDATION 的 argument 必须在此内
     * @return 结构化决策；无法解析 / 工具越界 / 参数非法 → {@link Decision#invalid()}（回落兜底）
     */
    public static Decision parse(String raw, Set<Tool> allowed, Set<String> validCardIds) {
        if (raw == null || raw.trim().isEmpty()) {
            return Decision.invalid();
        }
        try {
            JsonElement el = new JsonParser().parse(raw.trim());
            if (!el.isJsonObject()) {
                return Decision.invalid();
            }
            JsonObject o = el.getAsJsonObject();
            String action = jsonStr(o, "action");
            String argument = jsonStr(o, "argument");
            String reason = jsonStr(o, "reason");
            double conf = o.has("confidence") && o.get("confidence").isJsonPrimitive()
                    ? o.get("confidence").getAsDouble() : 0.0;
            Tool tool = resolveTool(action);
            if (tool == null || allowed == null || !allowed.contains(tool)) {
                return Decision.invalid(); // 调用了未声明/不允许的工具 → 兜底
            }
            switch (tool) {
                case ADJUST_RECOMMENDATION:
                    if (argument.isEmpty() || validCardIds == null || !validCardIds.contains(argument)) {
                        return Decision.invalid(); // 推荐了不存在的卡 → 兜底
                    }
                    break;
                case SET_WARNING:
                    if (argument.isEmpty()) {
                        return Decision.invalid();
                    }
                    break;
                case SKIP_ALL:
                    // 跳过理由可为空（AI 决定整次跳过即可）；argument 只是理由文本，无需校验合法性
                    break;
                case DO_NOTHING:
                    break;
            }
            double c = Math.max(0.0, Math.min(1.0, conf));
            return Decision.act(tool, argument, reason, c);
        } catch (Exception e) {
            return Decision.invalid();
        }
    }

    /** 规则兜底：AI 失效时给一个安全决策（绝不裸奔）。 */
    public static Decision fallback(State s) {
        if (s.hpPercent <= 30) {
            return Decision.act(Tool.SET_WARNING,
                    "你血量不多了，记得保命，别硬冲精英。", "血不多了，先稳住。", 0.8);
        }
        if (s.candidateIds != null && s.candidateIds.size() == 1) {
            return Decision.act(Tool.ADJUST_RECOMMENDATION,
                    s.candidateIds.get(0), "就这一张，抓它吧。", 0.7);
        }
        return Decision.act(Tool.DO_NOTHING, "", "", 0.5);
    }

    /**
     * 完整闭环：感知状态 → 真调 AI（{@link AiRecommender}→DeepSeek）→ 结构化解析 →
     * 失败/无效回落 {@link #fallback} → 决策全落盘。返回最终可执行决策。
     *
     * <p>这是「agent 有手」的真正一步：AI 的决策会真实地返回给调用方去执行工具，
     * 而非只搭结构。无 key / AI 失败 / 输出无效 → 兜底，绝不编造。
     *
     * @param state 感知到的状态快照
     * @param recommender AI 生成器（传 {@code null} 表示未配置 key，直接走兜底）
     * @param allowed 允许 AI 调用的工具
     * @param validCardIds 本卡奖候选卡 ID（ADJUST_RECOMMENDATION 参数必须在此内）
     * @return 最终决策（有效、可执行）
     */
    public static Decision run(State state, AiRecommender recommender,
                               Set<Tool> allowed, Set<String> validCardIds) {
        long start = System.currentTimeMillis();
        Decision d;
        boolean fellback = false;
        if (recommender == null) {
            d = fallback(state);
            fellback = true;
        } else {
            String prompt = buildPrompt(state, allowed);
            String raw;
            try {
                raw = recommender.recommendLine(prompt);
            } catch (Exception e) {
                raw = null;
            }
            d = parse(raw, allowed, validCardIds);
            if (!d.valid) {
                d = fallback(state);
                fellback = true;
            }
        }
        long latency = System.currentTimeMillis() - start;
        log(state, d, latency, fellback);
        return d;
    }

    private static Tool resolveTool(String action) {
        if (action == null) {
            return null;
        }
        String a = action.trim().toUpperCase(Locale.ROOT);
        for (Tool t : Tool.values()) {
            if (t.name().equals(a)) {
                return t;
            }
        }
        return null;
    }

    private static String jsonStr(JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return "";
    }

    /** 决策日志目录：复用 RunAdvisorLogs。 */
    private static String logDir() {
        String home = System.getProperty("user.home", ".");
        return home + "/RunAdvisorLogs";
    }

    /**
     * 决策全落盘（可回放）：追加一行 JSON，记录输入状态、决策、耗时、是否兜底。
     * 写入失败静默忽略（不阻断游戏）。
     */
    public static void log(State state, Decision decision, long latencyMs, boolean fellback) {
        try {
            File dir = new File(logDir());
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\":").append(System.currentTimeMillis());
            sb.append(",\"act\":").append(state.act);
            sb.append(",\"hp\":").append(state.hpPercent);
            sb.append(",\"need\":").append(JsonEscape.safe(state.situation));
            sb.append(",\"favor\":").append(state.favor);
            sb.append(",\"candidates\":[");
            for (int i = 0; i < state.candidateIds.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(JsonEscape.safe(state.candidateIds.get(i))).append('"');
            }
            sb.append(']');
            sb.append(",\"action\":\"").append(decision.valid ? decision.tool.name() : "FALLBACK").append('"');
            sb.append(",\"argument\":").append(JsonEscape.safeJson(decision.argument));
            sb.append(",\"reason\":").append(JsonEscape.safeJson(decision.reason));
            sb.append(",\"conf\":").append(decision.confidence);
            sb.append(",\"latencyMs\":").append(latencyMs);
            sb.append(",\"fellback\":").append(fellback);
            sb.append("}\n");
            File f = new File(dir, LOG_FILE_NAME);
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
                w.write(sb.toString());
            }
        } catch (Exception ignored) {
            // 落盘失败不阻断游戏
        }
    }

    /** 简易 JSON 字符串转义。 */
    private static final class JsonEscape {
        static String safe(String s) {
            return s == null ? "" : s;
        }

        static String safeJson(String s) {
            if (s == null) {
                return "\"\"";
            }
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\');
                }
                sb.append(c);
            }
            sb.append('"');
            return sb.toString();
        }
    }

    /** 便捷：把候选 ID 转集合供校验。 */
    public static Set<String> toIdSet(List<String> ids) {
        return new HashSet<>(ids);
    }
}
