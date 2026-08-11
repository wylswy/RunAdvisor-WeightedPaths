package com.derekjass.sts.weightedpaths.creative;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.ChatMessage;
import com.derekjass.sts.weightedpaths.creative.ChatBoxCore.Sender;
import com.derekjass.sts.weightedpaths.ui.ModFonts;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;

import java.util.List;

/**
 * 地图界面的「卡」聊天框 UI。
 *
 * 渲染：地图右上角一个半透明面板，显示与卡的对话（最近若干条）+ 一个「对它说话」入口。
 * 输入：点击输入区 → 弹系统原生文本输入框（支持中文）→ 交给 {@link ChatBoxCore}。
 * 卡主动发消息：由调用方（卡奖检测、事件等）经 {@link #core()} 加入。
 *
 * 本类依赖 gdx/游戏类，无法 JUnit 单测；核心逻辑在 {@link ChatBoxCore}（已测）。
 */
public final class ChatBoxUi {

    private static ChatBoxUi instance;
    private final ChatBoxCore core = new ChatBoxCore();
    /** 默认隐藏，按 Tab 呼出，避免挡地图右上角已有 UI。 */
    private boolean visible = false;
    /** 输入模式：开启时接管键盘，玩家在输入框直接打字。 */
    private boolean inputMode = false;
    private final StringBuilder inputText = new StringBuilder();
    /** 翻页偏移：0=最新一页，>0 表示往历史翻了 N 条。 */
    private int pageOffset = 0;
    /** 上次渲染时的消息总数，用于检测新消息并自动回到最新页。 */
    private int lastTotalSeen = 0;

    private float boxX;
    private float boxY;
    private float boxW;
    private float boxH;

    private static final Texture PANEL_TEX = createPanelTexture();
    private static final int MAX_LINES = 8;

    private ChatBoxUi() {
    }

    public static ChatBoxUi get() {
        if (instance == null) {
            instance = new ChatBoxUi();
            // AI 结果回投游戏主线程再改对话，避免后台线程写 + 渲染线程读同一个 List 的竞态
            instance.core.setDispatcher(Gdx.app::postRunnable);
        }
        return instance;
    }

    public ChatBoxCore core() {
        return core;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean wasVisible = this.visible;
        this.visible = visible;
        // 探针：聊天框从隐藏变显示（呼出）时计数，落盘供「玩家用不用聊天」数据分析
        if (!wasVisible && visible) {
            core.recordOpen();
        }
    }

    /** 在地图界面渲染聊天框。 */
    public void render(SpriteBatch sb) {
        if (!visible) {
            return;
        }
        layout();
        BitmapFont font = ModFonts.body != null ? ModFonts.body : FontHelper.tipBodyFont;
        if (font == null) {
            return;
        }
        // 半透明背景
        sb.setColor(0f, 0f, 0f, 0.72f);
        sb.draw(PANEL_TEX, boxX, boxY, boxW, boxH);
        sb.setColor(Color.WHITE);

        // 标题（输入模式下提示操作）
        BitmapFont header = ModFonts.header != null ? ModFonts.header : FontHelper.cardTitleFont;
        String title = inputMode ? "输入中 · Enter发送 · Esc取消" : "卡 · 陪你说话";
        FontHelper.renderFontCentered(sb, header, title,
                boxX + boxW / 2.0f, boxY + boxH - 22.0f * Settings.scale,
                new Color(0.98f, 0.82f, 0.35f, 1.0f));

        // 对话（翻页窗口，每页最多 MAX_LINES 条）
        List<ChatMessage> msgs = core.messages();
        int total = msgs.size();
        // 有新消息进来 → 自动回到最新页（避免停在历史页看不到新对话）
        if (total > lastTotalSeen) {
            pageOffset = 0;
        }
        lastTotalSeen = total;
        int maxOffset = Math.max(0, total - MAX_LINES);
        pageOffset = Math.max(0, Math.min(pageOffset, maxOffset));
        int end = total - pageOffset;
        int start = Math.max(0, end - MAX_LINES);
        float y = boxY + boxH - 62.0f * Settings.scale;
        for (int i = start; i < end; i++) {
            ChatMessage m = msgs.get(i);
            String prefix = m.sender == Sender.CARD ? "卡： " : "你： ";
            Color c = m.sender == Sender.CARD
                    ? new Color(0.9f, 0.9f, 0.75f, 1.0f)
                    : new Color(0.65f, 0.78f, 1.0f, 1.0f);
            // 长消息（赌约提案/奖励情报）按面板宽度断行，避免横向超出
            java.util.List<String> lines = wrapText(prefix + m.text, font, boxW - 28.0f * Settings.scale);
            for (String line : lines) {
                FontHelper.renderFont(sb, font, line,
                        boxX + 14.0f * Settings.scale, y, c);
                y -= 36.0f * Settings.scale;
                if (y < boxY + 30.0f * Settings.scale) {
                    break;
                }
            }
            if (y < boxY + 30.0f * Settings.scale) {
                break;
            }
        }

        // 翻页条（仅当消息数超过一屏时显示）
        if (maxOffset > 0) {
            float barY = boxY + boxH - 40.0f * Settings.scale;
            String rangeLabel = "第 " + (start + 1) + "~" + end + " 条 / 共 " + total + " 条";
            FontHelper.renderFontCentered(sb, font, rangeLabel,
                    boxX + boxW / 2.0f, barY, new Color(0.8f, 0.8f, 0.8f, 1.0f));
            // 上一页（往历史翻）
            boolean hasPrev = pageOffset < maxOffset;
            FontHelper.renderFontCentered(sb, font, "◀ 上一页",
                    boxX + 50.0f * Settings.scale, barY,
                    hasPrev ? new Color(0.98f, 0.82f, 0.35f, 1.0f) : new Color(0.45f, 0.45f, 0.45f, 1.0f));
            // 下一页（往最新翻）
            boolean hasNext = pageOffset > 0;
            FontHelper.renderFontCentered(sb, font, "下一页 ▶",
                    boxX + boxW - 50.0f * Settings.scale, barY,
                    hasNext ? new Color(0.98f, 0.82f, 0.35f, 1.0f) : new Color(0.45f, 0.45f, 0.45f, 1.0f));
        }

        // 底部：契约待定时显示 接受/拒绝 按钮（不依赖打字）；否则输入入口
        boolean pactOffered = PactManager.engine().current() != null
                && PactManager.engine().current().status == PactEngine.Status.OFFERED;
        if (pactOffered && !inputMode) {
            FontHelper.renderFontCentered(sb, font, "✓ 接受契约",
                    boxX + boxW / 4.0f, boxY + 16.0f * Settings.scale,
                    new Color(0.35f, 1.0f, 0.55f, 1.0f));
            FontHelper.renderFontCentered(sb, font, "✗ 拒绝",
                    boxX + boxW * 3.0f / 4.0f, boxY + 16.0f * Settings.scale,
                    new Color(1.0f, 0.55f, 0.45f, 1.0f));
        } else if (inputMode) {
            String shown = "输入: " + inputText + "|";
            FontHelper.renderFontCentered(sb, font, shown,
                    boxX + boxW / 2.0f, boxY + 16.0f * Settings.scale,
                    new Color(0.35f, 1.0f, 0.55f, 1.0f));
        } else {
            String hint = "✍ 点这里打字（中文 Ctrl+V 粘贴）";
            if (PactManager.engine().current() != null
                    && PactManager.engine().current().status == PactEngine.Status.ACCEPTED) {
                hint = "✍ 点这里打字（契约已接受）";
            } else if (PactManager.isGrudgeActive()) {
                hint = "✍ 点这里打字（卡在记仇，推荐会偏保守）";
            }
            FontHelper.renderFontCentered(sb, font, hint,
                    boxX + boxW / 2.0f, boxY + 16.0f * Settings.scale,
                    new Color(1.0f, 0.72f, 0.35f, 1.0f));
        }
    }

    /** 处理输入：按 Tab 呼出/收回；显示时点底部左半"打字"或右半"粘贴"。 */
    public void update() {
        // 游戏切场景会重置全局输入处理器：每帧确认聊天包装器还在，否则打字会突然失灵
        ChatInputProcessor.ensureInstalled();
        // 输入模式下 Tab 不干扰打字；非输入模式按 Tab 呼出/收回
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            if (!inputMode) {
                setVisible(!visible); // 统一走 setVisible：呼出时记录探针计数
            }
            return;
        }
        if (!visible) {
            return;
        }
        layout();
        if (Gdx.input.justTouched()) {
            float mx = Gdx.input.getX();
            float my = Settings.HEIGHT - Gdx.input.getY();
            // 契约待定时：底部左半接受 / 右半拒绝（按钮路径，不依赖打字）
            if (isInInputArea(mx, my)
                    && PactManager.engine().current() != null
                    && PactManager.engine().current().status == PactEngine.Status.OFFERED) {
                boolean accept = mx < boxX + boxW / 2.0f;
                String reply = PactManager.onChatInput(accept ? "同意" : "拒绝");
                inputMode = false;
                inputText.setLength(0);
                if (!reply.isEmpty()) {
                    core.addCardMessage(reply);
                }
                return;
            }
            if (isInInputArea(mx, my)) {
                inputMode = true; // 点击输入框 → 进入输入模式，直接打字
            } else if (inputMode) {
                inputMode = false; // 点击别处 → 退出输入模式（不发送）
                inputText.setLength(0);
            }
            // 翻页按钮（仅消息超一屏时可用）
            if (handlePageClick(mx, my)) {
                return;
            }
        }
    }

    /** 处理翻页按钮点击：返回 true 表示点到了翻页条。 */
    private boolean handlePageClick(float mx, float my) {
        int total = core.messages().size();
        int maxOffset = Math.max(0, total - MAX_LINES);
        if (maxOffset <= 0) {
            return false;
        }
        float barY = boxY + boxH - 40.0f * Settings.scale;
        float barTop = barY + 16.0f * Settings.scale;
        float barBot = barY - 16.0f * Settings.scale;
        if (my > barTop || my < barBot) {
            return false;
        }
        // 上一页按钮：面板左端 100px 宽
        if (mx >= boxX && mx <= boxX + 100.0f * Settings.scale) {
            if (pageOffset < maxOffset) {
                pageOffset = Math.min(maxOffset, pageOffset + MAX_LINES);
            }
            return true;
        }
        // 下一页按钮：面板右端 100px 宽
        if (mx >= boxX + boxW - 100.0f * Settings.scale && mx <= boxX + boxW) {
            if (pageOffset > 0) {
                pageOffset = Math.max(0, pageOffset - MAX_LINES);
            }
            return true;
        }
        return false;
    }

    private boolean isInInputArea(float mx, float my) {
        return mx >= boxX && mx <= boxX + boxW
                && my >= boxY && my <= boxY + 40.0f * Settings.scale;
    }

    public boolean isInputMode() {
        return inputMode;
    }

    /** 由 ChatInputProcessor 调用：输入字符（英文/数字/中文粘贴合成）。 */
    public boolean handleKeyTyped(char c) {
        if (c == '\b' || c == '\n' || c == '\r') {
            return true;
        }
        if (Character.isDefined(c) && !Character.isISOControl(c)) {
            inputText.append(c);
        }
        return true;
    }

    /** 由 ChatInputProcessor 调用：退格/回车/取消/粘贴。 */
    public boolean handleKeyDown(int keycode) {
        if (keycode == Input.Keys.BACKSPACE) {
            if (inputText.length() > 0) {
                inputText.deleteCharAt(inputText.length() - 1);
            }
        } else if (keycode == Input.Keys.ENTER) {
            submit();
        } else if (keycode == Input.Keys.ESCAPE) {
            cancelInput();
        } else if (keycode == Input.Keys.V
                && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) {
            pasteClipboard();
        }
        return true;
    }

    private void submit() {
        String text = inputText.toString().trim();
        inputMode = false;
        inputText.setLength(0);
        if (!text.isEmpty()) {
            core.onPlayerSend(text);
        }
    }

    private void cancelInput() {
        inputMode = false;
        inputText.setLength(0);
    }

    private void pasteClipboard() {
        String text = readClipboard();
        if (text != null) {
            inputText.append(text);
        }
    }

    private String readClipboard() {
        try {
            Object data = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getContents(null)
                    .getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return data == null ? null : data.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 按可用宽度断行（中文为主，逐字累积；复用 GlyphLayout 减少分配）。 */
    private static java.util.List<String> wrapText(String text, BitmapFont font, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add(text == null ? "" : text);
            return lines;
        }
        GlyphLayout layout = new GlyphLayout();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            layout.setText(font, cur.toString() + ch);
            if (layout.width > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    private void layout() {
        boxW = 560.0f * Settings.scale;
        boxH = 400.0f * Settings.scale;
        // 屏幕左中，避开地图右侧节点/右上角 UI
        boxX = 40.0f * Settings.scale;
        boxY = (Settings.HEIGHT - boxH) / 2.0f;
    }

    private static Texture createPanelTexture() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }
}
