package com.derekjass.sts.weightedpaths.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.card.CardRecommendation;
import com.derekjass.sts.weightedpaths.card.CardScorer;
import com.derekjass.sts.weightedpaths.card.DeckAnalyzer;
import com.derekjass.sts.weightedpaths.card.DeckSnapshot;
import com.derekjass.sts.weightedpaths.card.GlobalRunPlan;
import com.derekjass.sts.weightedpaths.card.PortProfile;
import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine;
import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine.AiRecommendation;
import com.derekjass.sts.weightedpaths.creative.AgentBridge;
import com.derekjass.sts.weightedpaths.creative.AgentCore;
import com.derekjass.sts.weightedpaths.creative.AiRecommender;
import com.derekjass.sts.weightedpaths.creative.CardAttitudeEngine;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.derekjass.sts.weightedpaths.creative.DeepSeekAiRecommender;
import com.derekjass.sts.weightedpaths.creative.RecommendationPolicy;
import com.derekjass.sts.weightedpaths.creative.PactManager;
import com.derekjass.sts.weightedpaths.logging.RunAdvisorLogger;
import com.derekjass.sts.weightedpaths.logging.RunLogModels;
import com.derekjass.sts.weightedpaths.card.data.CardStatsLoader;
import com.derekjass.sts.weightedpaths.ui.CardUiStrings;
import com.derekjass.sts.weightedpaths.ui.ModFonts;
import com.derekjass.sts.weightedpaths.ui.config.Config;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.TheSilent;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.screens.CardRewardScreen;

import java.util.List;

public class CardRewardRenderPatch {

    private static final Color GRADE_S = new Color(1.0f, 0.85f, 0.2f, 1.0f);
    private static final Color GRADE_A = new Color(0.45f, 0.95f, 0.45f, 1.0f);
    private static final Color GRADE_B = new Color(0.85f, 0.85f, 0.85f, 1.0f);
    private static final Color GRADE_C = new Color(0.95f, 0.45f, 0.45f, 1.0f);
    private static final Color RECOMMENDED_COLOR = new Color(0.35f, 1.0f, 0.55f, 1.0f);
    private static final Color SKIP_HINT_COLOR = new Color(1.0f, 0.72f, 0.35f, 1.0f);
    // 记仇使坏推荐的标签色：橙色，提醒玩家「这是在逗你」，不是真推荐
    private static final Color MISCHIEF_COLOR = new Color(1.0f, 0.72f, 0.35f, 1.0f);
    // 抓牌历史"已有 N 张"的次要文字色
    private static final Color HAVING_COLOR = new Color(0.78f, 0.78f, 0.78f, 1.0f);
    private static String lastRewardLogKey = "";
    /** 本次卡奖推荐抓的卡 ID（供 onClose 检测违背）。 */
    private static String lastRecommendedId = "";
    /** 本次卡奖是否推荐整次跳过。 */
    private static boolean lastSkipAll = false;
    /** 本次卡奖各候选卡的 grade（供 onClose 判断弃高抓低）。 */
    private static final java.util.Map<String, String> lastRewardGrades = new java.util.HashMap<>();
    /** 玩家本轮卡奖实际抓的卡 ID（acquireCard 记录；空=未抓/跳过）。 */
    private static String playerPickedId = "";
    /** 卡 ID → 中文名（供台词显示中文卡名）。 */
    private static final java.util.Map<String, String> cardNameMap = new java.util.HashMap<>();

    /** 卡 ID → 中文名（供长期陪伴台词显示中文卡名）。未映射时回退原 ID。 */
    public static String chineseNameOf(String cardId) {
        if (cardId == null || cardId.isEmpty()) {
            return "";
        }
        String n = cardNameMap.get(cardId);
        return (n == null || n.isEmpty()) ? cardId : n;
    }

    /** 当前卡奖的 AI 拍板决策（volatile，异步线程写入；null=未配置/未返回/无效→回落规则）。 */
    private static volatile AiRecommendation aiRec;
    /** 已发起 AI 请求的卡奖 key（避免每帧重复请求）。 */
    private static volatile String aiRequestedKey = "";
    /** 本次卡奖是否为「记仇使坏」（AI 故意推坏卡逗玩家）。 */
    private static volatile boolean lastWasMischief = false;
    /** 是否已给 CardAttitudeEngine 注入主线程调度器（只注入一次，避免重复）。 */
    private static boolean attitudeDispatcherInjected = false;

    public static void resetRewardLogCache() {
        lastRewardLogKey = "";
    }

    /** 给 CardAttitudeEngine 注入主线程调度器（只注入一次）：AI 结果回主线程再改共享台词状态，避免竞态。 */
    private static void ensureAttitudeDispatcherInjected() {
        if (!attitudeDispatcherInjected) {
            CardAttitudeEngine.setDispatcher(com.badlogic.gdx.Gdx.app::postRunnable);
            attitudeDispatcherInjected = true;
        }
    }

    private CardRewardRenderPatch() {
    }

    @SpirePatch(clz = CardRewardScreen.class, method = "render")
    public static class PostRenderPatch {

        @SpirePostfixPatch
        public static void afterRender(CardRewardScreen __instance, SpriteBatch sb) {
            if (!Config.showCardScores()) {
                return;
            }
            if (!CardStatsLoader.isLoaded()) {
                return;
            }
            if (AbstractDungeon.player == null || !(AbstractDungeon.player instanceof TheSilent)) {
                return;
            }
            if (__instance.rewardGroup == null || __instance.rewardGroup.isEmpty()) {
                return;
            }

            List<AbstractCard> deck = AbstractDungeon.player.masterDeck.group;
            GlobalRunPlan plan = GlobalRunPlan.fromCurrentRun();
            java.util.HashMap<AbstractCard, CardRecommendation> scored = new java.util.HashMap<>();
            java.util.HashMap<AbstractCard, CardScorer.ScoredResult> detailed = new java.util.HashMap<>();
            double bestScore = 0.0;
            AbstractCard bestCard = null;

            for (AbstractCard card : __instance.rewardGroup) {
                if (card == null) {
                    continue;
                }
                CardScorer.ScoredResult result = CardScorer.scoreRewardOptionDetailed(card, deck, plan);
                detailed.put(card, result);
                scored.put(card, result.recommendation);
                if (result.recommendation.score > bestScore) {
                    bestScore = result.recommendation.score;
                    bestCard = card;
                }
            }

            double skipThreshold = com.derekjass.sts.weightedpaths.card.FourLayerScorer.SKIP_THRESHOLD
                    + (PactManager.isConservativeAdviceActive() ? 5.0 : 0.0);
            boolean skipAll = bestScore < skipThreshold;
            // 三选一里最高分至少显示 B，避免「全 C 无指引」
            if (!skipAll && bestCard != null) {
                CardRecommendation bestRec = scored.get(bestCard);
                if (bestRec != null && bestRec.grade == com.derekjass.sts.weightedpaths.card.CardGrade.C
                        && bestScore >= 38.0) {
                    scored.put(bestCard, new CardRecommendation(
                            com.derekjass.sts.weightedpaths.card.CardGrade.B, bestRec.score, bestRec.reason));
                }
            }
            // 信任调整（机制层）：不友好时确定性推荐失真（不依赖 AI/key，总是生效）；AI 有效决策随后覆盖
            RecommendationPolicy.Decision policy = RecommendationPolicy.decide(
                    buildPolicyCandidates(__instance, scored),
                    com.derekjass.sts.weightedpaths.creative.CardMoodEngine.favor());
            if (!skipAll && policy.trustAdjusted && policy.cardId != null) {
                AbstractCard picked = findById(__instance, policy.cardId);
                if (picked != null) {
                    bestCard = picked;
                    CardRecommendation rec = scored.get(picked);
                    if (rec != null && rec.reason != null && !rec.reason.contains("闹脾气")) {
                        scored.put(picked, new CardRecommendation(rec.grade, rec.score, rec.reason + "（它在闹脾气，推荐可能失真）"));
                    }
                }
            }

            maybeLogCardReward(__instance, scored, detailed, bestCard, skipAll,
                    !skipAll && policy.trustAdjusted, policy.trustFactor);

            // AI 拍板推荐：异步请求（每个卡奖一次），返回前渲染走规则兜底
            requestAiDecision(__instance, detailed, skipAll, buildDeckContext());

            // AI 拍板推荐：有效时覆盖「推荐指向 + 是否跳过」，带性格化理由
            AiRecommendation ai = aiRec;
            if (ai != null && ai.valid) {
                if (ai.skipAll) {
                    skipAll = true;
                    bestCard = null;
                } else {
                    skipAll = false;
                    bestCard = findById(__instance, ai.recommendedId);
                }
            }

            // 状态机同步：把最终显示的推荐同步给违背检测（AI 异步到达/信任调整后，显示与记录保持一致）
            lastRecommendedId = bestCard == null ? "" : bestCard.cardID;
            lastSkipAll = skipAll;

            boolean isMischief = lastWasMischief && ai != null && ai.valid && !ai.skipAll;
            for (AbstractCard card : __instance.rewardGroup) {
                if (card == null) {
                    continue;
                }
                CardRecommendation rec = scored.get(card);
                if (rec == null) {
                    continue;
                }
                boolean isPick = !skipAll && card == bestCard;
                drawRecommendation(sb, card, rec, isPick, ai, isMischief);
            }

            if (skipAll) {
                drawSkipHint(sb, ai);
            }
        }
    }

    /** 在本次卡奖候选里按卡 ID 找卡（AI 推荐的卡必须来自候选集，已由引擎校验）。 */
    private static AbstractCard findById(CardRewardScreen screen, String cardId) {
        if (cardId == null || screen == null || screen.rewardGroup == null) {
            return null;
        }
        for (AbstractCard card : screen.rewardGroup) {
            if (card != null && cardId.equals(card.cardID)) {
                return card;
            }
        }
        return null;
    }

    /** 触发 AI 拍板请求：每个卡奖只发一次（按 reward key 去重），异步不阻塞渲染。 */
    private static void requestAiDecision(CardRewardScreen screen,
                                          java.util.HashMap<AbstractCard, CardScorer.ScoredResult> detailed,
                                          boolean skipAll, String deckContext) {
        String key = currentRewardKey(screen);
        if (key.isEmpty() || key.equals(aiRequestedKey)) {
            return;
        }
        aiRequestedKey = key;
        aiRec = null; // 新卡奖：清空旧决策，渲染先回落规则兜底
        java.util.ArrayList<AiRecommendationEngine.Candidate> candidates = new java.util.ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (AbstractCard card : screen.rewardGroup) {
            if (card == null) {
                continue;
            }
            CardScorer.ScoredResult r = detailed.get(card);
            String grade = r == null ? "" : (r.recommendation == null ? "" : r.recommendation.grade.name());
            double score = r == null ? 0.0 : (r.recommendation == null ? 0.0 : r.recommendation.score);
            candidates.add(new AiRecommendationEngine.Candidate(card.cardID, card.name, grade, score));
            ids.add(card.cardID);
        }

        // 记仇使坏：本地决策，不依赖 AI key（无 key 也稳定触发，符合注释原意）。
        // 先看本次卡奖有没有 A/S 级高分卡（score>=60）——有则绝不使坏，
        // 高分 key 卡不能拿来赌气（玩家当真会亏局），照常走真推荐；只对平庸卡奖使坏
        boolean hasHighValueCard = false;
        for (AiRecommendationEngine.Candidate c : candidates) {
            if (c != null && c.score >= 60.0) {
                hasHighValueCard = true;
                break;
            }
        }
        if (com.derekjass.sts.weightedpaths.creative.CardMoodEngine.currentMood()
                == com.derekjass.sts.weightedpaths.creative.CardMoodEngine.Mood.RESENTFUL && !hasHighValueCard) {
            AiRecommendation mischief = AiRecommendationEngine.mischiefDecision(candidates);
            if (mischief.valid) {
                aiRec = mischief;
                lastWasMischief = true;
                RunAdvisorLogger.markLastRewardMischief(mischief.recommendedId);
            }
            return;
        }
        lastWasMischief = false;

        final AiRecommender recommender = createDefaultRecommender();
        if (recommender == null) {
            return; // 未配置 key：纯规则（使坏已在上面处理，AI 拍板走规则兜底）
        }

        final int favor = com.derekjass.sts.weightedpaths.creative.CardMoodEngine.favor();
        final String mood = com.derekjass.sts.weightedpaths.creative.CardMoodEngine.currentMood().name();
        // AgentCore：把当前状态编码成结构化 State，供 AI 通过工具协议决策
        final AgentCore.State state = AgentBridge.buildState(
                currentAct(), currentHpPercent(), deckContext, favor,
                AiRecommendationEngine.cardIds(candidates));
        // 卡奖场景允许 AI 用这些工具（可表达抓某张 / 整次跳过 / 弹提醒）
        final java.util.Set<AgentCore.Tool> allowed = new java.util.HashSet<>();
        allowed.add(AgentCore.Tool.ADJUST_RECOMMENDATION);
        allowed.add(AgentCore.Tool.SKIP_ALL);
        allowed.add(AgentCore.Tool.SET_WARNING);
        allowed.add(AgentCore.Tool.DO_NOTHING);
        final java.util.Set<String> validCardIds = AgentBridge.toIdSet(AiRecommendationEngine.cardIds(candidates));
        // 闭环收口：调 AI→解析→失败兜底→落盘 全部由 AgentCore.run() 承担（不在此重复实现）
        new Thread(() -> {
            try {
                AgentCore.ToolExecutor executor = createToolExecutor(screen, detailed);
                AgentCore.Decision decision = AgentCore.run(state, recommender, allowed, validCardIds, executor);
                if (decision.valid) {
                    AiRecommendation rec = AgentBridge.toRecommendation(decision);
                    if (rec.valid) {
                        aiRec = rec; // 有效决策：渲染层下一帧覆盖推荐
                    }
                }
            } catch (Exception ignored) {
                // 失败/超时：保持 null，渲染继续走规则兜底（不阻断游戏）
            }
        }).start();
    }

    /** 当前层数（第几幕）。 */
    private static int currentAct() {
        try {
            return AbstractDungeon.actNum;
        } catch (Exception ignored) {
            return 1;
        }
    }

    /** 当前血量百分比（0~100）。 */
    private static int currentHpPercent() {
        try {
            if (AbstractDungeon.player == null) {
                return 100;
            }
            int max = AbstractDungeon.player.maxHealth;
            int cur = AbstractDungeon.player.currentHealth;
            if (max <= 0) {
                return 100;
            }
            return (int) (cur * 100L / max);
        } catch (Exception ignored) {
            return 100;
        }
    }

    /** 构建候选卡快照（供信任调整策略层决策；纯数据，不触碰游戏状态）。 */
    private static java.util.List<AiRecommendationEngine.Candidate> buildPolicyCandidates(
            CardRewardScreen screen,
            java.util.HashMap<AbstractCard, CardRecommendation> scored) {
        java.util.ArrayList<AiRecommendationEngine.Candidate> candidates = new java.util.ArrayList<>();
        if (screen == null || screen.rewardGroup == null) {
            return candidates;
        }
        for (AbstractCard card : screen.rewardGroup) {
            if (card == null) {
                continue;
            }
            CardRecommendation rec = scored.get(card);
            String grade = rec == null || rec.grade == null ? "" : rec.grade.name();
            double score = rec == null ? 0.0 : rec.score;
            candidates.add(new AiRecommendationEngine.Candidate(card.cardID, card.name, grade, score));
        }
        return candidates;
    }

    /** 只读查询工具执行器：让 AI 通过真实引擎查证（算分器/牌组/路线规划），返回纯文本结果。 */
    private static AgentCore.ToolExecutor createToolExecutor(
            CardRewardScreen screen,
            java.util.HashMap<AbstractCard, CardScorer.ScoredResult> detailed) {
        return (tool, args) -> {
            switch (tool) {
                case "EVALUATE_CARD": {
                    String cardId = args == null || args.get("cardId") == null ? "" : args.get("cardId");
                    AbstractCard card = findById(screen, cardId);
                    CardScorer.ScoredResult r = card == null ? null : detailed.get(card);
                    if (r == null || r.recommendation == null) {
                        return "未找到候选卡 " + cardId + " 的评分";
                    }
                    CardRecommendation rec = r.recommendation;
                    StringBuilder sb = new StringBuilder();
                    sb.append("评分=").append(String.format("%.1f", rec.score));
                    sb.append(", 评级=").append(rec.grade);
                    if (r.breakdown != null) {
                        sb.append(", 分项(base=").append(String.format("%.1f", r.breakdown.baseScore))
                                .append(", 生存=").append(String.format("%.1f", r.breakdown.survivalBonus))
                                .append(", 端口=").append(String.format("%.2f", r.breakdown.portMult))
                                .append(")");
                    }
                    return sb.toString();
                }
                case "QUERY_DECK":
                    return buildDeckContext();
                case "QUERY_ROUTE": {
                    GlobalRunPlan plan = GlobalRunPlan.fromCurrentRun();
                    StringBuilder sb = new StringBuilder();
                    sb.append("第").append(plan.actNumber).append("幕");
                    if (plan.phase != null) {
                        sb.append("(").append(plan.phase.name()).append(")");
                    }
                    if (plan.actsRemaining > 0) {
                        sb.append(", 剩余").append(plan.actsRemaining).append("幕");
                    }
                    if (!plan.nextRoom.isEmpty()) {
                        sb.append(", 下一房=").append(plan.nextRoom);
                        if (plan.roomsUntilElite >= 0) {
                            sb.append(", 距精英").append(plan.roomsUntilElite).append("房");
                        } else {
                            sb.append(", 前方无精英");
                        }
                    } else {
                        sb.append(", 路线未知");
                    }
                    if (!plan.upcomingThisAct.isEmpty()) {
                        sb.append(", 前方路线=").append(String.join("", plan.upcomingThisAct));
                    }
                    return sb.toString();
                }
                default:
                    return "未知工具";
            }
        };
    }
    private static String currentRewardKey(CardRewardScreen screen) {
        if (screen == null || screen.rewardGroup == null) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        key.append(AbstractDungeon.floorNum).append(':');
        for (AbstractCard card : screen.rewardGroup) {
            if (card != null) {
                key.append(card.cardID).append('|');
            }
        }
        return key.toString();
    }

    /** 懒加载默认 AI 推荐器：从环境变量/系统属性读 key；未配置返回 null（纯规则）。 */
    private static AiRecommender createDefaultRecommender() {
        String key = System.getenv("RUN_ADVISOR_AI_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("runAdvisor.aiKey");
        }
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        return new DeepSeekAiRecommender(key.trim());
    }

    /** 构造牌组摘要，供 AI 决策参考（不含层数/血量——它们已由 AgentCore.State 结构化表达，避免重复）。 */
    private static String buildDeckContext() {
        if (AbstractDungeon.player == null || AbstractDungeon.player.masterDeck == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        DeckSnapshot snap = DeckAnalyzer.analyzeSnapshot(AbstractDungeon.player.masterDeck.group);
        PortProfile ports = snap.ports;
        sb.append("牌组").append(ports.deckSize).append("张");
        sb.append("（伤害").append(ports.damagePoints)
                .append("/格挡").append(ports.blockPoints)
                .append("/运转").append(ports.enginePoints);
        if (ports.hasAoe) {
            sb.append("/有AOE");
        }
        if (ports.hasScaling) {
            sb.append("/有成长");
        }
        sb.append("）");
        return sb.toString();
    }

    private static void maybeLogCardReward(
            CardRewardScreen screen,
            java.util.HashMap<AbstractCard, CardRecommendation> scored,
            java.util.HashMap<AbstractCard, CardScorer.ScoredResult> detailed,
            AbstractCard bestCard,
            boolean skipAll,
            boolean trustAdjusted,
            double trustFactor) {
        RunAdvisorLogger.ensureSession(); // 日志关闭时空操作；开启新局日志时重置卡奖缓存
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(AbstractDungeon.floorNum).append(':');
        for (AbstractCard card : screen.rewardGroup) {
            if (card != null) {
                keyBuilder.append(card.cardID).append('|');
            }
        }
        String key = keyBuilder.toString();
        if (key.equals(lastRewardLogKey)) {
            return; // 同一卡奖重复渲染：推荐状态机只推进一次
        }
        lastRewardLogKey = key;
        // 推荐状态机（卡的态度/记仇/违背检测）：与「决策日志」开关无关，永远执行——
        // 否则玩家关闭日志后态度台词不再推进、违背检测会用到陈旧数据
        lastRecommendedId = bestCard == null ? "" : bestCard.cardID;
        lastSkipAll = skipAll;
        lastRewardGrades.clear();
        cardNameMap.clear();
        playerPickedId = "";
        for (AbstractCard card : screen.rewardGroup) {
            if (card == null) {
                continue;
            }
            cardNameMap.put(card.cardID, card.name);
            // 用「显示口径」的评级（含 B 档提升），保证台词/日志与玩家看到的一致
            CardRecommendation rec = scored.get(card);
            if (rec != null) {
                lastRewardGrades.put(card.cardID, rec.grade.name());
            }
        }
        CardAttitudeEngine.advanceForNewReward();

        if (!RunAdvisorLogger.isEnabled()) {
            return; // 日志关闭：状态机已维护，不写文件
        }
        java.util.ArrayList<RunLogModels.CardChoiceLog> choices = new java.util.ArrayList<>();
        int index = 0;
        for (AbstractCard card : screen.rewardGroup) {
            if (card == null) {
                continue;
            }
            CardScorer.ScoredResult result = detailed.get(card);
            CardRecommendation rec = scored.get(card);
            if (result == null || rec == null) {
                continue;
            }
            boolean recommended = !skipAll && card == bestCard;
            choices.add(RunAdvisorLogger.choiceLog(
                    index,
                    card.cardID,
                    rec.grade.name(),
                    rec.score,
                    recommended,
                    result.breakdown));
            index++;
        }
        RunAdvisorLogger.logCardReward(skipAll, choices, trustAdjusted, trustFactor);
    }

    private static void drawRecommendation(
            SpriteBatch sb, AbstractCard card, CardRecommendation rec, boolean recommended,
            AiRecommendation ai, boolean isMischief) {
        float cx = card.hb.cX;
        float cy = card.hb.cY + card.hb.height + (36.0f * Settings.scale);
        // 记仇使坏：标签换成「逗你的」+橙色，与真推荐（绿「推荐」）视觉区分，玩家一眼能看出是玩笑
        String label = recommended
                ? (isMischief ? CardUiStrings.PICK_MISCHIEF : CardUiStrings.PICK_RECOMMENDED)
                : rec.gradeLabel();
        Color color = recommended
                ? (isMischief ? MISCHIEF_COLOR : RECOMMENDED_COLOR)
                : gradeColor(rec.grade);

        if (ModFonts.header != null) {
            FontHelper.renderFontCentered(sb, ModFonts.header, label, cx, cy, color);
        } else {
            FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, label, cx, cy, color);
        }

        // 抓牌历史·已有 N 张
        int existing = countInDeck(card.cardID);
        if (existing > 0) {
            String having = String.format(CardUiStrings.HAVING_COUNT, existing);
            float cy2 = cy - (22.0f * Settings.scale);
            if (ModFonts.body != null) {
                FontHelper.renderFontCentered(sb, ModFonts.body, having, cx, cy2, HAVING_COLOR);
            } else {
                FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, having, cx, cy2, HAVING_COLOR);
            }
        }

        // AI 拍板推荐：在推荐的卡下显示性格化理由（AI 有话说）
        if (recommended && ai != null && ai.reason != null && !ai.reason.isEmpty()) {
            float cy3 = cy - (44.0f * Settings.scale);
            if (ModFonts.body != null) {
                FontHelper.renderFontCentered(sb, ModFonts.body, ai.reason, cx, cy3, RECOMMENDED_COLOR);
            } else {
                FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, ai.reason, cx, cy3, RECOMMENDED_COLOR);
            }
        }
    }

    private static int countInDeck(String cardId) {
        if (cardId == null) {
            return 0;
        }
        int count = 0;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c != null && cardId.equals(c.cardID)) {
                count++;
            }
        }
        return count;
    }

    private static void drawSkipHint(SpriteBatch sb, AiRecommendation ai) {
        float cx = Settings.WIDTH / 2.0f;
        float cy = 80.0f * Settings.scale;
        String text = CardUiStrings.SKIP_ALL_HINT;
        // AI 拍板跳过：显示 AI 的性格化理由
        if (ai != null && ai.reason != null && !ai.reason.isEmpty()) {
            text = ai.reason;
        }
        if (ModFonts.body != null) {
            FontHelper.renderFontCentered(sb, ModFonts.body, text, cx, cy, SKIP_HINT_COLOR);
            return;
        }
        FontHelper.renderFontCentered(
                sb, FontHelper.cardTitleFont, text, cx, cy, SKIP_HINT_COLOR);
    }

    private static Color gradeColor(com.derekjass.sts.weightedpaths.card.CardGrade grade) {
        switch (grade) {
            case S:
                return GRADE_S;
            case A:
                return GRADE_A;
            case B:
                return GRADE_B;
            case C:
            default:
                return GRADE_C;
        }
    }

    @SpirePatch(clz = CardRewardScreen.class, method = "onClose")
    public static class PostClosePatch {
        @SpirePostfixPatch
        public static void afterClose(CardRewardScreen __instance) {
            if (AbstractDungeon.player == null) {
                return;
            }
            String chosenId = playerPickedId;
            boolean skipped = chosenId == null || chosenId.isEmpty();
            if (RunAdvisorLogger.isEnabled()) {
                RunAdvisorLogger.logPlayerCardChoice(chosenId == null ? "" : chosenId, skipped);
            }
            // 卡名转中文（显示用；卡名唯一，比较仍有效）
            String chosenName = cardNameMap.getOrDefault(chosenId == null ? "" : chosenId, "");
            // 最终推荐：AI 拍板有效则用 AI 的推荐（玩家实际违背的是显示给他的那个）
            AiRecommendation finalAi = aiRec;
            String recommendedName;
            boolean recommendedSkipAll;
            if (finalAi != null && finalAi.valid) {
                recommendedSkipAll = finalAi.skipAll;
                recommendedName = cardNameMap.getOrDefault(
                        finalAi.recommendedId == null ? "" : finalAi.recommendedId,
                        finalAi.recommendedId == null ? "" : finalAi.recommendedId);
            } else {
                recommendedSkipAll = lastSkipAll;
                recommendedName = cardNameMap.getOrDefault(lastRecommendedId, lastRecommendedId);
            }
            String chosenGrade = skipped ? "" : lastRewardGrades.getOrDefault(chosenId, "");
            String context = "当前第" + AbstractDungeon.actNum + "层";
            ChatBoxUi.get().core().setGameContext(context);
            // 记仇使坏场景：不走「违背推荐」的通用台词，改走「上当/没上当」的专属反馈
            if (lastWasMischief && finalAi != null && finalAi.valid && !finalAi.skipAll) {
                boolean playerTricked = !skipped && finalAi.recommendedId != null
                        && finalAi.recommendedId.equals(chosenId);
                String mischiefLine = CardAttitudeEngine.evaluateMischiefResult(playerTricked);
                if (!mischiefLine.isEmpty()) {
                    ChatBoxUi.get().core().addCardMessage(mischiefLine);
                }
                lastWasMischief = false;
                aiRec = null;
                return;
            }
            // 卡的态度：检测是否违背推荐，生成待显示台词
            CardAttitudeEngine.evaluateReward(recommendedName, recommendedSkipAll, chosenName, skipped, chosenGrade);
            // 卡的态度台词 → 聊天框（它"主动开口"）
            String attitude = CardAttitudeEngine.pendingLine();
            if (!attitude.isEmpty()) {
                ChatBoxUi.get().core().addCardMessage(attitude);
            }
            // AI 增强：传入当前层数情境，避免台词说错层的 Boss/设定
            ensureAttitudeDispatcherInjected();
            CardAttitudeEngine.enrichWithAi(recommendedName, recommendedSkipAll, chosenName, skipped, chosenGrade, context);
            // 卡奖结束：清空 AI 决策，避免残留到下一张卡奖
            aiRec = null;
            lastWasMischief = false;
        }
    }

    /** 玩家实际抓卡：acquireCard 是卡奖确认抓卡的明确入口，直接记录比推断牌组可靠。 */
    @SpirePatch(clz = CardRewardScreen.class, method = "acquireCard")
    public static class AcquireCardPatch {
        @SpirePostfixPatch
        public static void afterAcquire(CardRewardScreen __instance, AbstractCard card) {
            if (card != null) {
                playerPickedId = card.cardID;
                cardNameMap.put(card.cardID, card.name);
                // 契约：抓攻击牌检测（「本幕不抓攻击牌」契约的违背判定）
                String pactMsg = PactManager.onCardPicked(card.type == AbstractCard.CardType.ATTACK);
                if (!pactMsg.isEmpty()) {
                    ChatBoxUi.get().core().addCardMessage(pactMsg);
                }
            }
        }
    }
}
