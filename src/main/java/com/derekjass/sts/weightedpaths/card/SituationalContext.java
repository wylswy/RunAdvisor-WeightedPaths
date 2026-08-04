package com.derekjass.sts.weightedpaths.card;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;

/** 从当前 run 读取战斗/楼层情境。 */
public final class SituationalContext {

    public final int floor;
    public final int actNumber;
    public final int currentHp;
    public final int maxHp;
    public final int neowLamentBattlesLeft;
    public final boolean eliteGreedRecommended;
    /** 第一层前期，猎手血量低、卡组臃肿，应求稳。 */
    public final boolean earlyAct1;

    public SituationalContext(
            int floor,
            int actNumber,
            int currentHp,
            int maxHp,
            int neowLamentBattlesLeft,
            boolean eliteGreedRecommended,
            boolean earlyAct1) {
        this.floor = floor;
        this.actNumber = actNumber;
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.neowLamentBattlesLeft = neowLamentBattlesLeft;
        this.eliteGreedRecommended = eliteGreedRecommended;
        this.earlyAct1 = earlyAct1;
    }

    public static SituationalContext fromCurrentRun() {
        if (AbstractDungeon.player == null) {
            return empty();
        }
        int floor = AbstractDungeon.floorNum;
        int act = AbstractDungeon.actNum;
        int hp = AbstractDungeon.player.currentHealth;
        int maxHp = AbstractDungeon.player.maxHealth;
        int lamentLeft = neowLamentBattlesRemaining();
        boolean eliteGreed = lamentLeft > 0;
        boolean earlyAct1 = act == 1 && floor <= 8;
        return new SituationalContext(floor, act, hp, maxHp, lamentLeft, eliteGreed, earlyAct1);
    }

    public static int neowLamentBattlesRemaining() {
        if (AbstractDungeon.player == null) {
            return 0;
        }
        for (AbstractRelic relic : AbstractDungeon.player.relics) {
            if (relic != null && "NeowsBlessing".equals(relic.relicId) && relic.counter > 0) {
                return relic.counter;
            }
        }
        return 0;
    }

    public static boolean isNeowLamentActive() {
        return neowLamentBattlesRemaining() > 0;
    }

    private static SituationalContext empty() {
        return new SituationalContext(0, 0, 0, 0, 0, false, false);
    }
}
