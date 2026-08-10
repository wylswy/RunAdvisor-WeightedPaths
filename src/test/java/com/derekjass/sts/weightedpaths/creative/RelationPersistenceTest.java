package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.RelationPersistence.RelationData;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 跨局关系档案持久化测试：读写 round-trip + 缺省/旧文件兜底（用临时目录，不碰真实档案）。 */
public class RelationPersistenceTest {

    private String tmpDir() {
        try {
            File f = Files.createTempDirectory("relation_test").toFile();
            f.deleteOnExit();
            return f.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void saveLoad_roundTrip() {
        RelationPersistence.setCustomDirForTest(tmpDir());
        RelationData d = new RelationData();
        d.bonding = 9;
        d.totalRuns = 3;
        d.totalVictories = 1;
        d.maxFloorReached = 42;
        d.lastTierIndex = 2;
        d.pendingUpgrade = true;
        RelationPersistence.save(d);

        RelationData loaded = RelationPersistence.load();
        assertEquals(9, loaded.bonding);
        assertEquals(3, loaded.totalRuns);
        assertEquals(1, loaded.totalVictories);
        assertEquals(42, loaded.maxFloorReached);
        assertEquals(2, loaded.lastTierIndex);
        assertTrue("pendingUpgrade 应保留", loaded.pendingUpgrade);
    }

    @Test
    public void load_noFileReturnsNull() {
        RelationPersistence.setCustomDirForTest(tmpDir());
        assertNull("无档案应视为全新玩家", RelationPersistence.load());
    }

    @Test
    public void saveLoad_lastRunSummaryRoundTrip() {
        RelationPersistence.setCustomDirForTest(tmpDir());
        RelationData d = new RelationData();
        d.bonding = 2;
        d.totalRuns = 1;
        d.lastRunSummary = new RelationPersistence.LastRunSummary();
        d.lastRunSummary.floorReached = 17;
        d.lastRunSummary.victory = false;
        d.lastRunSummary.pickedCardIds.add("幽魂形态");
        d.lastRunSummary.pickedCardIds.add("杂技");
        d.lastRunSummary.chatHighlights.add("这局帮我");
        RelationPersistence.save(d);

        RelationData loaded = RelationPersistence.load();
        assertEquals(17, loaded.lastRunSummary.floorReached);
        assertFalse(loaded.lastRunSummary.victory);
        assertEquals(Arrays.asList("幽魂形态", "杂技"), loaded.lastRunSummary.pickedCardIds);
        assertEquals(Arrays.asList("这局帮我"), loaded.lastRunSummary.chatHighlights);
    }

    @Test
    public void load_oldFileWithoutPendingUpgradeDefaultsFalse() throws Exception {
        String dir = tmpDir();
        RelationPersistence.setCustomDirForTest(dir);
        File f = new File(dir, RelationPersistence.FILE_NAME);
        // 模拟早期版本写的档案（没有 pendingUpgrade 字段）
        Files.write(f.toPath(),
                "{\"bonding\":5,\"totalRuns\":2,\"totalVictories\":0,\"maxFloorReached\":20,\"lastTierIndex\":1}"
                        .getBytes("UTF-8"));
        RelationData loaded = RelationPersistence.load();
        assertFalse("旧档案缺 pendingUpgrade 应读回 false", loaded.pendingUpgrade);
        assertEquals(5, loaded.bonding);
    }
}
