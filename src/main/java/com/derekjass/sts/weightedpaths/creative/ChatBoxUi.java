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
    private boolean visible = true;

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

        // 标题
        BitmapFont header = ModFonts.header != null ? ModFonts.header : FontHelper.cardTitleFont;
        FontHelper.renderFontCentered(sb, header, "卡 · 陪你说话",
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

        // 输入入口提示
        FontHelper.renderFontCentered(sb, font, "▶ 点这里对它说话",
                boxX + boxW / 2.0f, boxY + 16.0f * Settings.scale,
                new Color(1.0f, 0.72f, 0.35f, 1.0f));
    }

    /** 处理输入：点击输入区 → 弹系统文本输入框。 */
    public void update() {
        if (!visible) {
            return;
        }
        layout();
        if (!Gdx.input.justTouched()) {
            return;
        }
        float mx = Gdx.input.getX();
        float my = Settings.HEIGHT - Gdx.input.getY(); // gdx 坐标原点在左下，翻转
        if (mx >= boxX && mx <= boxX + boxW
                && my >= boxY && my <= boxY + 36.0f * Settings.scale) {
            promptInput();
        }
    }

    private void promptInput() {
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override
            public void input(String text) {
                core.onPlayerSend(text);
            }

            @Override
            public void canceled() {
            }
        }, "对卡说话", "", "想跟它说什么？");
    }

    private void layout() {
        boxW = 520.0f * Settings.scale;
        boxH = 380.0f * Settings.scale;
        boxX = Settings.WIDTH - boxW - 30.0f * Settings.scale;
        boxY = Settings.HEIGHT - boxH - 60.0f * Settings.scale;
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
