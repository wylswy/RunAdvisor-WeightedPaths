package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.PlayerRelation.Tier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** 长期陪伴逻辑层测试：关系阶段/结算/升级/开场白 + 真记得记忆。 */
public class PlayerRelationTest {

    @Before
    public void setUp() {
        RelationPersistence.setCustomDirForTest(tmpDir());
        PlayerRelation.resetForTest();
    }

    @After
    public void tearDown() {
        PlayerRelation.resetForTest();
    }

    private String tmpDir() {
        try {
            File f = Files.createTempDirectory("relation_logic").toFile();
            f.deleteOnExit();
            return f.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void newPlayer_startsStranger() {
        PlayerRelation r = PlayerRelation.get();
        assertEquals(Tier.STRANGER, r.tier());
        assertEquals(0, r.bonding());
        assertEquals(0, r.totalRuns());
        assertEquals(0, r.totalVictories());
        assertEquals(0, r.maxFloorReached());
    }

    @Test
    public void settleRun_lossAccumulatesStats() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 12, 3, Collections.emptyList(), Collections.emptyList());
        assertEquals("失败局 bonding = 1(对局)+0(非胜)+3(聊天)", 4, r.bonding());
        assertEquals(1, r.totalRuns());
        assertEquals(0, r.totalVictories());
        assertEquals(12, r.maxFloorReached());
    }

    @Test
    public void settleRun_winGivesExtraBondingAndKeepsMaxFloor() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(true, 50, 0, Collections.emptyList(), Collections.emptyList());
        assertEquals("胜利局 bonding = 1(对局)+1(胜)+0", 2, r.bonding());
        assertEquals(1, r.totalVictories());
        assertEquals(50, r.maxFloorReached());
        r.settleRun(false, 30, 0, Collections.emptyList(), Collections.emptyList());
        assertEquals(50, r.maxFloorReached());
    }

    @Test
    public void tierProgressesWithBonding() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        assertEquals(Tier.FRIEND, r.tier());
    }

    @Test
    public void upgrade_setsPendingUpgradeFlag() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        assertTrue("跨过阈值应挂起升级标志", r.pendingUpgradeForTest());
    }

    @Test
    public void greeting_consumesUpgradeAndFallsBack() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        String first = r.greeting();
        assertFalse(r.pendingUpgradeForTest());
        assertNotEquals("", first);
        assertNotEquals("", r.greeting());
    }

    @Test
    public void relation_persistsAcrossReload() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(true, 40, 2, Arrays.asList("幽魂形态", "杂技"), Arrays.asList("我答应听你的"));
        PlayerRelation.resetForTest();
        PlayerRelation r2 = PlayerRelation.get();
        assertEquals(4, r2.bonding());
        assertEquals(1, r2.totalRuns());
        assertEquals(1, r2.totalVictories());
        assertEquals(40, r2.maxFloorReached());
        assertEquals("跨局档案应保留上把抓的牌", "幽魂形态", r2.pickedCardIdsForTest().get(0));
    }

    // ---- 真记得（长期陪伴核心）----

    @Test
    public void greeting_recallsDeathFloorAndPickedCard() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 17, 0, Arrays.asList("幽魂形态"), Collections.emptyList());
        // 消费升级标志后再看"真记得"开场
        r.greeting();
        String g = r.greeting();
        assertTrue("开场应提到死在第17层", g.contains("17"));
        assertTrue("开场应提到上把抓的幽魂形态", g.contains("幽魂形态"));
    }

    @Test
    public void greeting_recallsVictory() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(true, 51, 0, Collections.emptyList(), Collections.emptyList());
        r.greeting(); // 消费升级
        String g = r.greeting();
        assertTrue("上把赢了应被记着", g.contains("赢"));
    }

    @Test
    public void memoryText_includesFactsAndHonestyGuardrail() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 9, 0, Arrays.asList("刀刃之舞"), Arrays.asList("这局帮我"));
        String m = r.memoryText();
        assertTrue("记忆应含上把抓的牌", m.contains("刀刃之舞"));
        assertTrue("记忆应含诚实护栏", m.contains("记不清"));
        assertTrue("记忆应含玩家说过的话", m.contains("这局帮我"));
    }

    @Test
    public void memoryText_emptyWhenNoMemory() {
        PlayerRelation r = PlayerRelation.get();
        assertEquals("无记忆应返回空串", "", r.memoryText());
    }

    @Test
    public void greeting_neverBlankForAnyTier() {
        PlayerRelation r = PlayerRelation.get();
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        assertNotEquals("", r.greeting());
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        assertNotEquals("", r.greeting());
        r.settleRun(false, 5, 5, Collections.emptyList(), Collections.emptyList());
        assertNotEquals("", r.greeting());
    }
}
