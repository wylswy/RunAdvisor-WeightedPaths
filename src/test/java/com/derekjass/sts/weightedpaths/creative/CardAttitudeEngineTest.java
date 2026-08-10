package com.derekjass.sts.weightedpaths.creative;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 卡态度引擎测试：验证「违背推荐 → 生成有态度台词」的核心逻辑。
 * 台词文本是随机的，故只断言「是否生成 / 是否覆盖 / 是否清空」。
 */
public class CardAttitudeEngineTest {

    private static final String REC = "Footwork";
    private static final String OTHER = "Backstab";
    private static final String WEAK = "Quick Slash";

    @Test
    public void skippedDespiteRecommendation() {
        CardAttitudeEngine.evaluateReward(REC, false, "", true, "");
        assertTrue("该抓却跳过应生成台词", CardAttitudeEngine.hasPending());
        assertNotNull(CardAttitudeEngine.consumePendingLine());
    }

    @Test
    public void pickedDespiteSkipRecommendation() {
        CardAttitudeEngine.evaluateReward("", true, OTHER, false, "B");
        assertTrue("推荐跳过却抓卡应生成台词", CardAttitudeEngine.hasPending());
    }

    @Test
    public void ignoredRecommendedPickedOther() {
        CardAttitudeEngine.evaluateReward(REC, false, OTHER, false, "B");
        assertTrue("弃推荐抓别的应生成台词", CardAttitudeEngine.hasPending());
    }

    @Test
    public void pickedWeakAfterIgnoringRecommended() {
        CardAttitudeEngine.evaluateReward(REC, false, WEAK, false, "C");
        assertTrue("弃推荐抓C级应生成台词", CardAttitudeEngine.hasPending());
    }

    @Test
    public void followedRecommendation_noLine() {
        CardAttitudeEngine.evaluateReward(REC, false, REC, false, "B");
        assertFalse("按推荐抓卡不应生成台词", CardAttitudeEngine.hasPending());
    }

    @Test
    public void pendingNotOverwritten() {
        CardAttitudeEngine.setPendingForTest("已经怼了一句");
        CardAttitudeEngine.evaluateReward(REC, false, OTHER, false, "B");
        assertEquals("已有待显示台词时不应被覆盖", "已经怼了一句",
                CardAttitudeEngine.consumePendingLine());
    }

    @Test
    public void consumeClearsPending() {
        CardAttitudeEngine.setPendingForTest("一句台词");
        assertTrue(CardAttitudeEngine.hasPending());
        assertNotNull(CardAttitudeEngine.consumePendingLine());
        assertFalse("消费后应清空", CardAttitudeEngine.hasPending());
    }

    @Test
    public void mischiefTricked_returnsHappyLine() {
        CardMoodEngine.reset();
        String line = CardAttitudeEngine.evaluateMischiefResult(true);
        assertTrue("上当应生成得意台词", line != null && !line.isEmpty());
        assertTrue("上当后好感度应回升", CardMoodEngine.favor() >= 0);
    }

    @Test
    public void mischiefUntricked_returnsStubbornLineAndLowersFavor() {
        CardMoodEngine.reset();
        int before = CardMoodEngine.favor();
        String line = CardAttitudeEngine.evaluateMischiefResult(false);
        assertTrue("没上当应生成嘴硬台词", line != null && !line.isEmpty());
        assertTrue("没上当后好感度应下降", CardMoodEngine.favor() < before);
    }

    @Test
    public void mischiefTricked_lineIsCheerfulNotStubborn() {
        CardMoodEngine.reset();
        // 上当台词来自得意池，不应带「没上当」的嘴硬标记
        String line = CardAttitudeEngine.evaluateMischiefResult(true);
        assertFalse("上当台词不应说「没上当」", line.contains("没上当"));
        assertTrue("上当台词应带得意语气", line.contains("哈哈") || line.contains("上当")
                || line.contains("上钩") || line.contains("原谅"));
    }

    @Test
    public void mischiefUntricked_lineIsStubborn() {
        CardMoodEngine.reset();
        String line = CardAttitudeEngine.evaluateMischiefResult(false);
        assertTrue("没上当台词应提及没上当，实际=[" + line + "]",
                line.contains("没上当") || line.contains("没有上当") || line.contains("没中计"));
    }

    private static void assertEquals(String msg, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + "：期望=" + expected + " 实际=" + actual);
        }
    }
}
