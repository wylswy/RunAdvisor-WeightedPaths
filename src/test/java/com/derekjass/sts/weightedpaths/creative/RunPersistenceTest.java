package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.ChatMessage;
import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.Sender;
import com.derekjass.sts.weightedpaths.creative.RunPersistence.PersistedMessage;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
                new ChatMessage(Sender.CARD, "哼，你别乱抓"), new ChatMessage(Sender.PLAYER, "我错了")), 3, 0, 0, null);
        assertTrue("同 seed+ts 应判定为同一局(SL)",
                RunPersistence.isSameRun(SEED, TS));
    }

    @Test
    public void isSameRun_differentSeedIsNewRun() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, 0, 0, 0, null);
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
        RunPersistence.saveCurrentRun(SEED, TS, null, 0, 0, 0, null);
        assertFalse(RunPersistence.isSameRun(null, TS));
    }

    @Test
    public void loadMessages_roundTrip() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, Arrays.asList(
                new ChatMessage(Sender.CARD, "卡的话"),
                new ChatMessage(Sender.PLAYER, "玩家的话"),
                new ChatMessage(Sender.CARD, "  ") /* 空消息应被忽略? 由 core 层处理，这里存 */), -2, 0, 0, null);
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
        RunPersistence.saveCurrentRun(SEED, TS, null, -5, 0, 0, null);
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
        RunPersistence.saveCurrentRun(SEED, TS, null, 7, 0, 0, null);
        RunPersistence.saveCurrentRun(null, TS, null, -9, 0, 0, null); // null seed 不应覆盖
        assertTrue("null seed 不应破坏已有指纹", RunPersistence.isSameRun(SEED, TS));
        assertEquals("null seed 不应覆盖 favor", 7, RunPersistence.loadFavor());
    }

    @Test
    public void clear_wipesState() {
        RunPersistence.setCustomDirForTest(tmpDir());
        RunPersistence.saveCurrentRun(SEED, TS, null, 2, 0, 0, null);
        assertTrue(RunPersistence.isSameRun(SEED, TS));
        RunPersistence.clear();
        assertFalse("clear 后应视为新局", RunPersistence.isSameRun(SEED, TS));
    }

    @Test
    public void probeStats_roundTrip() {
        String dir = tmpDir();
        RunPersistence.setCustomDirForTest(dir);
        RunPersistence.saveCurrentRun(SEED, TS, null, 0, 7, 3, null);
        assertEquals("聊天框打开次数应可恢复", 7, RunPersistence.loadChatboxOpens());
        assertEquals("玩家消息数应可恢复", 3, RunPersistence.loadChatboxPlayerMessages());
    }

    @Test
    public void probeStats_oldFileWithoutFieldsReturnsZero() throws Exception {
        String dir = tmpDir();
        RunPersistence.setCustomDirForTest(dir);
        java.io.File f = new java.io.File(dir, RunPersistence.FILE_NAME);
        java.nio.file.Files.write(f.toPath(),
                "{\"seed\":1,\"seedSourceTimestamp\":2,\"favor\":3}".getBytes("UTF-8"));
        assertEquals("旧文件无探针字段应返回0", 0, RunPersistence.loadChatboxOpens());
        assertEquals("旧文件无探针字段应返回0", 0, RunPersistence.loadChatboxPlayerMessages());
    }

    @Test
    public void save_doesNotLeaveTmpFile() {
        String dir = tmpDir();
        RunPersistence.setCustomDirForTest(dir);
        RunPersistence.saveCurrentRun(SEED, TS, null, 1, 0, 0, null);
        java.io.File f = new java.io.File(dir, RunPersistence.FILE_NAME);
        java.io.File tmp = new java.io.File(dir, RunPersistence.FILE_NAME + ".tmp");
        assertTrue("状态文件应存在", f.exists());
        assertFalse("原子写不应残留临时文件", tmp.exists());
    }

    @Test
    public void pactState_roundTrip() {
        RunPersistence.setCustomDirForTest(tmpDir());
        JsonObject pact = new JsonObject();
        pact.addProperty("lastAct", 2);
        pact.addProperty("conservative", true);
        JsonObject inner = new JsonObject();
        inner.addProperty("condition", "REACH_ELITE_HP_ABOVE_50");
        inner.addProperty("reward", "CONSERVATIVE_ADVICE");
        inner.addProperty("act", 1);
        inner.addProperty("status", "ACCEPTED");
        pact.add("pact", inner);
        RunPersistence.saveCurrentRun(SEED, TS, null, 0, 0, 0, pact);
        JsonObject loaded = RunPersistence.loadPactState();
        assertEquals(2, loaded.get("lastAct").getAsInt());
        assertTrue(loaded.get("conservative").getAsBoolean());
        assertEquals("ACCEPTED", loaded.getAsJsonObject("pact").get("status").getAsString());
    }

    @Test
    public void loadPactState_oldFileWithoutFieldReturnsNull() throws Exception {
        String dir = tmpDir();
        RunPersistence.setCustomDirForTest(dir);
        java.io.File f = new java.io.File(dir, RunPersistence.FILE_NAME);
        java.nio.file.Files.write(f.toPath(),
                "{\"seed\":1,\"seedSourceTimestamp\":2,\"favor\":3}".getBytes("UTF-8"));
        assertNull(RunPersistence.loadPactState());
    }
}
