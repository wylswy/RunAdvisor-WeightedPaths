package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;

/** 进入地图界面时刷新 live 路线，避免战斗/事件后统计丢失。 */
public class MapScreenOpenPatch {

    private MapScreenOpenPatch() {
    }

    @SpirePatch(clz = DungeonMapScreen.class, method = "open")
    public static class OnMapOpen {

        @SpirePostfixPatch
        public static void afterOpen(DungeonMapScreen __instance) {
            if (AbstractDungeon.player != null) {
                WeightedPaths.ensureMapRouteReady();
            }
        }
    }
}
