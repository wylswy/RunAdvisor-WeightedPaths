package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.logging.RunAdvisorLogger;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;

/** 对局结束写入 run_summary.json。 */
public class RunLifecyclePatch {

    private RunLifecyclePatch() {
    }

    @SpirePatch(
            clz = DeathScreen.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {MonsterGroup.class})
    public static class OnDeath {

        @SpirePostfixPatch
        public static void after(DeathScreen __instance, MonsterGroup mg) {
            RunAdvisorLogger.onRunEnd(false, "death");
        }
    }

    @SpirePatch(
            clz = VictoryScreen.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {MonsterGroup.class})
    public static class OnVictory {

        @SpirePostfixPatch
        public static void after(VictoryScreen __instance, MonsterGroup mg) {
            RunAdvisorLogger.onRunEnd(true, "victory");
        }
    }
}
