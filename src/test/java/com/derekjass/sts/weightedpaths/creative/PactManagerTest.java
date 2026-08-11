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
        List<String> msgs = PactManager.onMapOpen(1, true);
        assertFalse(msgs.isEmpty());
        assertTrue(msgs.get(0).contains("提议"));
        assertTrue(PactManager.engine().hasActive());
        assertEquals(PactEngine.Status.OFFERED, PactManager.engine().current().status);
        assertEquals(PactEngine.Condition.REACH_ELITE_HP_ABOVE_50, PactManager.engine().current().condition);
    }

    @Test
    public void onMapOpenSameActReturnsNothing() {
        PactManager.onMapOpen(1, true);
        List<String> second = PactManager.onMapOpen(1, true);
        assertTrue(second.isEmpty());
    }

    @Test
    public void onMapOpenActChangeSettlesPreviousAct() {
        PactManager.onMapOpen(1, false);
        PactManager.onChatInput("同意");
        List<String> msgs = PactManager.onMapOpen(2, true);
        // 结算消息 + 新幕提案；REACH_ELITE 没到精英直接过幕 → 违背 -2
        assertEquals(2, msgs.size());
        assertEquals(-2, CardMoodEngine.favor());
    }

    @Test
    public void chatAcceptAcceptsPact() {
        PactManager.onMapOpen(1, true);
        String reply = PactManager.onChatInput("同意");
        assertFalse(reply.isEmpty());
        assertEquals(PactEngine.Status.ACCEPTED, PactManager.engine().current().status);
    }

    @Test
    public void chatDeclineClearsPact() {
        PactManager.onMapOpen(1, true);
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
        PactManager.onMapOpen(1, true);
        assertEquals("", PactManager.onChatInput("今天天气不错"));
        assertEquals(PactEngine.Status.OFFERED, PactManager.engine().current().status);
    }



    @Test
    public void eliteReachedCompletesAndGrantsReward() {
        PactManager.onMapOpen(1, true);
        PactManager.onChatInput("同意");
        String msg = PactManager.onEliteReached(70);
        assertTrue(msg.contains("兑现"));
        assertEquals(2, CardMoodEngine.favor());
        assertEquals(PactEngine.Status.COMPLETED, PactManager.engine().current().status);
    }

    @Test
    public void eliteReachedBelowThresholdViolates() {
        PactManager.onMapOpen(1, true);
        PactManager.onChatInput("同意");
        String msg = PactManager.onEliteReached(30);
        assertFalse(msg.isEmpty());
        assertEquals(-2, CardMoodEngine.favor());
    }

    @Test
    public void conservativeAdviceFlagSetOnReward() {
        PactManager.onMapOpen(1, false); // 无种子 → CONSERVATIVE_ADVICE
        PactManager.onChatInput("同意");
        PactManager.onEliteReached(70); // REACH_ELITE 完成
        assertTrue(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void resetClearsEverything() {
        PactManager.onMapOpen(1, true);
        PactManager.onChatInput("同意");
        PactManager.resetForRun();
        assertNull(PactManager.engine().current());
        assertFalse(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void exportRestore_roundTripKeepsPact() {
        PactManager.onMapOpen(1, true);
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
        PactManager.onMapOpen(1, false); // 无种子 → CONSERVATIVE_ADVICE
        PactManager.engine().accept();
        PactManager.onEliteReached(70); // REACH_ELITE 完成，奖励置保守建议标志
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
        PactManager.onMapOpen(1, true);
        PactManager.restoreState(null);
        assertNull(PactManager.engine().current());
        assertFalse(PactManager.isConservativeAdviceActive());
    }

    @Test
    public void violationActivatesGrudgeAndNextCompletionClearsIt() {
        PactManager.onMapOpen(1, true); // REACH_ELITE
        PactManager.onChatInput("同意");
        PactManager.onEliteReached(30); // 低血进精英 → 违背 → 记仇
        assertTrue(PactManager.isGrudgeActive());
        PactManager.onMapOpen(2, true); // 下一幕新契约
        PactManager.onChatInput("同意");
        PactManager.onEliteReached(70); // 完成 → 原谅
        assertFalse(PactManager.isGrudgeActive());
    }

    @Test
    public void grudgePersistsAcrossSaveRestore() {
        PactManager.onMapOpen(1, true);
        PactManager.onChatInput("同意");
        PactManager.onEliteReached(30); // 违背
        com.google.gson.JsonObject state = PactManager.exportState();
        PactManager.resetForRun();
        PactManager.restoreState(state);
        assertTrue(PactManager.isGrudgeActive());
    }

    @Test
    public void rewardText_usesHiddenIntel() {
        PactManager.setEliteIntelProvider(() -> new PactManager.EliteIntel() {
            @Override
            public int roomsUntilElite() {
                return 3;
            }

            @Override
            public java.util.List<String> upcomingThisAct() {
                return java.util.Arrays.asList("M", "R", "E");
            }

            @Override
            public String nextRoom() {
                return "M";
            }

            @Override
            public int roomsUntilSymbol(String target) {
                return "R".equals(target) ? 2 : -1;
            }

            @Override
            public int futureEliteCount() {
                return 2;
            }

            @Override
            public int futureRestCount() {
                return 1;
            }

            @Override
            public double routeValueLead() {
                return 12.4;
            }
        });
        try {
            PactManager.onMapOpen(1, true); // REACH_ELITE + 有种子
            PactManager.onChatInput("同意");
            String msg = PactManager.onEliteReached(70);
            assertTrue("应报未来精英: " + msg, msg.contains("后面还有 2 个精英"));
            assertTrue("应报路线领先: " + msg, msg.contains("领先次优约 12 分"));
        } finally {
            PactManager.setEliteIntelProvider(() -> null);
        }
    }

    @Test
    public void describeCurrentPact_returnsTextOnlyWhenActive() {
        PactManager.resetForRun();
        assertEquals("", PactManager.describeCurrentPact());
        PactManager.onMapOpen(1, true);
        assertTrue(PactManager.describeCurrentPact().contains("精英"));
    }

    @Test
    public void chatDeclineWithNegationDeclinesInsteadOfAccepting() {
        PactManager.onMapOpen(1, true);
        String reply = PactManager.onChatInput("不同意");
        assertFalse(reply.isEmpty());
        assertNull(PactManager.engine().current());
    }
}
