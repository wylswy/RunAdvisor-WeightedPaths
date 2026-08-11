package com.derekjass.sts.weightedpaths.seed;

import basemod.BaseMod;
import basemod.interfaces.PreStartGameSubscriber;
import basemod.interfaces.StartGameSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SeedDecodeHook implements StartGameSubscriber, PreStartGameSubscriber {

    private static final Logger logger = LogManager.getLogger(SeedDecodeHook.class.getName());

    private SeedDecodeHook() {
        BaseMod.subscribe(this);
    }

    public static void initialize() {
        new SeedDecodeHook();
    }

    @Override
    public void receivePreStartGame() {
        SeedOracle.clear();
        logger.info("SeedOracle cache cleared for new run.");
    }

    @Override
    public void receiveStartGame() {
        if (Settings.seed == null) {
            logger.warn("Settings.seed is null at start game; skipping SeedOracle decode.");
            return;
        }
        long seed = Settings.seed.longValue();
        SeedOracle.decode(seed);
    }

    @SpirePatch(clz = AbstractDungeon.class, method = "dungeonTransitionSetup")
    public static class ActTransitionPatch {

        @SpirePostfixPatch
        public static void afterActTransition() {
            logger.info("Act transition complete; current actNum={}.", AbstractDungeon.actNum);
        }
    }
}
