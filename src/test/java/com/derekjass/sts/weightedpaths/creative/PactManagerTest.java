package com.derekjass.sts.weightedpaths.creative;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 契约接线层测试：幕切换提案、聊天接受/拒绝、好感度应用、奖励发放与保守建议标志。 */
public class PactManagerTest {

    @Before
    public void setUp() {
        PactManager.resetForRun();
        CardMoodEngine.reset();
    }

    @Test
    public void onMapOpenActChangeProposes() {
        List<String> msgs = PactManager.onMapOpen(1, true, true);
        assertFalse(msgs.isEmpty());
        assertTrue(msgs.get(0).contains("提议"));
        assertTrue(PactManager.engine().hasActive());
        assertEquals(PactEngine.Status.OFFERED, PactManager.engine().current().status);
        assertEquals(PactEngine.Condition.NO_ATTACK_CARDS_THIS_ACT, PactManager.engine().current().condition);
    }

    @Test
    public void onMapOpenSameActReturnsNothing() {
        PactManager.onMapOpen(1, true, true);
        List<String> second = PactManager.onMapOpen(1, true, true);
        assertTrue(second.isEmpty());
    }

    @Test
    public void onMapOpenActChangeSettlesPreviousAct() {
        PactManager.onMapOpen(1, true, false);
        PactManager.onChatInput("同意");
        List<String> msgs = PactManager.onMapOpen(2, true, false);
        // 结算消息 + 新幕提案
        assertEquals(2, msgs.size());
        assertEquals(2, CardMoodEngine.favor()); // NO_ATTACK 完成 +2
    }

    @Test
    public void chatAcceptAcceptsPact() {
        PactManager.onMapOpen(1, true, true);
        String reply = PactManager.onChatInput("同意");
        assertFalse(reply.isEmpty());
        assertEquals(PactEngine.Status.ACCEPTED, PactManager.engine().current().status);
    }

    @Test
    public void chatDeclineClearsPact() {
        PactManager.onMapOpen(1, true, true);
        String reply = PactManager.onChatInput("拒绝");
        assertFalse(reply.isEmpty());
        assertNull(PactManager.engine().current());
    }

    @Test
    public void chatInputWithoutPactReturnsEmpty() {
        assertEquals("", PactManager.onChatInput("同意"));
    }

    @Test
    public void chatUnrelatedKeepsOffer() {
        PactManager.onMapOpen(1, true, true);
        assertEquals("", PactManager.onChatInput("今天天气不错"));
        assertEquals(PactEngine.Status.OFFERED, PactManager.engine().current().status);
    }

    @Test
    public void attackPickViolatesAndDropsFavor() {
        PactManager.onMapOpen(1, true, true);
        PactManager.onChatInput("同意");
        String msg = PactManager.onCardPicked(true);
        assertFalse(msg.isEmpty());
        assertEquals(-2, CardMoodEngine.favor());
        assertEquals(PactEngine.Status.VIOLATED, PactManager.engine().current().status);
    }

    @Test
    public void nonAttackPickKeepsPact() {
        PactManager.onMapOpen(1, true, true);
        PactManager.onChatInput("同意");
        assertEquals("", PactManager.onCardPicked(false));
        assertEquals(0, CardMoodEngine.favor());
        assertEquals(PactEngine.Status.ACCEPTED, PactManager.engine().current().status);
    }

    @Test
    public void eliteReachedCompletesAndGrantsReward() {
        PactManager.onMapOpen(1, false, true);
        PactManager.onChatInput("同意");
        String msg = PactManager.onEliteReached(70);
        assertTrue(msg.contains("兑现"));
        assertEquals(2, CardMoodEngine.favor());
        assertEquals(PactEngine.Status.COMPLETED, PactManager.engine().current().status);
    }

    @Test
    public void eliteReachedBelowThresholdViolates() {
        PactManager.onMapOpen(1, false, true);
        PactManager.onChatInput("同意");
        String msg = PactManager.onEliteReached(30);
        assertFalse(msg.isEmpty());
        assertEquals(-2, CardMoodEngine.favor());
    }

    @Test
    public void conservativeAdviceFlagSetOnReward() {
        PactManager.onMapOpen(1, true, false); // 无种子 → CONSERVATIVE_ADVICE
        PactManager.onChatInput("同意");
        PactManager.onActEnd(); // NO_ATTACK 完成
        assertTrue(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void resetClearsEverything() {
        PactManager.onMapOpen(1, true, true);
        PactManager.onChatInput("同意");
        PactManager.resetForRun();
        assertNull(PactManager.engine().current());
        assertFalse(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void exportRestore_roundTripKeepsPact() {
        PactManager.onMapOpen(1, true, true);
        PactManager.engine().accept();
        com.google.gson.JsonObject state = PactManager.exportState();
        PactManager.resetForRun();
        PactManager.restoreState(state);
        assertEquals(PactEngine.Status.ACCEPTED, PactManager.engine().current().status);
        assertEquals(1, PactManager.engine().current().acceptedAct);
        assertFalse(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void restoreState_restoresConservativeFlag() {
        PactManager.onMapOpen(1, true, false); // 无种子 → CONSERVATIVE_ADVICE
        PactManager.engine().accept();
        PactManager.onActEnd(); // NO_ATTACK 完成，奖励置保守建议标志
        com.google.gson.JsonObject state = PactManager.exportState();
        PactManager.resetForRun();
        PactManager.restoreState(state);
        assertTrue(PactManager.isConservativeAdviceActive());
        assertEquals(PactEngine.Status.COMPLETED, PactManager.engine().current().status);
    }

    @Test
    public void restoreState_invalidPactClearsPactKeepsFlags() {
        com.google.gson.JsonObject bad = new com.google.gson.JsonObject();
        bad.addProperty("lastAct", 1);
        bad.addProperty("conservative", true);
        com.google.gson.JsonObject pact = new com.google.gson.JsonObject();
        pact.addProperty("condition", "BOGUS");
        bad.add("pact", pact);
        PactManager.restoreState(bad);
        assertNull(PactManager.engine().current());
        assertTrue(PactManager.isConservativeAdviceActive());
        assertEquals(1, PactManager.exportState().get("lastAct").getAsInt());
    }

    @Test
    public void restoreState_invalidTopLevelResets() {
        com.google.gson.JsonObject bad = new com.google.gson.JsonObject();
        bad.addProperty("lastAct", "not-an-int");
        PactManager.restoreState(bad);
        assertNull(PactManager.engine().current());
        assertFalse(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void restoreState_nullResets() {
        PactManager.onMapOpen(1, true, true);
        PactManager.restoreState(null);
        assertNull(PactManager.engine().current());
        assertFalse(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void chatDeclineWithNegationDeclinesInsteadOfAccepting() {
        PactManager.onMapOpen(1, true, true);
        String reply = PactManager.onChatInput("不同意");
        assertFalse(reply.isEmpty());
        assertNull(PactManager.engine().current());
    }
}
