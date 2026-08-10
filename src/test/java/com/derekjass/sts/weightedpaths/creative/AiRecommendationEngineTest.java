package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine.AiRecommendation;
import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine.Candidate;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** AI 拍板推荐引擎纯逻辑测试：提示词构造 + 决策解析（不联网）。 */
public class AiRecommendationEngineTest {

    private static final Set<String> IDS = new HashSet<>(Arrays.asList("Backstab", "Footwork", "DaggerSpray"));

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
    public void parse_picksValidCard() {
        AiRecommendation rec = AiRecommendationEngine.parse("PICK:Backstab|你现在缺点直伤", IDS);
        assertTrue(rec.valid);
        assertEquals("Backstab", rec.recommendedId);
        assertFalse(rec.skipAll);
        assertEquals("你现在缺点直伤", rec.reason);
    }

    @Test
    public void parse_pickWithoutReason() {
        AiRecommendation rec = AiRecommendationEngine.parse("PICK:Footwork", IDS);
        assertTrue(rec.valid);
        assertEquals("Footwork", rec.recommendedId);
        assertEquals("", rec.reason);
    }

    @Test
    public void parse_skipToken() {
        AiRecommendation rec = AiRecommendationEngine.parse("SKIP|这几张都一般", IDS);
        assertTrue(rec.valid);
        assertTrue(rec.skipAll);
        assertNull(rec.recommendedId);
        assertEquals("这几张都一般", rec.reason);
    }

    @Test
    public void parse_skipChineseWord() {
        assertTrue(AiRecommendationEngine.parse("跳过|都不太适合", IDS).skipAll);
        assertTrue(AiRecommendationEngine.parse("不抓|先别乱拿", IDS).skipAll);
        assertTrue(AiRecommendationEngine.parse("别拿|牌组够纯了", IDS).skipAll);
    }

    @Test
    public void parse_invalidCardIdFallsBackToRule() {
        AiRecommendation rec = AiRecommendationEngine.parse("PICK:NonExistent|这个不存在", IDS);
        assertFalse("编造不存在的卡应回落规则兜底", rec.valid);
    }

    @Test
    public void parse_nullAndEmptyInvalid() {
        assertFalse(AiRecommendationEngine.parse(null, IDS).valid);
        assertFalse(AiRecommendationEngine.parse("", IDS).valid);
        assertFalse(AiRecommendationEngine.parse("   ", IDS).valid);
    }

    @Test
    public void parse_garbageInvalid() {
        assertFalse(AiRecommendationEngine.parse("随便说点什么", IDS).valid);
        assertFalse(AiRecommendationEngine.parse("PICK:|没写卡ID", IDS).valid);
    }

    @Test
    public void parse_fullWidthColonPick() {
        AiRecommendation rec = AiRecommendationEngine.parse("PICK：DaggerSpray|群怪用得上", IDS);
        assertTrue(rec.valid);
        assertEquals("DaggerSpray", rec.recommendedId);
    }

    @Test
    public void parse_lowercasePick() {
        AiRecommendation rec = AiRecommendationEngine.parse("pick:Footwork|行", IDS);
        assertTrue(rec.valid);
        assertEquals("Footwork", rec.recommendedId);
    }

    @Test
    public void buildPrompt_containsCandidatesAndContext() {
        String prompt = AiRecommendationEngine.buildPrompt(
                candidates(), "第2层，牌组缺防，血量偏低", -3, "记仇");
        assertTrue("应含候选卡名", prompt.contains("背后突袭"));
        assertTrue("应含候选卡ID", prompt.contains("Backstab"));
        assertTrue("应含情境", prompt.contains("牌组缺防"));
        assertTrue("应含好感度", prompt.contains("好感度"));
        assertTrue("应含心情", prompt.contains("记仇"));
        assertTrue("应含格式约束", prompt.contains("PICK:"));
    }

    @Test
    public void buildPrompt_handlesNullContext() {
        String prompt = AiRecommendationEngine.buildPrompt(candidates(), null, 0, "友好");
        assertTrue(prompt.contains("候选卡"));
        assertTrue(prompt.contains("Backstab"));
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
        assertTrue("理由应是赌气调皮的", rec.reason.contains("最次"));
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
