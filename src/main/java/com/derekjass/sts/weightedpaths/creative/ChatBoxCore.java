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

    /**
     * 把一段要在「游戏主线程」执行的代码投递过去（线程调度抽象）。
     * 默认实现直接在当前线程执行（纯逻辑测试友好）；UI 装配层注入 {@code Gdx.app::postRunnable}，
     * 让 AI 后台线程的结果安全回到游戏主线程再改共享状态（避免后台线程写 + 渲染线程读同一个 List）。
     */
    public interface ThreadDispatcher {
        void post(Runnable action);
    }

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
    /** 对话变化时回调（用于落盘持久化，供 SL 恢复）；可注入，默认无。 */
    private Runnable onChange;
    /** 线程调度器：把要在主线程执行的动作投递过去；默认直接在当前线程执行（测试友好）。 */
    private ThreadDispatcher dispatcher = Runnable::run;
    /** 探针：聊天框累计打开次数（跨 SL 累积，回答「玩家到底用不用聊天框」）。 */
    private int openCount = 0;
    /** 探针：玩家累计发送消息数。 */
    private int playerMessageCount = 0;

    /** 设置对话变化回调（如落盘保存）。 */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** 设置线程调度器（UI 层注入 Gdx.app::postRunnable，让 AI 结果回主线程）。默认直跑。 */
    public void setDispatcher(ThreadDispatcher dispatcher) {
        this.dispatcher = dispatcher == null ? Runnable::run : dispatcher;
    }

    /** 探针：聊天框被呼出时调用（UI 层 Tab 打开）。落盘保留，SL 恢复后继续累积。 */
    public void recordOpen() {
        openCount++;
        notifyChange();
    }

    public int openCount() {
        return openCount;
    }

    public int playerMessageCount() {
        return playerMessageCount;
    }

    /** SL 重进同一局时恢复探针计数（继续累积，不清零）。 */
    public void restoreProbeStats(int opens, int playerMessages) {
        openCount = Math.max(0, opens);
        playerMessageCount = Math.max(0, playerMessages);
    }

    private void notifyChange() {
        if (onChange != null) {
            try {
                onChange.run();
            } catch (Exception ignored) {
                // 落盘失败不致命
            }
        }
    }

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

    /**
     * 记忆/探针用：最近最多 {@code max} 条玩家消息（新→旧顺序，跳过卡消息与空白）。
     * 供长期陪伴结算时采集「玩家说过的话」——注意历史是正序存储，必须倒序遍历才是"最近"。
     */
    public List<String> recentPlayerMessages(int max) {
        List<String> out = new ArrayList<>();
        if (max <= 0) {
            return out;
        }
        for (int i = messages.size() - 1; i >= 0 && out.size() < max; i--) {
            ChatMessage m = messages.get(i);
            if (m.sender == Sender.PLAYER && m.text != null && !m.text.trim().isEmpty()) {
                out.add(m.text.trim());
            }
        }
        return out;
    }

    /** 清空对话记录与探针计数（新局开始；SL 恢复用 {@link #restoreProbeStats} 找回计数）。 */
    public void clear() {
        messages.clear();
        openCount = 0;
        playerMessageCount = 0;
    }

    /** 恢复历史对话（SL 重进同一局）。按 sender 逐条加入。 */
    public void restoreMessages(List<ChatMessage> restored) {
        if (restored == null) {
            return;
        }
        for (ChatMessage m : restored) {
            if (m != null && m.text != null && !m.text.trim().isEmpty()) {
                messages.add(new ChatMessage(m.sender, m.text.trim()));
            }
        }
        trimHistory();
    }

    /** 卡主动发消息（态度台词、提醒等）。 */
    public void addCardMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        messages.add(new ChatMessage(Sender.CARD, text.trim()));
        trimHistory();
        notifyChange();
    }

    /** 玩家发送消息：入历史 → 道歉识别 → AI 回复。 */
    public void onPlayerSend(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String trimmed = text.trim();
        messages.add(new ChatMessage(Sender.PLAYER, trimmed));
        trimHistory();
        playerMessageCount++; // 探针：玩家发消息计数（先于落盘，保证本次计入）
        notifyChange();
        if (CardMoodEngine.isApology(trimmed)) {
            CardMoodEngine.recordApology();
        }
        // 契约：聊天框接受/拒绝赌约（未处理时 PactManager 返回空串，不影响正常聊天）
        String pactReply = PactManager.onChatInput(trimmed);
        if (!pactReply.isEmpty()) {
            addCardMessage(pactReply);
        }
        requestAiReply(trimmed);
    }

    /** 构造聊天提示词：身份 + 情境 + 好感度 + 历史 + 玩家消息。 */
    public static String buildChatPrompt(List<ChatMessage> history, String gameContext,
                                         int favor, String playerText) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《杀戮尖塔》(Slay the Spire) 里一张温柔陪伴、会跟玩家聊天、会记得他的卡。");
        sb.append("你的性格：平时温柔体贴、总陪着他、护着他；偶尔调皮地捉弄他一下，从不真凶他。你很倔——他若指出你的错误，你不爱认，会嘴硬地坚持自己（其实是嘴硬心软）。");
        sb.append("这个游戏的世界观里只有卡牌、怪物、遗物、地图与商店，没有神、宗教、神话、虚构地点。");
        sb.append("只谈卡牌、牌组、对局与选择，绝不编造游戏外设定。\n");
        if (gameContext != null && !gameContext.isEmpty()) {
            sb.append("当前情境：").append(gameContext).append("\n");
        }
        sb.append("你对玩家的好感度：").append(favor)
                .append("（负数=记仇/闹脾气，正数=友好。请让语气贴合它）\n");
        String memory = PlayerRelation.get().memoryText();
        if (!memory.isEmpty()) {
            sb.append("你们的长久记忆：").append(memory).append("\n");
        }
        sb.append("你们最近的对话：\n");
        for (ChatMessage m : history) {
            sb.append(m.sender == Sender.CARD ? "卡：" : "玩家：").append(m.text).append("\n");
        }
        sb.append("现在玩家说：").append(playerText).append("\n");
        sb.append("请用一句中文、口语化、符合当前好感度的回复，别超过40字，别用引号，只输出那句话。");
        return sb.toString();
    }

    private void requestAiReply(String playerText) {
        ensureAiChat();
        if (aiChat == null || apiKey.isEmpty()) {
            return; // 未配置 AI：聊天无自动回复（道歉仍会通过好感度反映到台词）
        }
        final String prompt = buildChatPrompt(messages, gameContext, CardMoodEngine.favor(), playerText);
        AiExecutor.submit(() -> {
            try {
                String reply = aiChat.reply(prompt);
                if (reply != null && !reply.trim().isEmpty()) {
                    // 回投主线程再改共享列表，避免后台线程写 + 渲染线程读同一份 ArrayList 的竞态
                    dispatcher.post(() -> addCardMessage(reply.trim()));
                }
            } catch (Exception ignored) {
                // AI 失败：静默（卡暂时不说话）
            }
        });
    }

    /** 懒加载聊天 AI：从环境变量/系统属性读 key（与卡的态度共用）。 */
    private void ensureAiChat() {
        if (aiChat != null) {
            return;
        }
        String key = System.getenv("RUN_ADVISOR_AI_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("runAdvisor.aiKey");
        }
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        final String k = key.trim();
        aiChat = prompt -> DeepSeekClient.generateLine(k, prompt);
        apiKey = k;
    }

    private void trimHistory() {
        while (messages.size() > MAX_HISTORY) {
            messages.remove(0);
        }
    }
}
