package com.derekjass.sts.weightedpaths.patches;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;

/**
 * 把「卡」的聊天框挂到地图界面：渲染 + 输入更新。
 */
public class ChatBoxPatch {

    private ChatBoxPatch() {
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void afterRender(DungeonMapScreen __instance, SpriteBatch sb) {
            ChatBoxUi.get().render(sb);
        }
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void afterUpdate(DungeonMapScreen __instance) {
            ChatBoxUi.get().update();
        }
    }
}
