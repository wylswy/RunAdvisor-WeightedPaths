package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.card.data.CardStatEntry;
import com.derekjass.sts.weightedpaths.card.data.CardStatsLoader;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.AbstractCard.CardRarity;

import java.util.ArrayList;
import java.util.List;

public final class CardScorer {

    private CardScorer() {
    }

    public static CardRecommendation score(AbstractCard card, List<AbstractCard> deck, GlobalRunPlan plan) {
        ScoredResult result = scoreInternal(card, deck, plan);
        return result.recommendation;
    }

    public static CardRecommendation score(AbstractCard card, List<AbstractCard> deck) {
        return score(card, deck, GlobalRunPlan.fromCurrentRun());
    }

    public static ScoredResult scoreRewardOptionDetailed(
            AbstractCard candidate, List<AbstractCard> deck, GlobalRunPlan plan) {
        if (candidate == null) {
            return new ScoredResult(new CardRecommendation(CardGrade.C, 0.0, ""), null);
        }
        if (plan == null) {
            plan = GlobalRunPlan.fromCurrentRun();
        }
        ArrayList<AbstractCard> withCandidate = new ArrayList<>(deck.size() + 1);
        withCandidate.addAll(deck);
        withCandidate.add(candidate);
        return scoreInternal(candidate, withCandidate, plan);
    }

    public static CardRecommendation scoreRewardOption(
            AbstractCard candidate, List<AbstractCard> deck, GlobalRunPlan plan) {
        return scoreRewardOptionDetailed(candidate, deck, plan).recommendation;
    }

    public static CardRecommendation scoreRewardOption(AbstractCard candidate, List<AbstractCard> deck) {
        return scoreRewardOption(candidate, deck, GlobalRunPlan.fromCurrentRun());
    }

    private static ScoredResult scoreInternal(AbstractCard card, List<AbstractCard> deck, GlobalRunPlan plan) {
        if (card == null) {
            return new ScoredResult(new CardRecommendation(CardGrade.C, 0.0, ""), null);
        }
        if (plan == null) {
            plan = GlobalRunPlan.fromCurrentRun();
        }
        DeckSnapshot snapshot = DeckAnalyzer.analyzeSnapshot(deck);
        RelicProfile relics = RelicAnalyzer.analyze();
        SituationalContext situational = SituationalContext.fromCurrentRun();
        CardStatEntry entry = CardStatsLoader.get(card.cardID);
        double base = entry != null ? entry.baseScore : defaultBaseScore(card.rarity);

        if (relics.enginePressureReduced) {
            snapshot = withEnergySupport(snapshot);
        }

        FourLayerScorer.DetailedResult detailed = FourLayerScorer.evaluateDetailed(
                card, entry, base, snapshot, relics, situational, plan);
        return new ScoredResult(detailed.recommendation, detailed.breakdown);
    }

    public static final class ScoredResult {
        public final CardRecommendation recommendation;
        public final ScoreBreakdown breakdown;

        public ScoredResult(CardRecommendation recommendation, ScoreBreakdown breakdown) {
            this.recommendation = recommendation;
            this.breakdown = breakdown;
        }
    }

    private static DeckSnapshot withEnergySupport(DeckSnapshot snapshot) {
        return new DeckSnapshot(
                snapshot.ports,
                snapshot.directions,
                snapshot.hasAcrobatics,
                snapshot.hasPrepared,
                snapshot.hasMasterfulStab,
                snapshot.hasAdrenaline,
                true,
                snapshot.averageCost,
                snapshot.strikeCount,
                snapshot.defendCount,
                snapshot.acrobaticsCount,
                snapshot.preparedCount);
    }

    private static double defaultBaseScore(CardRarity rarity) {
        if (rarity == null) {
            return 50.0;
        }
        switch (rarity) {
            case RARE:
                return 62.0;
            case UNCOMMON:
                return 58.0;
            case COMMON:
                return 52.0;
            case BASIC:
                return 46.0;
            default:
                return 50.0;
        }
    }
}
