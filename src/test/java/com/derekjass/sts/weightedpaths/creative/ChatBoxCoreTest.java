package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.ChatMessage;
import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.Sender;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 聊天框核心测试：对话管理、道歉触发、提示词构造（不联网）。 */
public class ChatBoxCoreTest {

    @Test
    public void addCardMessageAppends() {
        ChatBoxCore core = new ChatBoxCore();
        core.addCardMessage("你好");
        core.addCardMessage("  你缺的是防  ");
        assertEquals(2, core.messages().size());
        assertEquals(Sender.CARD, core.messages().get(1).sender);
        assertEquals("你缺的是防", core.messages().get(1).text); // 去空白
    }

    @Test
    public void playerMessageAppendsAsPlayer() {
        ChatBoxCore core = new ChatBoxCore();
        core.onPlayerSend("这局怎么打");
        assertEquals(1, core.messages().size());
        assertEquals(Sender.PLAYER, core.messages().get(0).sender);
        assertEquals("这局怎么打", core.messages().get(0).text);
    }

    @Test
    public void blankInputIgnored() {
        ChatBoxCore core = new ChatBoxCore();
        core.onPlayerSend("");
        core.onPlayerSend("   ");
        core.onPlayerSend(null);
        assertEquals(0, core.messages().size());
    }

    @Test
    public void apologyRaisesFavor() {
        ChatBoxCore core = new ChatBoxCore();
        CardMoodEngine.reset();
        for (int i = 0; i < 3; i++) {
            CardMoodEngine.recordDefiance();
        }
        int before = CardMoodEngine.favor();
        core.onPlayerSend("对不起我错了");
        assertTrue("道歉应提升好感度", CardMoodEngine.favor() > before);
    }

    @Test
    public void buildChatPromptContainsIdentityHistoryAndPlayerText() {
        ChatBoxCore core = new ChatBoxCore();
        core.addCardMessage("哼，你又不听我的");
        core.onPlayerSend("我错了");
        String prompt = ChatBoxCore.buildChatPrompt(
                core.messages(), "当前第2层", CardMoodEngine.favor(), "我以后听你的");
        assertTrue("应含身份约束", prompt.contains("杀戮尖塔"));
        assertTrue("应含情境", prompt.contains("当前第2层"));
        assertTrue("应含历史卡话", prompt.contains("你又不听我的"));
        assertTrue("应含玩家消息", prompt.contains("我以后听你的"));
        assertTrue("应含好感度", prompt.contains("好感度"));
    }

    @Test
    public void buildChatPromptMarksSenders() {
        ChatBoxCore core = new ChatBoxCore();
        core.addCardMessage("卡的话");
        core.onPlayerSend("玩家的话");
        String prompt = ChatBoxCore.buildChatPrompt(core.messages(), "", 0, "再来一句");
        assertTrue(prompt.contains("卡：卡的话"));
        assertTrue(prompt.contains("玩家：玩家的话"));
    }

    @Test
    public void restoreMessages_appendsAndTrims() {
        ChatBoxCore core = new ChatBoxCore();
        core.restoreMessages(java.util.Arrays.asList(
                new ChatMessage(Sender.CARD, "你跑啦？"),
                new ChatMessage(Sender.PLAYER, "  我回来了  "),
                new ChatMessage(Sender.CARD, "   "), // 空白应被忽略
                null));
        assertEquals(2, core.messages().size());
        assertEquals(Sender.CARD, core.messages().get(0).sender);
        assertEquals("你跑啦？", core.messages().get(0).text);
        assertEquals("我回来了", core.messages().get(1).text); // 去空白
    }

    @Test
    public void onChangeFiresOnMessage() {
        ChatBoxCore core = new ChatBoxCore();
        final int[] count = {0};
        core.setOnChange(() -> count[0]++);
        core.addCardMessage("一句");
        core.onPlayerSend("玩家");
        core.addCardMessage("  "); // 空白不触发
        assertEquals(2, count[0]);
    }

    @Test
    public void recordOpenAndPlayerMessagesCount() {
        ChatBoxCore core = new ChatBoxCore();
        assertEquals(0, core.openCount());
        assertEquals(0, core.playerMessageCount());
        core.recordOpen();
        core.recordOpen();
        assertEquals(2, core.openCount());
        core.onPlayerSend("你好");
        core.onPlayerSend("   "); // 空白不计
        core.onPlayerSend(null);
        assertEquals("空白/空消息不应计数", 1, core.playerMessageCount());
    }

    @Test
    public void restoreProbeStats_continuesAccumulation() {
        ChatBoxCore core = new ChatBoxCore();
        core.recordOpen();
        core.onPlayerSend("一句");
        core.restoreProbeStats(core.openCount(), core.playerMessageCount()); // 模拟 SL 恢复
        core.recordOpen();
        core.onPlayerSend("第二句");
        assertEquals("SL 恢复后打开次数继续累积", 2, core.openCount());
        assertEquals("SL 恢复后消息数继续累积", 2, core.playerMessageCount());
    }
}
