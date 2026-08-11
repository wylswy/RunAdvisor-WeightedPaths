package com.derekjass.sts.weightedpaths.creative;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 推荐的「消费者模型」与记仇使坏决策。
 *
 * <p><b>职责收敛（2026-08-11）：</b>本类不再做 AI 决策协议的构建/解析——那部分已由
 * {@link AgentCore}（JSON 工具调用协议）取代，经 {@link AgentBridge} 桥接回本类的
 * {@link AiRecommendation} 供渲染层消费。此处只保留：
 * <ul>
 *   <li>{@link AiRecommendation} —— UI 消费的推荐结果（抓哪张 / 是否跳过 / 理由）；</li>
 *   <li>{@link Candidate} —— 一张候选卡的评分快照，供接线与使坏决策使用；</li>
 *   <li>{@link #mischiefDecision(List)} —— 记仇时的「使坏」决策（故意推最差卡逗玩家）。</li>
 * </ul>
 */
public final class AiRecommendationEngine {

    /** 结构化决策结果。 */
    public static final class AiRecommendation {
        /** AI 推荐抓的卡 ID；{@link #skipAll} 为 true 时为 null。 */
        public final String recommendedId;
        /** 是否整次跳过（不抓任何卡）。 */
        public final boolean skipAll;
        /** AI 给的性格化推荐理由（可为空）。 */
        public final String reason;
        /** 决策是否有效（AI 返回了候选集内的卡 / 明确跳过）。false = 回落规则兜底。 */
        public final boolean valid;

        private AiRecommendation(String recommendedId, boolean skipAll, String reason, boolean valid) {
            this.recommendedId = recommendedId;
            this.skipAll = skipAll;
            this.reason = reason == null ? "" : reason.trim();
            this.valid = valid;
        }

        static AiRecommendation pick(String cardId, String reason) {
            return new AiRecommendation(cardId, false, reason, true);
        }

        static AiRecommendation skip(String reason) {
            return new AiRecommendation(null, true, reason, true);
        }

        static AiRecommendation invalid() {
            return new AiRecommendation(null, false, "", false);
        }
    }

    /** 一张候选卡的信息，供 AI 决策。 */
    public static final class Candidate {
        public final String cardId;
        public final String name;
        public final String grade;
        public final double score;

        public Candidate(String cardId, String name, String grade, double score) {
            this.cardId = cardId;
            this.name = name;
            this.grade = grade;
            this.score = score;
        }
    }

    private AiRecommendationEngine() {
    }

    /**
     * 记仇时的「使坏」决策：故意把推荐指向候选里评级/分数最差的那张，逗玩家——看你还信不信它。
     *
     * <p>情感逻辑（设计意图）：它记仇时推坏卡，就是想试探玩家是否还信任它。
     * 若玩家当真抓了这张坏卡 → 态度引擎视作「顺从推荐」，好感度回升（它被你的信任打动）；
     * 若玩家没上当 → 它继续闹脾气。这条线让「骗」和「原谅」连成完整的情感循环。
     *
     * @param candidates 本次候选卡（至少 1 张）
     * @return 指向最差卡的决策；无候选时返回 invalid（回落规则兜底）
     */
    public static AiRecommendation mischiefDecision(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return AiRecommendation.invalid();
        }
        Candidate worst = candidates.get(0);
        for (Candidate c : candidates) {
            if (c != null && c.score < worst.score) {
                worst = c;
            }
        }
        if (worst == null || worst.cardId == null) {
            return AiRecommendation.invalid();
        }
        return AiRecommendation.pick(worst.cardId, MISCHIEF_REASON);
    }

    /** 记仇时推坏卡的理由（明着坏：清楚告诉玩家这是逗你的，不装；短，避免超卡宽压边）。 */
    private static final String MISCHIEF_REASON = "这张最次，故意逗你的，爱信不信";

    /** 便捷：把候选卡转成合法 ID 列表（供接线构造 AgentCore 校验集）。 */
    public static List<String> cardIds(List<Candidate> candidates) {
        List<String> ids = new ArrayList<>();
        if (candidates != null) {
            for (Candidate c : candidates) {
                if (c != null && c.cardId != null) {
                    ids.add(c.cardId);
                }
            }
        }
        return ids;
    }
}
