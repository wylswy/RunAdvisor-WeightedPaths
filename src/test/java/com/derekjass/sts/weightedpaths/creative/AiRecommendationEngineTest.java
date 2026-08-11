package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine.AiRecommendation;
import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine.Candidate;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** AI 推荐消费者模型 + 记仇使坏决策纯逻辑测试（不联网）。
 * 协议构建/解析（buildPrompt/parse）已迁至 AgentCore，不在此测。 */
public class AiRecommendationEngineTest {

    private static List<Candidate> candidates() {
        return Arrays.asList(
                new Candidate("Backstab", "背后突袭", "A", 78.0),
                new Candidate("Footwork", "腿部功夫", "B", 62.0),
                new Candidate("DaggerSpray", "刀刃之舞", "C", 40.0));
    }

    private static Candidate c(String id) {
        return new Candidate(id, id, "A", 70.0);
    }

    @Test
    public void cardIds_collectsNonNullIds() {
        List<String> ids = AiRecommendationEngine.cardIds(candidates());
        assertEquals(3, ids.size());
        assertTrue(ids.contains("Backstab"));
    }

    @Test
    public void cardIds_skipsNull() {
        List<String> ids = AiRecommendationEngine.cardIds(
                Arrays.asList(c("A"), null, c("B")));
        assertEquals(2, ids.size());
    }

    @Test
    public void mischiefDecision_picksWorstCard() {
        // DaggerSpray 分最低(40)，记仇时应故意推它
        AiRecommendation rec = AiRecommendationEngine.mischiefDecision(candidates());
        assertTrue("记仇应给有效决策", rec.valid);
        assertEquals("DaggerSpray", rec.recommendedId);
        assertFalse(rec.skipAll);
        assertTrue("理由应明说这是逗你的(明着坏)", rec.reason.contains("逗你"));
    }

    @Test
    public void mischiefDecision_emptyInvalid() {
        assertFalse(AiRecommendationEngine.mischiefDecision(null).valid);
        assertFalse(AiRecommendationEngine.mischiefDecision(new java.util.ArrayList<>()).valid);
    }

    @Test
    public void mischiefDecision_singleCandidatePicksIt() {
        List<Candidate> single = Arrays.asList(c("Footwork"));
        AiRecommendation rec = AiRecommendationEngine.mischiefDecision(single);
        assertTrue(rec.valid);
        assertEquals("Footwork", rec.recommendedId);
    }

    @Test
    public void mischiefDecision_picksLowestScoreAmongTies() {
        List<Candidate> list = Arrays.asList(
                new Candidate("A", "A", "B", 60.0),
                new Candidate("B", "B", "C", 30.0),
                new Candidate("C", "C", "C", 30.0));
        // B 和 C 同分最低，任选其一且必须是 A 之外的差卡
        AiRecommendation rec = AiRecommendationEngine.mischiefDecision(list);
        assertTrue(rec.valid);
        assertTrue("应推最差之一", "B".equals(rec.recommendedId) || "C".equals(rec.recommendedId));
    }
}
