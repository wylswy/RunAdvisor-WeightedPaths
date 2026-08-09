package com.derekjass.sts.weightedpaths.creative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「卡」的聊天框核心 —— 管理玩家与卡的双向对话，并驱动好感度与 AI 回复。
 *
 * 职责（纯逻辑，可单测；UI 渲染与输入由 patches/ui 层负责）：
 *  - 维护对话历史（限长）
 *  - 卡主动发消息（态度台词、提醒等）
 *  - 玩家发消息：识别道歉 → 好感度回升；触发 AI 多轮回复（异步）
 *  - 构造带「身份 + 情境 + 好感度 + 历史」的聊天提示词
 */
public final class ChatBoxCore {

    public enum Sender { CARD, PLAYER }

    public static final class ChatMessage {
        public final Sender sender;
        public final String text;

        public ChatMessage(Sender sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }

    private static final int MAX_HISTORY = 24;

    private final List<ChatMessage> messages = new ArrayList<>();
    private AiChat aiChat;
    private String apiKey = "";
    private String gameContext = "";

    /** 测试注入 mock AI。 */
    public void setAiChatForTest(AiChat aiChat, String apiKey) {
        this.aiChat = aiChat;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    /** 设置当前游戏情境（如「当前第2层」）。 */
    public void setGameContext(String gameContext) {
        this.gameContext = gameContext == null ? "" : gameContext;
    }

    public List<ChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    /** 卡主动发消息（态度台词、提醒等）。 */
    public void addCardMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        messages.add(new ChatMessage(Sender.CARD, text.trim()));
        trimHistory();
    }

    /** 玩家发送消息：入历史 → 道歉识别 → AI 回复。 */
    public void onPlayerSend(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String trimmed = text.trim();
        messages.add(new ChatMessage(Sender.PLAYER, trimmed));
        trimHistory();
        if (CardMoodEngine.isApology(trimmed)) {
            CardMoodEngine.recordApology();
        }
        requestAiReply(trimmed);
    }

    /** 构造聊天提示词：身份 + 情境 + 好感度 + 历史 + 玩家消息。 */
    public static String buildChatPrompt(List<ChatMessage> history, String gameContext,
                                         int favor, String playerText) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《杀戮尖塔》(Slay the Spire) 里一张有性格、会跟玩家聊天、会记得他的卡。");
        sb.append("这个游戏的世界观里只有卡牌、怪物、遗物、地图与商店，没有神、宗教、神话、虚构地点。");
        sb.append("只谈卡牌、牌组、对局与选择，绝不编造游戏外设定。\n");
        if (gameContext != null && !gameContext.isEmpty()) {
            sb.append("当前情境：").append(gameContext).append("\n");
        }
        sb.append("你对玩家的好感度：").append(favor)
                .append("（负数=记仇/闹脾气，正数=友好。请让语气贴合它）\n");
        sb.append("你们最近的对话：\n");
        for (ChatMessage m : history) {
            sb.append(m.sender == Sender.CARD ? "卡：" : "玩家：").append(m.text).append("\n");
        }
        sb.append("现在玩家说：").append(playerText).append("\n");
        sb.append("请用一句中文、口语化、符合当前好感度的回复，别超过40字，别用引号，只输出那句话。");
        return sb.toString();
    }

    private void requestAiReply(String playerText) {
        if (aiChat == null || apiKey.isEmpty()) {
            return; // 未配置 AI：聊天无自动回复（道歉仍会通过好感度反映到台词）
        }
        final String prompt = buildChatPrompt(messages, gameContext, CardMoodEngine.favor(), playerText);
        new Thread(() -> {
            try {
                String reply = aiChat.reply(prompt);
                if (reply != null && !reply.trim().isEmpty()) {
                    addCardMessage(reply.trim());
                }
            } catch (Exception ignored) {
                // AI 失败：静默（卡暂时不说话）
            }
        }).start();
    }

    private void trimHistory() {
        while (messages.size() > MAX_HISTORY) {
            messages.remove(0);
        }
    }
}
