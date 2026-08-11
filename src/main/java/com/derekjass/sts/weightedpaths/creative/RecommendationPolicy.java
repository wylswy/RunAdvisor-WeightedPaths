package com.derekjass.sts.weightedpaths.creative;

import java.util.List;

/**
 * 信任调整策略层 —— 把「关系影响推荐质量」变成确定性、可测试的机制。
 *
 * <p>设计（机制式，非许愿式）：
 * <ul>
 *   <li>规则算分器照常给出每张候选的诚实分数（界面显示不变，玩家仍能看到真实评级）；</li>
 *   <li>本层按好感度对「规则最优卡」做确定性失真：有效分 = 规则分 × {@link #trustFactor(int)}，
 *       其余候选不变——当次优卡落在失真范围内时，推荐会转向次优；</li>
 *   <li>同输入必同输出（纯函数、无随机、无外部依赖），可单测、可回放、可审计；</li>
 *   <li>自我保护：最优卡若遥遥领先，失真再大也不会把推荐推到明显更差的卡上（不坑玩家）。</li>
 * </ul>
 *
 * <p>与「记仇使坏」的关系：使坏是 RESENTFUL 时的刻意整蛊（明着坏、有道歉回路）；
 * 本层是任何不友好状态下的环境性失真——不依赖 AI、不依赖 key，总是生效。
 * AI 的有效决策（{@link AgentCore}）随后覆盖本层结果。
 *
 * <p>阈值与 {@link CardMoodEngine.Mood} 对齐：RESENTFUL=favor≤-6，UNHAPPY=favor≤-2。
 */
public final class RecommendationPolicy {

    /** 记仇阈值：favor ≤ -6（对应 RESENTFUL）。 */
    private static final int RESENTFUL_FAVOR = -6;
    /** 不高兴阈值：favor ≤ -2（对应 UNHAPPY）。 */
    private static final int UNHAPPY_FAVOR = -2;

    /** 记仇时的推荐质量系数：最优卡有效分打 55 折（次优有机会反超）。 */
    private static final double TRUST_RESENTFUL = 0.55;
    /** 不高兴时的推荐质量系数：最优卡有效分打 8 折（接近卡才可能反超）。 */
    private static final double TRUST_UNHAPPY = 0.80;
    /** 友好：完全按规则分，无失真。 */
    private static final double TRUST_FRIENDLY = 1.0;

    private RecommendationPolicy() {
    }

    /** 好感度 → 推荐质量系数（显式曲线；友好=1.0，越不友好系数越低）。 */
    public static double trustFactor(int favor) {
        if (favor <= RESENTFUL_FAVOR) {
            return TRUST_RESENTFUL;
        }
        if (favor <= UNHAPPY_FAVOR) {
            return TRUST_UNHAPPY;
        }
        return TRUST_FRIENDLY;
    }

    /** 信任调整决策结果（纯数据）。 */
    public static final class Decision {
        /** 最终推荐抓的卡 ID（候选集内；无候选时为空串）。 */
        public final String cardId;
        /** 纯规则最优卡 ID（信任调整的对照基线）。 */
        public final String ruleBestCardId;
        /** 本次推荐是否因信任调整与纯规则最优不同。 */
        public final boolean trustAdjusted;
        /** 本次生效的信任系数。 */
        public final double trustFactor;
        /** 纯规则最优分。 */
        public final double ruleBestScore;
        /** 选中卡的有效分（最优卡被失真时 = 规则分 × 系数；其余卡 = 规则分）。 */
        public final double effectiveScore;

        private Decision(String cardId, String ruleBestCardId, boolean trustAdjusted,
                         double trustFactor, double ruleBestScore, double effectiveScore) {
            this.cardId = cardId == null ? "" : cardId;
            this.ruleBestCardId = ruleBestCardId == null ? "" : ruleBestCardId;
            this.trustAdjusted = trustAdjusted;
            this.trustFactor = trustFactor;
            this.ruleBestScore = ruleBestScore;
            this.effectiveScore = effectiveScore;
        }
    }

    /**
     * 纯函数：给定候选卡与好感度，返回信任调整后的推荐。
     *
     * <p>规则：仅「规则最优卡」按 {@link #trustFactor(int)} 失真（有效分 = 规则分 × 系数），
     * 其余候选保持原分；取有效分最高者为最终推荐。失真不改变候选集、不改变显示分数，
     * 只改变「推荐指向哪张」。确定性：同输入必同输出。
     *
     * @param candidates 本次候选卡（可为空；空则返回空决策，调用方自行兜底）
     * @param favor 当前好感度（-10..10）
     * @return 信任调整决策；空候选时 cardId 为空串、trustAdjusted=false
     */
    public static Decision decide(List<AiRecommendationEngine.Candidate> candidates, int favor) {
        if (candidates == null || candidates.isEmpty()) {
            return new Decision("", "", false, 1.0, 0.0, 0.0);
        }
        AiRecommendationEngine.Candidate best = null;
        for (AiRecommendationEngine.Candidate c : candidates) {
            if (c == null || c.cardId == null || c.cardId.isEmpty()) {
                continue;
            }
            if (best == null || c.score > best.score) {
                best = c;
            }
        }
        if (best == null) {
            return new Decision("", "", false, 1.0, 0.0, 0.0);
        }
        double trust = trustFactor(favor);
        double ruleBestScore = best.score;
        if (trust >= 1.0) {
            return new Decision(best.cardId, best.cardId, false, trust, ruleBestScore, ruleBestScore);
        }
        double bestEffective = best.score * trust;
        AiRecommendationEngine.Candidate chosen = best;
        double chosenEffective = bestEffective;
        for (AiRecommendationEngine.Candidate c : candidates) {
            if (c == null || c.cardId == null || c.cardId.isEmpty() || c == best) {
                continue;
            }
            if (c.score > chosenEffective) {
                chosen = c;
                chosenEffective = c.score;
            }
        }
        boolean adjusted = chosen != best;
        return new Decision(chosen.cardId, best.cardId, adjusted, trust, ruleBestScore, chosenEffective);
    }
}