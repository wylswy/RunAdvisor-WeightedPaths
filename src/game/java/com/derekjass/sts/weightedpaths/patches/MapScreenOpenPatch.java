package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.card.GlobalRunPlan;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.derekjass.sts.weightedpaths.creative.PactManager;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;

/** 进入地图界面时刷新 live 路线，避免战斗/事件后统计丢失；同时驱动契约的幕切换结算与提案。 */
public class MapScreenOpenPatch {

    private MapScreenOpenPatch() {
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "open")
    public static class OnMapOpen {

        @SpirePostfixPatch
        public static void afterOpen(DungeonMapScreen __instance) {
            if (AbstractDungeon.player != null) {
                WeightedPaths.ensureMapRouteReady();
                // 契约：幕切换 → 结算上一幕 + 提出新幕契约（消息进聊天框）
                for (String msg : PactManager.onMapOpen(AbstractDungeon.actNum,
                        GlobalRunPlan.fromCurrentRun().seedAvailable)) {
                    ChatBoxUi.get().core().addCardMessage(msg);
                }
            }
        }
    }
}