package com.derekjass.sts.weightedpaths.creative;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** DeepSeek 客户端纯逻辑测试：请求体构造、响应解析、提示词构造（不实际联网）。 */
public class DeepSeekClientTest {

    @Test
    public void parseLine_validResponse() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"你缺的是防，你抓的是刀。行吧。\"}}]}";
        assertEquals("你缺的是防，你抓的是刀。行吧。", DeepSeekClient.parseLine(json));
    }

    @Test
    public void parseLine_trimsWhitespace() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"  放着你都敢不抓？  \"}}]}";
        assertEquals("放着你都敢不抓？", DeepSeekClient.parseLine(json));
    }

    @Test
    public void parseLine_emptyChoicesReturnsNull() {
        assertEquals(null, DeepSeekClient.parseLine("{\"choices\":[]}"));
    }

    @Test
    public void parseLine_malformedReturnsNull() {
        assertNull(DeepSeekClient.parseLine("not json"));
        assertNull(DeepSeekClient.parseLine(""));
        assertNull(DeepSeekClient.parseLine(null));
    }

    @Test
    public void buildRequestBody_containsModelAndUserMessage() {
        String body = DeepSeekClient.buildRequestBody("一句话");
        assertTrue("应包含模型", body.contains("deepseek-chat"));
        assertTrue("应包含消息", body.contains("一句话"));
        assertTrue("应包含 role=user", body.contains("\"role\":\"user\""));
    }

    @Test
    public void buildAttitudePrompt_mentionsChosenAndRecommended() {
        String prompt = DeepSeekClient.buildAttitudePrompt(
                "Footwork", false, "Backstab", false, "B", "牌组缺防");
        assertTrue("应提到推荐卡", prompt.contains("Footwork"));
        assertTrue("应提到实际抓卡", prompt.contains("Backstab"));
        assertTrue("应提到评级", prompt.contains("B"));
        assertTrue("应提到牌组状态", prompt.contains("牌组缺防"));
    }

    @Test
    public void buildAttitudePrompt_skippedCase() {
        String prompt = DeepSeekClient.buildAttitudePrompt(
                "Footwork", false, "", true, "", "牌组缺防");
        assertTrue("应提到推荐卡", prompt.contains("Footwork"));
        assertTrue("应提到跳过了", prompt.contains("跳过了"));
    }

    @Test
    public void generateLine_nullKeyReturnsNull() {
        assertNull(DeepSeekClient.generateLine(null, "prompt"));
        assertNull(DeepSeekClient.generateLine("  ", "prompt"));
        assertNull(DeepSeekClient.generateLine("key", null));
    }

    @Test
    public void generateLineResult_nullKeyReportsNotConfigured() {
        DeepSeekClient.AiResult r = DeepSeekClient.generateLineResult(null, "prompt");
        assertFalse("未配置 key 应报告 NOT_CONFIGURED", r.isSuccess());
        assertEquals(DeepSeekClient.Status.NOT_CONFIGURED, r.status);
        assertEquals("", r.text);
    }

    @Test
    public void generateLineResult_emptyPromptReportsNotConfigured() {
        DeepSeekClient.AiResult r = DeepSeekClient.generateLineResult("key", "");
        assertEquals(DeepSeekClient.Status.NOT_CONFIGURED, r.status);
    }

    @Test
    public void parseLine_handlesMultipleChoicesFirst() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"第一句\"}},{\"message\":{\"content\":\"第二句\"}}]}";
        assertEquals("第一句", DeepSeekClient.parseLine(json));
    }
}
