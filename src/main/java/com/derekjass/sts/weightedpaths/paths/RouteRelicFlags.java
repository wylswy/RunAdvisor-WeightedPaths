package com.derekjass.sts.weightedpaths.paths;

import com.derekjass.sts.weightedpaths.helpers.RelicTracker;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;

/**
 * 影响路线取舍的遗物标记（读当前玩家，模拟过程中不变）。
 */
public final class RouteRelicFlags {

    public final boolean coffeeDripper;
    public final boolean fusionHammer;
    public final boolean smilingMask;
    public final boolean mealTicket;
    public final boolean blackStar;
    public final boolean preservedInsect;
    public final boolean ectoPlasm;

    public RouteRelicFlags(
            boolean coffeeDripper,
            boolean fusionHammer,
            boolean smilingMask,
            boolean mealTicket,
            boolean blackStar,
            boolean preservedInsect,
            boolean ectoPlasm) {
        this.coffeeDripper = coffeeDripper;
        this.fusionHammer = fusionHammer;
        this.smilingMask = smilingMask;
        this.mealTicket = mealTicket;
        this.blackStar = blackStar;
        this.preservedInsect = preservedInsect;
        this.ectoPlasm = ectoPlasm;
    }

    public static RouteRelicFlags fromPlayer() {
        if (AbstractDungeon.player == null || AbstractDungeon.player.relics == null) {
            return none();
        }
        boolean coffeeDripper = false;
        boolean fusionHammer = false;
        boolean smilingMask = false;
        boolean mealTicket = false;
        boolean blackStar = false;
        boolean preservedInsect = false;
        for (AbstractRelic relic : AbstractDungeon.player.relics) {
            if (relic == null) {
                continue;
            }
            switch (relic.relicId) {
                case "Coffee Dripper":
                    coffeeDripper = true;
                    break;
                case "Fusion Hammer":
                    fusionHammer = true;
                    break;
                case "Smiling Mask":
                    smilingMask = true;
                    break;
                case "MealTicket":
                    mealTicket = true;
                    break;
                case "Black Star":
                    blackStar = true;
                    break;
                case "Preserved Insect":
                    preservedInsect = true;
                    break;
                default:
                    break;
            }
        }
        return new RouteRelicFlags(
                coffeeDripper,
                fusionHammer,
                smilingMask,
                mealTicket,
                blackStar,
                preservedInsect,
                RelicTracker.hasEcto);
    }

    public static RouteRelicFlags none() {
        return new RouteRelicFlags(false, false, false, false, false, false, false);
    }
}
