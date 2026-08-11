package com.derekjass.sts.weightedpaths.creative;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 契约/赌约状态机测试：提出/接受/拒绝、REACH_ELITE 的完成与违背、幕结束结算、奖励只发一次。 */
public class PactEngineTest {

    @Test
    public void proposeCreatesOfferedPact() {
        PactEngine engine = new PactEngine();
        PactEngine.Pact pact = engine.propose(1, true);
        assertNotNull(pact);
        assertEquals(PactEngine.Condition.REACH_ELITE_HP_ABOVE_50, pact.condition);
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, pact.reward);
        assertEquals(PactEngine.Status.OFFERED, pact.status);
        assertTrue(engine.hasActive());
    }

    @Test
    public void proposeFallsBackToConservativeAdviceWithoutSeed() {
        PactEngine engine = new PactEngine();
        PactEngine.Pact pact = engine.propose(1, false);
        assertEquals(PactEngine.Reward.CONSERVATIVE_ADVICE, pact.reward);
    }

    @Test
    public void proposeReturnsNullWhenActive() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        assertNull(engine.propose(2, true));
    }

    @Test
    public void acceptThenStateAccepted() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        assertEquals(PactEngine.Status.ACCEPTED, engine.current().status);
        assertTrue(engine.hasActive());
    }

    @Test
    public void declineClearsPact() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.decline();
        assertFalse(engine.hasActive());
        assertNull(engine.current());
    }

    @Test
    public void reachElitePactCompletedWhenHpAboveThreshold() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_COMPLETE, engine.onEliteReached(60));
        assertEquals(PactEngine.Status.COMPLETED, engine.current().status);
    }

    @Test
    public void reachElitePactViolatedWhenHpBelowThreshold() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_VIOLATE, engine.onEliteReached(40));
        assertEquals(PactEngine.Status.VIOLATED, engine.current().status);
    }

    @Test
    public void reachElitePactViolatedWhenActEndsWithoutElite() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_VIOLATE, engine.onActEnd());
        assertEquals(PactEngine.Status.VIOLATED, engine.current().status);
    }

    @Test
    public void eventsIgnoredWhenNoActivePact() {
        PactEngine engine = new PactEngine();
        assertEquals(0, engine.onEliteReached(30));
        assertEquals(0, engine.onActEnd());
    }

    @Test
    public void rewardConsumedOnce() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        engine.onEliteReached(60);
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, engine.consumeReward());
        assertNull(engine.consumeReward());
    }

    @Test
    public void violationDoesNotProduceReward() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        engine.onEliteReached(30);
        assertNull(engine.pendingReward());
    }

    @Test
    public void exportState_noActivePactReturnsNull() {
        PactEngine engine = new PactEngine();
        assertNull(engine.exportState());
    }

    @Test
    public void exportRestore_roundTripPreservesAcceptedPact() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        com.google.gson.JsonObject o = engine.exportState();
        PactEngine restored = new PactEngine();
        restored.restoreState(o);
        assertEquals(PactEngine.Condition.REACH_ELITE_HP_ABOVE_50, restored.current().condition);
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, restored.current().reward);
        assertEquals(1, restored.current().acceptedAct);
        assertEquals(PactEngine.Status.ACCEPTED, restored.current().status);
        assertTrue(restored.hasActive());
    }

    @Test
    public void exportRestore_completedKeepsPendingReward() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.accept();
        engine.onEliteReached(60);
        PactEngine restored = new PactEngine();
        restored.restoreState(engine.exportState());
        assertEquals(PactEngine.Status.COMPLETED, restored.current().status);
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, restored.pendingReward());
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, restored.consumeReward());
        assertNull(restored.consumeReward());
    }

    @Test
    public void restoreState_invalidDataClearsState() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        com.google.gson.JsonObject bad = new com.google.gson.JsonObject();
        bad.addProperty("condition", "NOT_A_CONDITION");
        bad.addProperty("reward", "REVEAL_NEXT_ELITE");
        bad.addProperty("act", 1);
        bad.addProperty("status", "ACCEPTED");
        engine.restoreState(bad);
        assertFalse(engine.hasActive());
        assertNull(engine.current());
        assertNull(engine.pendingReward());
    }

    @Test
    public void restoreState_nullKeepsCurrentState() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true);
        engine.restoreState(null);
        assertTrue(engine.hasActive());
        assertEquals(PactEngine.Status.OFFERED, engine.current().status);
    }
}