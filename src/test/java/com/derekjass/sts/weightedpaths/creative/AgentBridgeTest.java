package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.AgentCore.Decision;
import com.derekjass.sts.weightedpaths.creative.AgentCore.State;
import com.derekjass.sts.weightedpaths.creative.AgentCore.Tool;
import com.derekjass.sts.weightedpaths.creative.AiRecommendationEngine.AiRecommendation;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentBridgeTest {

    private static final Set<Tool> ALL = new HashSet<>(Arrays.asList(Tool.values()));
    private static final Set<String> IDS = new HashSet<>(Arrays.asList("Backstab", "Footwork"));

    private static Decision parse(String action, String argument, String reason) {
        String json = String.format(
                "{\"action\":\"%s\",\"argument\":\"%s\",\"reason\":\"%s\",\"confidence\":0.8}",
                action, argument, reason);
        return AgentCore.parse(json, ALL, IDS);
    }

    @Test
    public void adjustMapsToPick() {
        Decision d = parse("ADJUST_RECOMMENDATION", "Backstab", "适合你");
        AiRecommendation rec = AgentBridge.toRecommendation(d);
        assertTrue(rec.valid);
        assertFalse(rec.skipAll);
        assertEquals("Backstab", rec.recommendedId);
        assertEquals("适合你", rec.reason);
    }

    @Test
    public void skipAllMapsToSkip() {
        Decision d = parse("SKIP_ALL", "这几张都一般", "先不抓");
        AiRecommendation rec = AgentBridge.toRecommendation(d);
        assertTrue(rec.valid);
        assertTrue(rec.skipAll);
        assertNull(rec.recommendedId);
    }

    @Test
    public void setWarningMapsToInvalid() {
        Decision d = parse("SET_WARNING", "血不多了", "保命");
        AiRecommendation rec = AgentBridge.toRecommendation(d);
        assertFalse(rec.valid); // 不产生推荐动作 → 回落规则兜底
    }

    @Test
    public void doNothingMapsToInvalid() {
        Decision d = parse("DO_NOTHING", "", "没大事");
        AiRecommendation rec = AgentBridge.toRecommendation(d);
        assertFalse(rec.valid);
    }

    @Test
    public void invalidDecisionMapsToInvalid() {
        assertFalse(AgentBridge.toRecommendation(null).valid);
        assertFalse(AgentBridge.toRecommendation(Decision.invalid()).valid);
    }

    @Test
    public void buildStatePreservesFields() {
        State s = AgentBridge.buildState(2, 35, "缺防", -3, Arrays.asList("A", "B"));
        assertEquals(2, s.act);
        assertEquals(35, s.hpPercent);
        assertEquals("缺防", s.situation);
        assertEquals(-3, s.favor);
        assertEquals(Arrays.asList("A", "B"), s.candidateIds);
    }

    @Test
    public void toIdSetCopiesIds() {
        Set<String> set = AgentBridge.toIdSet(Arrays.asList("A", "B", "A"));
        assertEquals(2, set.size());
        assertTrue(set.contains("A"));
        assertTrue(set.contains("B"));
    }
}
