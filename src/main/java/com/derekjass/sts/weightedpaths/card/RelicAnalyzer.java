package com.derekjass.sts.weightedpaths.card;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.HashSet;
import java.util.Set;

public final class RelicAnalyzer {

    private static final Set<String> DAMAGE_PORT_RELICS = new HashSet<>();
    private static final Set<String> ENGINE_PORT_RELICS = new HashSet<>();
    private static final Set<String> BLOCK_RELIEF = new HashSet<>();
    private static final Set<String> ENGINE_RELIEF = new HashSet<>();
    private static final Set<String> BLOCK_PRESSURE = new HashSet<>();
    private static final Set<String> ENGINE_PRESSURE = new HashSet<>();

    static {
        DAMAGE_PORT_RELICS.add("Snake Skull");
        DAMAGE_PORT_RELICS.add("The Specimen");
        DAMAGE_PORT_RELICS.add("TwistedFunnel");
        DAMAGE_PORT_RELICS.add("Shuriken");
        DAMAGE_PORT_RELICS.add("Kunai");
        DAMAGE_PORT_RELICS.add("Letter Opener");
        DAMAGE_PORT_RELICS.add("Ninja Scroll");

        ENGINE_PORT_RELICS.add("Tough Bandages");
        ENGINE_PORT_RELICS.add("Tingsha");
        ENGINE_PORT_RELICS.add("HoveringKite");
        ENGINE_PORT_RELICS.add("Dead Branch");

        BLOCK_RELIEF.add("Orichalcum");
        BLOCK_RELIEF.add("Anchor");
        BLOCK_RELIEF.add("Calipers");
        BLOCK_RELIEF.add("FossilizedHelix");
        BLOCK_RELIEF.add("Thread and Needle");

        ENGINE_RELIEF.add("Ring of the Snake");
        ENGINE_RELIEF.add("Bag of Preparation");
        ENGINE_RELIEF.add("Gambling Chip");
        ENGINE_RELIEF.add("Pocketwatch");
        ENGINE_RELIEF.add("Sundial");
        ENGINE_RELIEF.add("Snecko Eye");
        ENGINE_RELIEF.add("Runic Pyramid");

        BLOCK_PRESSURE.add("Philosopher's Stone");
        BLOCK_PRESSURE.add("Mark of Pain");
        BLOCK_PRESSURE.add("Coffee Dripper");
        BLOCK_PRESSURE.add("Busted Crown");

        ENGINE_PRESSURE.add("Velvet Choker");
    }

    private RelicAnalyzer() {
    }

    public static RelicProfile analyze() {
        if (AbstractDungeon.player == null || AbstractDungeon.player.relics == null) {
            return RelicProfile.empty();
        }

        int damageBonus = 0;
        int engineBonus = 0;
        boolean blockRelief = false;
        boolean engineRelief = false;
        boolean blockPressure = false;
        boolean enginePressure = false;
        boolean runicPyramid = false;

        for (AbstractRelic relic : AbstractDungeon.player.relics) {
            if (relic == null) {
                continue;
            }
            String id = relic.relicId;
            if (DAMAGE_PORT_RELICS.contains(id)) {
                damageBonus = Math.min(3, damageBonus + 1);
            }
            if (ENGINE_PORT_RELICS.contains(id)) {
                engineBonus = Math.min(3, engineBonus + 1);
            }
            if (BLOCK_RELIEF.contains(id)) {
                blockRelief = true;
            }
            if (ENGINE_RELIEF.contains(id)) {
                engineRelief = true;
            }
            if (BLOCK_PRESSURE.contains(id)) {
                blockPressure = true;
            }
            if (ENGINE_PRESSURE.contains(id)) {
                enginePressure = true;
            }
            if ("Runic Pyramid".equals(id)) {
                runicPyramid = true;
            }
        }

        return new RelicProfile(
                damageBonus,
                engineBonus,
                blockRelief,
                engineRelief,
                blockPressure,
                enginePressure,
                runicPyramid);
    }
}
