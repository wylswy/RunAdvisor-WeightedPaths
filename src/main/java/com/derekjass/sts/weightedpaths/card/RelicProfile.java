package com.derekjass.sts.weightedpaths.card;

/** 遗物对三端口的修正（非流派）。 */
public final class RelicProfile {

    /** 0–3：强化伤害端口选牌（苦无、手里剑、蛇头骨等）。 */
    public final int damagePortBonus;
    /** 0–3：强化运转端口选牌（弃牌系遗物等）。 */
    public final int enginePortBonus;
    public final boolean blockPressureReduced;
    public final boolean enginePressureReduced;
    public final boolean blockPressureIncreased;
    public final boolean enginePressureIncreased;
    public final boolean hasRunicPyramid;

    public RelicProfile(
            int damagePortBonus,
            int enginePortBonus,
            boolean blockPressureReduced,
            boolean enginePressureReduced,
            boolean blockPressureIncreased,
            boolean enginePressureIncreased,
            boolean hasRunicPyramid) {
        this.damagePortBonus = damagePortBonus;
        this.enginePortBonus = enginePortBonus;
        this.blockPressureReduced = blockPressureReduced;
        this.enginePressureReduced = enginePressureReduced;
        this.blockPressureIncreased = blockPressureIncreased;
        this.enginePressureIncreased = enginePressureIncreased;
        this.hasRunicPyramid = hasRunicPyramid;
    }

    public static RelicProfile empty() {
        return new RelicProfile(0, 0, false, false, false, false, false);
    }
}
