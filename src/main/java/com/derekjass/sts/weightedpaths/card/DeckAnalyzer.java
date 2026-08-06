package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.card.data.CardStatEntry;
import com.derekjass.sts.weightedpaths.card.data.CardStatsLoader;
import com.megacrit.cardcrawl.cards.AbstractCard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DeckAnalyzer {

    private static final Set<String> ENERGY_CARDS = new HashSet<>();

    static {
        ENERGY_CARDS.add("Adrenaline");
        ENERGY_CARDS.add("Concentrate");
        ENERGY_CARDS.add("Tactician");
    }

    private DeckAnalyzer() {
    }

    public static DeckSnapshot analyzeSnapshot(List<AbstractCard> deck) {
        int damage = 0;
        int block = 0;
        int engine = 0;
        int weak = 0;
        int zeroCostAttacks = 0;
        boolean hasAoe = false;
        boolean hasScaling = false;
        int deckSize = 0;
        int costSum = 0;
        // 方向统计（组件一致性）
        int attackCount = 0;
        int dotCount = 0;
        int drawCount = 0;
        int blockCount = 0;

        boolean hasAcrobatics = false;
        boolean hasPrepared = false;
        boolean hasMasterfulStab = false;
        boolean hasAdrenaline = false;
        int acrobaticsCount = 0;
        int preparedCount = 0;
        int strikeCount = 0;
        int defendCount = 0;

        for (AbstractCard card : deck) {
            if (card == null) {
                continue;
            }
            deckSize++;
            costSum += Math.max(0, card.costForTurn);

            String id = card.cardID;
            if ("Strike_G".equals(id)) {
                strikeCount++;
            }
            if ("Defend_G".equals(id)) {
                defendCount++;
            }
            if ("Acrobatics".equals(id)) {
                hasAcrobatics = true;
                acrobaticsCount++;
            }
            if ("Prepared".equals(id)) {
                hasPrepared = true;
                preparedCount++;
            }
            if ("Masterful Stab".equals(id)) {
                hasMasterfulStab = true;
            }
            if ("Adrenaline".equals(id)) {
                hasAdrenaline = true;
            }

            CardStatEntry entry = CardStatsLoader.get(id);
            if (entry == null) {
                continue;
            }
            if (entry.servesPort(Port.DAMAGE)) {
                damage++;
            }
            if (entry.servesPort(Port.BLOCK)) {
                block++;
            }
            if (entry.servesPort(Port.ENGINE)) {
                engine++;
            }
            // 方向统计（组件一致性）
            if (entry.hasTag("attack")) {
                attackCount++;
            }
            if (entry.hasTag("dot")) {
                dotCount++;
            }
            if (entry.hasTag("draw") || entry.hasTag("discard")) {
                drawCount++;
            }
            if (entry.hasTag("block")) {
                blockCount++;
            }
            if (entry.hasTag("weak")) {
                weak++;
            }
            if (entry.hasTag("zeroCost") && entry.hasTag("attack")) {
                zeroCostAttacks++;
            }
            if (entry.hasTag("aoe")) {
                hasAoe = true;
            }
            if (entry.hasTag("scaling")) {
                hasScaling = true;
            }
        }

        PortProfile ports = new PortProfile(
                damage, block, engine, weak, zeroCostAttacks, deckSize, hasAoe, hasScaling);
        DirectionProfile directions =
                new DirectionProfile(attackCount, dotCount, drawCount, blockCount);
        double averageCost = deckSize > 0 ? (double) costSum / deckSize : 1.0;
        boolean hasEnergySupport = hasAdrenaline || ENERGY_CARDS.stream()
                .anyMatch(id -> containsCardId(deck, id));
        return new DeckSnapshot(
                ports,
                directions,
                hasAcrobatics,
                hasPrepared,
                hasMasterfulStab,
                hasAdrenaline,
                hasEnergySupport,
                averageCost,
                strikeCount,
                defendCount,
                acrobaticsCount,
                preparedCount);
    }

    private static boolean containsCardId(List<AbstractCard> deck, String cardId) {
        for (AbstractCard card : deck) {
            if (card != null && cardId.equals(card.cardID)) {
                return true;
            }
        }
        return false;
    }

    public static PortProfile analyze(List<AbstractCard> deck) {
        return analyzeSnapshot(deck).ports;
    }
}
