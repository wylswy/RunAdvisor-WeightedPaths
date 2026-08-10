package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.ChatMessage;
import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.Sender;
import com.derekjass.sts.weightedpaths.creative.RunPersistence.PersistedMessage;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Run 状态持久化测试：指纹判断 + 消息/好感度存取（用临时目录，不碰真实 ~/RunAdvisorLogs）。 */
public class RunPersistenceTest {

    private static final long SEED = 123456789L;
    private static final long TS = 9876543210L;

    private String tmpDir() {
        try {
            java.nio.file.Path p = Files.createTempDirectory("runp_test");
            File f = p.toFile();
            f.deleteOnExit();
            return f.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void saveAndIsSameRun_matchingFingerprint() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, Arrays.asList(
                new ChatMessage(Sender.CARD, "哼，你别乱抓"), new ChatMessage(Sender.PLAYER, "我错了")), 3);
        assertTrue("同 seed+ts 应判定为同一局(SL)",
                RunPersistence.isSameRun(SEED, TS));
    }

    @Test
    public void isSameRun_differentSeedIsNewRun() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, 0);
        assertFalse("不同 seed 应为新局", RunPersistence.isSameRun(SEED + 1, TS));
        assertFalse("不同 ts 应为新局", RunPersistence.isSameRun(SEED, TS + 1));
    }

    @Test
    public void isSameRun_noStateFileReturnsFalse() {
        RunPersistence.setCustomDirForTest(tmpDir());
        assertFalse("无状态文件应视为新局", RunPersistence.isSameRun(SEED, TS));
    }

    @Test
    public void isSameRun_nullSeedReturnsFalse() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, 0);
        assertFalse(RunPersistence.isSameRun(null, TS));
    }

    @Test
    public void loadMessages_roundTrip() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, Arrays.asList(
                new ChatMessage(Sender.CARD, "卡的话"),
                new ChatMessage(Sender.PLAYER, "玩家的话"),
                new ChatMessage(Sender.CARD, "  ") /* 空消息应被忽略? 由 core 层处理，这里存 */), -2);
        List<PersistedMessage> msgs = RunPersistence.loadMessages();
        assertEquals("应存下 3 条", 3, msgs.size());
        assertEquals(Sender.CARD, msgs.get(0).sender);
        assertEquals("卡的话", msgs.get(0).text);
        assertEquals(Sender.PLAYER, msgs.get(1).sender);
        assertEquals("玩家的话", msgs.get(1).text);
    }

    @Test
    public void loadFavor_roundTrip() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, -5);
        assertEquals(-5, RunPersistence.loadFavor());
    }

    @Test
    public void loadMessages_noStateReturnsEmpty() {
        RunPersistence.setCustomDirForTest(tmpDir());
        assertTrue(RunPersistence.loadMessages().isEmpty());
        assertEquals(0, RunPersistence.loadFavor());
    }

    @Test
    public void saveNullSeed_doesNotOverwrite() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, 7);
        RunPersistence.saveCurrentRun(null, TS, null, -9); // null seed 不应覆盖
        assertTrue("null seed 不应破坏已有指纹", RunPersistence.isSameRun(SEED, TS));
        assertEquals("null seed 不应覆盖 favor", 7, RunPersistence.loadFavor());
    }

    @Test
    public void clear_wipesState() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, 2);
        assertTrue(RunPersistence.isSameRun(SEED, TS));
        RunPersistence.clear();
        assertFalse("clear 后应视为新局", RunPersistence.isSameRun(SEED, TS));
    }
}
