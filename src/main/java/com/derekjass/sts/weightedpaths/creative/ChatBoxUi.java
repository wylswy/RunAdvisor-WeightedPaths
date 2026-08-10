package com.derekjass.sts.weightedpaths.creative;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
        this.visible = visible;
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

        // 对话（最近 MAX_LINES 条）
        List<ChatMessage> msgs = core.messages();
        int start = Math.max(0, msgs.size() - MAX_LINES);
        float y = boxY + boxH - 62.0f * Settings.scale;
        for (int i = start; i < msgs.size(); i++) {
            ChatMessage m = msgs.get(i);
            String prefix = m.sender == Sender.CARD ? "卡： " : "你： ";
            Color c = m.sender == Sender.CARD
                    ? new Color(0.9f, 0.9f, 0.75f, 1.0f)
                    : new Color(0.65f, 0.78f, 1.0f, 1.0f);
            FontHelper.renderFont(sb, font, prefix + m.text,
                    boxX + 14.0f * Settings.scale, y, c);
            y -= 36.0f * Settings.scale;
            if (y < boxY + 30.0f * Settings.scale) {
                break;
            }
        }

        // 输入入口：输入模式显示输入文本，否则显示提示
        if (inputMode) {
            String shown = "输入: " + inputText + "|";
            FontHelper.renderFontCentered(sb, font, shown,
                    boxX + boxW / 2.0f, boxY + 16.0f * Settings.scale,
                    new Color(0.35f, 1.0f, 0.55f, 1.0f));
        } else {
            FontHelper.renderFontCentered(sb, font, "✍ 点这里打字（中文 Ctrl+V 粘贴）",
                    boxX + boxW / 2.0f, boxY + 16.0f * Settings.scale,
                    new Color(1.0f, 0.72f, 0.35f, 1.0f));
        }
    }

    /** 处理输入：按 Tab 呼出/收回；显示时点底部左半"打字"或右半"粘贴"。 */
    public void update() {
        // 输入模式下 Tab 不干扰打字；非输入模式按 Tab 呼出/收回
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            if (!inputMode) {
                visible = !visible;
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
            if (isInInputArea(mx, my)) {
                inputMode = true; // 点击输入框 → 进入输入模式，直接打字
            } else if (inputMode) {
                inputMode = false; // 点击别处 → 退出输入模式（不发送）
                inputText.setLength(0);
            }
        }
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
