package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.AgentCore.Decision;
import com.derekjass.sts.weightedpaths.creative.AgentCore.State;
import com.derekjass.sts.weightedpaths.creative.AgentCore.Tool;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgentCoreTest {

    private static final Set<Tool> ALL = new HashSet<>(Arrays.asList(Tool.values()));
    private static final Set<String> IDS = new HashSet<>(Arrays.asList("Backstab", "Footwork", "NoxiousFumes"));

    private static String json(String action, String argument, String reason, double conf) {
        return String.format(
                "{\"action\":\"%s\",\"argument\":\"%s\",\"reason\":\"%s\",\"confidence\":%s}",
                action, argument, reason, conf);
    }

    @Test
    public void parseValidAdjust() {
        Decision d = AgentCore.parse(json("ADJUST_RECOMMENDATION", "Backstab", "这张适合你", 0.9), ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.ADJUST_RECOMMENDATION, d.tool);
        assertEquals("Backstab", d.argument);
        assertEquals(0.9, d.confidence, 1e-9);
    }

    @Test
    public void parseValidWarning() {
        Decision d = AgentCore.parse(json("SET_WARNING", "你血不多了", "先保命", 0.8), ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.SET_WARNING, d.tool);
    }

    @Test
    public void parseValidDoNothing() {
        Decision d = AgentCore.parse(json("DO_NOTHING", "", "没大事", 0.5), ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.DO_NOTHING, d.tool);
    }

    @Test
    public void parseValidSkipAll() {
        Decision d = AgentCore.parse(json("SKIP_ALL", "这几张都一般", "先不抓", 0.7), ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.SKIP_ALL, d.tool);
        assertEquals("这几张都一般", d.argument);
    }

    @Test
    public void parseSkipAllNotAllowedInvalid() {
        Set<Tool> noSkip = new HashSet<>(Arrays.asList(Tool.ADJUST_RECOMMENDATION));
        assertFalse(AgentCore.parse(json("SKIP_ALL", "理由", "r", 0.7), noSkip, IDS).valid);
    }

    @Test
    public void parseGarbageJsonInvalid() {
        assertFalse(AgentCore.parse("这不是JSON", ALL, IDS).valid);
        assertFalse(AgentCore.parse("", ALL, IDS).valid);
        assertFalse(AgentCore.parse(null, ALL, IDS).valid);
    }

    @Test
    public void parseToolNotAllowedInvalid() {
        Set<Tool> onlyWarning = new HashSet<>(Arrays.asList(Tool.SET_WARNING));
        assertFalse(AgentCore.parse(json("ADJUST_RECOMMENDATION", "Backstab", "r", 0.9), onlyWarning, IDS).valid);
    }

    @Test
    public void parseUnknownToolInvalid() {
        assertFalse(AgentCore.parse(json("DELETE_DATABASE", "x", "r", 0.9), ALL, IDS).valid);
    }

    @Test
    public void parseAdjustOutsideCandidatesInvalid() {
        assertFalse(AgentCore.parse(json("ADJUST_RECOMMENDATION", "HACK", "r", 0.9), ALL, IDS).valid);
        assertFalse(AgentCore.parse(json("ADJUST_RECOMMENDATION", "", "r", 0.9), ALL, IDS).valid);
    }

    @Test
    public void parseEmptyWarningInvalid() {
        assertFalse(AgentCore.parse(json("SET_WARNING", "", "r", 0.9), ALL, IDS).valid);
    }

    @Test
    public void confidenceClamped() {
        Decision d = AgentCore.parse(json("DO_NOTHING", "", "r", 99.0), ALL, IDS);
        assertEquals(1.0, d.confidence, 1e-9);
        Decision d2 = AgentCore.parse(json("DO_NOTHING", "", "r", -5.0), ALL, IDS);
        assertEquals(0.0, d2.confidence, 1e-9);
    }

    @Test
    public void fallbackLowHpWarns() {
        State s = new State(2, 20, "缺防", -3, Arrays.asList("Backstab", "Footwork"));
        Decision d = AgentCore.fallback(s);
        assertTrue(d.valid);
        assertEquals(Tool.SET_WARNING, d.tool);
    }

    @Test
    public void fallbackSingleCardAdjusts() {
        State s = new State(1, 60, "", 5, Arrays.asList("OnlyCard"));
        Decision d = AgentCore.fallback(s);
        assertEquals(Tool.ADJUST_RECOMMENDATION, d.tool);
        assertEquals("OnlyCard", d.argument);
    }

    @Test
    public void fallbackOtherwiseDoesNothing() {
        State s = new State(3, 80, "", 5, Arrays.asList("A", "B"));
        Decision d = AgentCore.fallback(s);
        assertEquals(Tool.DO_NOTHING, d.tool);
    }

    @Test
    public void buildPromptContainsProtocol() {
        State s = new State(2, 50, "缺运转", -2, Arrays.asList("A", "B"));
        String p = AgentCore.buildPrompt(s, ALL);
        assertTrue(p.contains("ADJUST_RECOMMENDATION"));
        assertTrue(p.contains("SET_WARNING"));
        assertTrue(p.contains("DO_NOTHING"));
        assertTrue(p.contains("\"action\""));
        assertTrue(p.contains("缺运转"));
    }

    @Test
    public void logAppendsLine() throws Exception {
        File dir = new File(System.getProperty("user.home"), "RunAdvisorLogs");
        if (dir.exists()) {
            File log = new File(dir, AgentCore.LOG_FILE_NAME);
            if (log.exists()) {
                log.delete(); // 测试前清掉，避免污染
            }
        }
        State s = new State(1, 40, "", 3, Arrays.asList("Backstab"));
        Decision d = AgentCore.parse(json("ADJUST_RECOMMENDATION", "Backstab", "抓它", 0.8), ALL, IDS);
        AgentCore.log(s, d, 120L, false);
        File log = new File(dir, AgentCore.LOG_FILE_NAME);
        String content = new String(Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("Backstab"));
        assertTrue(content.contains("ADJUST_RECOMMENDATION"));
        assertTrue(content.contains("120"));
    }

    @Test
    public void runWithAiReturnsParsedDecision() {
        AiRecommender ai = prompt -> json("ADJUST_RECOMMENDATION", "Backstab", "适合你", 0.9);
        Decision d = AgentCore.run(new State(2, 50, "", 3, Arrays.asList("Backstab", "Footwork")), ai, ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.ADJUST_RECOMMENDATION, d.tool);
        assertEquals("Backstab", d.argument);
    }

    @Test
    public void runNullRecommenderFallsBackLowHp() {
        State s = new State(2, 20, "", -3, Arrays.asList("Backstab", "Footwork"));
        Decision d = AgentCore.run(s, null, ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.SET_WARNING, d.tool); // 未配置 key → 低血兜底弹警告
    }

    @Test
    public void runAiFailureFallsBack() {
        AiRecommender ai = prompt -> null; // AI 失败
        State s = new State(2, 20, "", -3, Arrays.asList("A", "B"));
        Decision d = AgentCore.run(s, ai, ALL, IDS);
        assertEquals(Tool.SET_WARNING, d.tool); // 回落低血兜底
    }

    @Test
    public void runAiGarbageFallsBack() {
        AiRecommender ai = prompt -> "not json"; // AI 输出无效
        State s = new State(3, 80, "", 5, Arrays.asList("A", "B"));
        Decision d = AgentCore.run(s, ai, ALL, IDS);
        assertTrue(d.valid);
        assertEquals(Tool.DO_NOTHING, d.tool); // 高血多卡兜底 DO_NOTHING
    }
}
