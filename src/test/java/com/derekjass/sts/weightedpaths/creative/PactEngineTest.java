package com.derekjass.sts.weightedpaths.creative;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 契约/赌约状态机测试：提出/接受/拒绝、两类条件的完成与违背、幕结束结算、奖励只发一次。 */
public class PactEngineTest {

    @Test
    public void proposeCreatesOfferedPact() {
        PactEngine engine = new PactEngine();
        PactEngine.Pact pact = engine.propose(1, true, true);
        assertNotNull(pact);
        assertEquals(PactEngine.Condition.NO_ATTACK_CARDS_THIS_ACT, pact.condition);
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, pact.reward);
        assertEquals(PactEngine.Status.OFFERED, pact.status);
        assertTrue(engine.hasActive());
    }

    @Test
    public void proposeSelectsReachEliteWhenDeckDoesNotNeedDiscipline() {
        PactEngine engine = new PactEngine();
        PactEngine.Pact pact = engine.propose(1, false, true);
        assertEquals(PactEngine.Condition.REACH_ELITE_HP_ABOVE_50, pact.condition);
    }

    @Test
    public void proposeFallsBackToConservativeAdviceWithoutSeed() {
        PactEngine engine = new PactEngine();
        PactEngine.Pact pact = engine.propose(1, true, false);
        assertEquals(PactEngine.Reward.CONSERVATIVE_ADVICE, pact.reward);
    }

    @Test
    public void proposeReturnsNullWhenActive() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        assertNull(engine.propose(2, true, true));
    }

    @Test
    public void acceptThenStateAccepted() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.accept();
        assertEquals(PactEngine.Status.ACCEPTED, engine.current().status);
        assertTrue(engine.hasActive());
    }

    @Test
    public void declineClearsPact() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.decline();
        assertFalse(engine.hasActive());
        assertNull(engine.current());
    }

    @Test
    public void noAttackPactViolatedOnAttackPick() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_VIOLATE, engine.onCardPicked(true));
        assertEquals(PactEngine.Status.VIOLATED, engine.current().status);
    }

    @Test
    public void noAttackPactIgnoresNonAttackPick() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.accept();
        assertEquals(0, engine.onCardPicked(false));
        assertEquals(PactEngine.Status.ACCEPTED, engine.current().status);
    }

    @Test
    public void noAttackPactCompletedOnActEnd() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.accept();
        engine.onCardPicked(false);
        assertEquals(PactEngine.FAVOR_COMPLETE, engine.onActEnd());
        assertEquals(PactEngine.Status.COMPLETED, engine.current().status);
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, engine.pendingReward());
    }

    @Test
    public void reachElitePactCompletedWhenHpAboveThreshold() {
        PactEngine engine = new PactEngine();
        engine.propose(1, false, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_COMPLETE, engine.onEliteReached(60));
        assertEquals(PactEngine.Status.COMPLETED, engine.current().status);
    }

    @Test
    public void reachElitePactViolatedWhenHpBelowThreshold() {
        PactEngine engine = new PactEngine();
        engine.propose(1, false, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_VIOLATE, engine.onEliteReached(40));
        assertEquals(PactEngine.Status.VIOLATED, engine.current().status);
    }

    @Test
    public void reachElitePactViolatedWhenActEndsWithoutElite() {
        PactEngine engine = new PactEngine();
        engine.propose(1, false, true);
        engine.accept();
        assertEquals(PactEngine.FAVOR_VIOLATE, engine.onActEnd());
        assertEquals(PactEngine.Status.VIOLATED, engine.current().status);
    }

    @Test
    public void eventsIgnoredWhenNoActivePact() {
        PactEngine engine = new PactEngine();
        assertEquals(0, engine.onCardPicked(true));
        assertEquals(0, engine.onEliteReached(30));
        assertEquals(0, engine.onActEnd());
    }

    @Test
    public void rewardConsumedOnce() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.accept();
        engine.onActEnd();
        assertEquals(PactEngine.Reward.REVEAL_NEXT_ELITE, engine.consumeReward());
        assertNull(engine.consumeReward());
    }

    @Test
    public void violationDoesNotProduceReward() {
        PactEngine engine = new PactEngine();
        engine.propose(1, true, true);
        engine.accept();
        engine.onCardPicked(true);
        assertNull(engine.pendingReward());
    }
}