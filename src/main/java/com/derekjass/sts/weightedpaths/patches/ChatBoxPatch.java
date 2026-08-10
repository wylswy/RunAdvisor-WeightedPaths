package com.derekjass.sts.weightedpaths.patches;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;

/**
 * 把「卡」的聊天框挂到地图 + 卡奖界面：渲染 + 输入更新（Tab 呼出/收回）。
 */
public class ChatBoxPatch {

    private ChatBoxPatch() {
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "render")
    public static class MapRenderPatch {
        @SpirePostfixPatch
        public static void afterRender(DungeonMapScreen __instance, SpriteBatch sb) {
            ChatBoxUi.get().render(sb);
        }
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "update")
    public static class MapUpdatePatch {
        @SpirePostfixPatch
        public static void afterUpdate(DungeonMapScreen __instance) {
            ChatBoxUi.get().update();
        }
    }

    @SpirePatch(clz = CardRewardScreen.class, method = "render")
    public static class CardRewardRenderPatch2 {
        @SpirePostfixPatch
        public static void afterRender(CardRewardScreen __instance, SpriteBatch sb) {
            ChatBoxUi.get().render(sb);
        }
    }

    @SpirePatch(clz = CardRewardScreen.class, method = "update")
    public static class CardRewardUpdatePatch2 {
        @SpirePostfixPatch
        public static void afterUpdate(CardRewardScreen __instance) {
            ChatBoxUi.get().update();
        }
    }
}
