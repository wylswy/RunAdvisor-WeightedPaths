package com.derekjass.sts.weightedpaths.card;

/** 卡组快照：三端口统计 + 费用/洁牌 + 一层精英就绪。 */
public final class DeckSnapshot {

    public final PortProfile ports;
    public final DirectionProfile directions;
    public final boolean hasAcrobatics;
    public final boolean hasPrepared;
    public final boolean hasMasterfulStab;
    public final boolean hasAdrenaline;
    public final boolean hasEnergySupport;
    public final double averageCost;
    public final int strikeCount;
    public final int defendCount;
    public final int acrobaticsCount;
    public final int preparedCount;

    public DeckSnapshot(
            PortProfile ports,
            DirectionProfile directions,
            boolean hasAcrobatics,
            boolean hasPrepared,
            boolean hasMasterfulStab,
            boolean hasAdrenaline,
            boolean hasEnergySupport,
            double averageCost,
            int strikeCount,
            int defendCount,
            int acrobaticsCount,
            int preparedCount) {
        this.ports = ports;
        this.directions = directions;
        this.hasAcrobatics = hasAcrobatics;
        this.hasPrepared = hasPrepared;
        this.hasMasterfulStab = hasMasterfulStab;
        this.hasAdrenaline = hasAdrenaline;
        this.hasEnergySupport = hasEnergySupport;
        this.averageCost = averageCost;
        this.strikeCount = strikeCount;
        this.defendCount = defendCount;
        this.acrobaticsCount = acrobaticsCount;
        this.preparedCount = preparedCount;
    }

    public int removalUrgency() {
        int urgency = strikeCount * 2;
        if (ports.deckSize >= 16) {
            urgency += 2;
        }
        if (ports.deckSize >= 20) {
            urgency += 3;
        }
        if (defendCount >= 4 && ports.blockPoints >= 3) {
            urgency += 2;
        }
        return urgency;
    }

    public boolean needsShopRemoval() {
        return strikeCount >= 2 || removalUrgency() >= 5;
    }

    /** 内切 + 杂技 + 精巧 → 一层可进精英（端口：weak + ENGINE + DAMAGE）。 */
    public boolean act1SanLouMaoEliteCombo() {
        return ports.weakPoints >= 1 && hasAcrobatics && hasMasterfulStab && ports.deckSize >= 8;
    }

    /** 一层精英就绪：BLOCK + DAMAGE + weak 达标，或上述三卡组合。 */
    public boolean act1EliteReady() {
        if (act1SanLouMaoEliteCombo()) {
            return true;
        }
        boolean survival = (ports.blockPoints >= 2 && ports.weakPoints >= 1) || ports.blockPoints >= 3;
        boolean damage = ports.damagePoints >= 2;
        boolean hasFrontline = ports.blockPoints >= 1 || ports.weakPoints >= 1;
        return ports.deckSize >= 8 && survival && damage && hasFrontline;
    }
}
