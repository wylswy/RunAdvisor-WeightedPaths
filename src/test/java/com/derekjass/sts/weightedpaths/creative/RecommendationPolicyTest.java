package com.derekjass.sts.weightedpaths.creative;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 信任调整策略层测试：显式曲线边界、失真翻转、自我保护、确定性、空输入。 */
public class RecommendationPolicyTest {

    private static AiRecommendationEngine.Candidate c(String id, double score) {
        return new AiRecommendationEngine.Candidate(id, id, "", score);
    }

    @Test
    public void trustFactor_friendly_isOne() {
        assertEquals(1.0, RecommendationPolicy.trustFactor(0), 1e-9);
        assertEquals(1.0, RecommendationPolicy.trustFactor(10), 1e-9);
        assertEquals(1.0, RecommendationPolicy.trustFactor(-1), 1e-9);
    }

    @Test
    public void trustFactor_unhappy_isPointEight() {
        assertEquals(0.80, RecommendationPolicy.trustFactor(-2), 1e-9);
        assertEquals(0.80, RecommendationPolicy.trustFactor(-5), 1e-9);
    }

    @Test
    public void trustFactor_resentful_isPointFiveFive() {
        assertEquals(0.55, RecommendationPolicy.trustFactor(-6), 1e-9);
        assertEquals(0.55, RecommendationPolicy.trustFactor(-10), 1e-9);
    }

    @Test
    public void decide_friendly_picksRuleBest_notAdjusted() {
        List<AiRecommendationEngine.Candidate> cs = Arrays.asList(c("A", 80.0), c("B", 60.0), c("C", 40.0));
        RecommendationPolicy.Decision d = RecommendationPolicy.decide(cs, 0);
        assertEquals("A", d.cardId);
        assertEquals("A", d.ruleBestCardId);
        assertFalse(d.trustAdjusted);
        assertEquals(80.0, d.ruleBestScore, 1e-9);
    }

    @Test
    public void decide_unhappy_flipsCloseCall() {
        // 最优 60 打 8 折 = 48 < 次优 55 → 推荐转向次优
        List<AiRecommendationEngine.Candidate> cs = Arrays.asList(c("A", 60.0), c("B", 55.0));
        RecommendationPolicy.Decision d = RecommendationPolicy.decide(cs, -3);
        assertEquals("B", d.cardId);
        assertEquals("A", d.ruleBestCardId);
        assertTrue(d.trustAdjusted);
        assertEquals(0.80, d.trustFactor, 1e-9);
    }

    @Test
    public void decide_resentful_flipsModerateGap() {
        // 最优 70 打 55 折 = 38.5 < 次优 60 → 推荐转向次优
        List<AiRecommendationEngine.Candidate> cs = Arrays.asList(c("A", 70.0), c("B", 60.0), c("C", 30.0));
        RecommendationPolicy.Decision d = RecommendationPolicy.decide(cs, -8);
        assertEquals("B", d.cardId);
        assertTrue(d.trustAdjusted);
        assertEquals(0.55, d.trustFactor, 1e-9);
    }

    @Test
    public void decide_resentful_selfGuardsObviousBest() {
        // 最优 90 打 55 折 = 49.5 仍高于次优 40 → 不翻转（不坑玩家）
        List<AiRecommendationEngine.Candidate> cs = Arrays.asList(c("A", 90.0), c("B", 40.0), c("C", 30.0));
        RecommendationPolicy.Decision d = RecommendationPolicy.decide(cs, -10);
        assertEquals("A", d.cardId);
        assertFalse(d.trustAdjusted);
    }

    @Test
    public void decide_unhappy_selfGuardsObviousBest() {
        List<AiRecommendationEngine.Candidate> cs = Arrays.asList(c("A", 90.0), c("B", 40.0));
        RecommendationPolicy.Decision d = RecommendationPolicy.decide(cs, -3);
        assertEquals("A", d.cardId);
        assertFalse(d.trustAdjusted);
    }

    @Test
    public void decide_isDeterministic() {
        List<AiRecommendationEngine.Candidate> cs = Arrays.asList(c("A", 60.0), c("B", 55.0));
        RecommendationPolicy.Decision d1 = RecommendationPolicy.decide(cs, -8);
        RecommendationPolicy.Decision d2 = RecommendationPolicy.decide(cs, -8);
        assertEquals(d1.cardId, d2.cardId);
        assertEquals(d1.trustAdjusted, d2.trustAdjusted);
        assertEquals(d1.effectiveScore, d2.effectiveScore, 1e-9);
    }

    @Test
    public void decide_singleCandidate_neverAdjusted() {
        List<AiRecommendationEngine.Candidate> cs = Collections.singletonList(c("A", 50.0));
        RecommendationPolicy.Decision d = RecommendationPolicy.decide(cs, -10);
        assertEquals("A", d.cardId);
        assertFalse(d.trustAdjusted);
    }

    @Test
    public void decide_emptyOrNull_returnsEmptyDecision() {
        RecommendationPolicy.Decision d1 = RecommendationPolicy.decide(null, -5);
        assertFalse(d1.trustAdjusted);
        assertEquals("", d1.cardId);
        RecommendationPolicy.Decision d2 = RecommendationPolicy.decide(
                Collections.<AiRecommendationEngine.Candidate>emptyList(), -5);
        assertFalse(d2.trustAdjusted);
        assertEquals("", d2.cardId);
    }
}