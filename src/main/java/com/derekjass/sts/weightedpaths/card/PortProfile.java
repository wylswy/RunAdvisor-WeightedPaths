package com.derekjass.sts.weightedpaths.card;

public final class PortProfile {

    public final int damagePoints;
    public final int blockPoints;
    public final int enginePoints;
    public final int weakPoints;
    public final int zeroCostAttacks;
    public final int deckSize;
    public final boolean hasAoe;
    public final boolean hasScaling;

    public PortProfile(
            int damagePoints,
            int blockPoints,
            int enginePoints,
            int weakPoints,
            int zeroCostAttacks,
            int deckSize,
            boolean hasAoe,
            boolean hasScaling) {
        this.damagePoints = damagePoints;
        this.blockPoints = blockPoints;
        this.enginePoints = enginePoints;
        this.weakPoints = weakPoints;
        this.zeroCostAttacks = zeroCostAttacks;
        this.deckSize = deckSize;
        this.hasAoe = hasAoe;
        this.hasScaling = hasScaling;
    }

    public int portPoints(Port port) {
        switch (port) {
            case DAMAGE:
                return damagePoints;
            case BLOCK:
                return blockPoints;
            case ENGINE:
                return enginePoints;
            default:
                return 0;
        }
    }

    public Port weakestPort() {
        Port weakest = Port.DAMAGE;
        int min = damagePoints;
        if (blockPoints < min) {
            min = blockPoints;
            weakest = Port.BLOCK;
        }
        if (enginePoints < min) {
            weakest = Port.ENGINE;
        }
        return weakest;
    }

    public Port secondWeakestPort() {
        Port weakest = weakestPort();
        Port second = Port.DAMAGE;
        int best = Integer.MAX_VALUE;
        for (Port port : Port.values()) {
            if (port == weakest) {
                continue;
            }
            int pts = portPoints(port);
            if (pts < best) {
                best = pts;
                second = port;
            }
        }
        return second;
    }
}
