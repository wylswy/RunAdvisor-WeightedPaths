package com.derekjass.sts.weightedpaths.creative;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * 聊天框输入处理器：包装 STS 的 ScrollInputProcessor，在聊天框处于输入模式时接管键盘，
 * 其余事件透传给原处理器。用于让游戏内能直接打字（英文/数字 keyTyped，中文 Ctrl+V 粘贴）。
 */
public final class ChatInputProcessor implements InputProcessor {

    private final InputProcessor original;

    private ChatInputProcessor(InputProcessor original) {
        this.original = original;
    }

    /** 包装当前全局输入处理器，让聊天框能接收键盘。 */
    public static void install() {
        InputProcessor original = Gdx.input.getInputProcessor();
        Gdx.input.setInputProcessor(new ChatInputProcessor(original));
    }

    @Override
    public boolean keyDown(int keycode) {
        if (ChatBoxUi.get().isInputMode()) {
            return ChatBoxUi.get().handleKeyDown(keycode);
        }
        return original != null && original.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return original != null && original.keyUp(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        if (ChatBoxUi.get().isInputMode()) {
            return ChatBoxUi.get().handleKeyTyped(character);
        }
        return original != null && original.keyTyped(character);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return original != null && original.touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return original != null && original.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return original != null && original.touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return original != null && original.mouseMoved(screenX, screenY);
    }

    @Override
    public boolean scrolled(int amount) {
        return original != null && original.scrolled(amount);
    }
}
