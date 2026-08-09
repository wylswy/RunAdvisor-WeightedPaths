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
}
