package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.card.data.CardStatEntry;
import com.megacrit.cardcrawl.cards.AbstractCard;

/**
 * 四层筛选：纯三端口（DAMAGE / BLOCK / ENGINE），无流派包逻辑。
 */
public final class FourLayerScorer {

    public static final double SKIP_THRESHOLD = 45.0;
    /** 补偿历史 ×0.70 压分；最终分 = raw × 此系数（勿低于 1.0）。 */
    private static final double SCORE_CALIBRATION = 1.40;
    private static final int SILENT_ENGINE_BIAS = 1;

    private FourLayerScorer() {
    }

    public static CardRecommendation evaluate(
            AbstractCard card,
            CardStatEntry entry,
            double baseScore,
            DeckSnapshot deck,
            RelicProfile relics,
            SituationalContext situational,
            GlobalRunPlan plan) {
        return evaluateDetailed(card, entry, baseScore, deck, relics, situational, plan).recommendation;
    }

    public static DetailedResult evaluateDetailed(
            AbstractCard card,
            CardStatEntry entry,
            double baseScore,
            DeckSnapshot deck,
            RelicProfile relics,
            SituationalContext situational,
            GlobalRunPlan plan) {
        if (card == null) {
            return new DetailedResult(new CardRecommendation(CardGrade.C, 0.0, ""), null);
        }

        LayerResult survival = layer1Survival(card, entry, deck, situational, plan);
        double portMult = layer2Port(entry, deck, relics, plan);
        double synergyMult = layer3PortSynergy(card, entry, deck, relics, plan);
        double pollutionMult = layer4Pollution(card, entry, deck, relics);

        double combinedMult = portMult * synergyMult * pollutionMult;
        // 惩罚连乘下限：避免 premium/transition 被乘穿到 C 档
        double multFloor = 0.72;
        if (entry.hasTag("premiumTransition") || entry.hasTag("transition") || entry.hasTag("act1Engine")) {
            multFloor = 0.82;
        }
        combinedMult = Math.max(combinedMult, multFloor);

        double raw = (baseScore + survival.bonus) * combinedMult;
        double score = raw * SCORE_CALIBRATION;
        score = Math.max(0.0, Math.min(100.0, score));

        ScoreBreakdown breakdown = new ScoreBreakdown(
                baseScore, survival.bonus, portMult, synergyMult, pollutionMult, SCORE_CALIBRATION, score);
        return new DetailedResult(new CardRecommendation(CardGrade.fromScore(score), score, ""), breakdown);
    }

    public static final class DetailedResult {
        public final CardRecommendation recommendation;
        public final ScoreBreakdown breakdown;

        public DetailedResult(CardRecommendation recommendation, ScoreBreakdown breakdown) {
            this.recommendation = recommendation;
            this.breakdown = breakdown;
        }
    }

    private static LayerResult layer1Survival(
            AbstractCard card,
            CardStatEntry entry,
            DeckSnapshot deck,
            SituationalContext situational,
            GlobalRunPlan plan) {
        if (entry == null || situational == null) {
            return LayerResult.none();
        }
        double bonus = 0.0;
        String tag = "";

        double hpRatio = situational.maxHp > 0
                ? (double) situational.currentHp / situational.maxHp
                : 1.0;

        boolean elitePressure = plan != null
                && (plan.nextRoomIsElite() || plan.eliteWithin(2)
                || plan.remainingRunWide.eliteCount >= 3);
        boolean survivalCrisis = hpRatio < 0.30 && elitePressure;
        boolean hpLow = hpRatio < 0.40 && (plan != null && plan.nextRoomIsElite());

        boolean needAoe = !deck.ports.hasAoe && plan != null
                && (plan.nextRoomIsElite()
                || countSymbol(plan.upcomingThisAct, "M") >= 3
                || plan.remainingRunWide.monsterCount >= 8);

        if (survivalCrisis) {
            if (entry.hasTag("premiumTransition") || entry.hasTag("transition")
                    || entry.servesPort(Port.BLOCK) || entry.hasTag("weak")) {
                bonus += 22.0;
                tag = "survival";
            }
        } else if (hpLow && (entry.servesPort(Port.BLOCK) || entry.hasTag("weak"))) {
            bonus += 15.0;
            tag = "survival";
        }

        if (plan != null && plan.nextRoomIsElite()
                && (entry.hasTag("premiumTransition") || entry.hasTag("transition")
                || entry.servesPort(Port.BLOCK))) {
            bonus += 14.0;
            tag = "nextElite";
        }

        if (needAoe && entry.hasTag("aoe")) {
            bonus += 24.0;
            tag = "aoe";
        }

        if (situational.neowLamentBattlesLeft > 0) {
            Port weakest = deck.ports.weakestPort();
            if (entry.servesPort(weakest) || entry.hasTag("scaling")) {
                bonus += 10.0;
            }
        }

        if (situational.actNumber == 1) {
            if (entry.hasTag("premiumTransition")) {
                bonus += situational.earlyAct1 ? 24.0 : 18.0;
                if (entry.servesPort(Port.BLOCK)) {
                    bonus += 6.0;
                }
                if (tag.isEmpty()) {
                    tag = "act1Premium";
                }
            } else if (entry.hasTag("transition")) {
                bonus += situational.earlyAct1 ? 10.0 : 8.0;
                if (tag.isEmpty()) {
                    tag = "act1ModestTransition";
                }
            }
            // 内切线：weak 过渡（Neutralize 已在牌组，同线牌如腿扫/尖啸/精巧）
            if (entry.hasTag("weak")
                    && (entry.hasTag("premiumTransition") || entry.hasTag("transition"))) {
                bonus += 8.0;
            }
            // 杂技/准备：3楼猫 内切+杂技+精巧，一层可抓，不是「战未来不抓」
            if (entry.hasTag("act1Engine")) {
                if (deck.ports.weakPoints >= 1 || deck.ports.enginePoints < 2) {
                    bonus += 14.0;
                    if (tag.isEmpty()) {
                        tag = "act1Engine";
                    }
                }
            }
            // 仅裸 payoff 战未来降权：战术/本能/设局（内切+杂技不在此列）
            if (isBareAct1Future(card, entry, deck)) {
                bonus -= 12.0;
            }
            if (entry.hasTag("scaling") && !entry.hasTag("premiumTransition")
                    && !entry.hasTag("transition") && !entry.hasTag("terminal")) {
                bonus -= 6.0;
            }
        } else if (entry.hasTag("premiumTransition") || entry.hasTag("transition")) {
            bonus += 8.0;
            if (tag.isEmpty()) {
                tag = "transition";
            }
        } else if (situational.earlyAct1 && entry.hasTag("weak")) {
            bonus += 6.0;
        }

        if (entry.hasTag("future") && plan != null && !plan.nextRoomIsElite()
                && !plan.eliteWithin(1)) {
            bonus += 8.0;
        }
        if (entry.hasTag("future") && plan != null
                && (plan.nextRoomIsElite() || plan.eliteWithin(1))) {
            bonus -= 10.0;
        }

        return new LayerResult(bonus, tag);
    }

    private static double layer2Port(
            CardStatEntry entry,
            DeckSnapshot deck,
            RelicProfile relics,
            GlobalRunPlan plan) {
        if (entry == null) {
            return 1.0;
        }
        double mult = 1.0;
        Port weakest = effectiveWeakestPort(deck.ports, relics, plan);

        if (entry.servesPort(weakest)) {
            mult = weakest == Port.ENGINE ? 1.60 : 1.35;
        }

        if (deck.ports.portPoints(Port.ENGINE) < 2 && entry.servesPort(Port.ENGINE)) {
            mult = Math.max(mult, 1.60);
        }
        if (deck.ports.portPoints(Port.BLOCK) < 3 && entry.servesPort(Port.BLOCK)) {
            mult = Math.max(mult, 1.35);
        }
        if (deck.ports.portPoints(Port.DAMAGE) < 3 && entry.servesPort(Port.DAMAGE)) {
            mult = Math.max(mult, 1.35);
        }

        if (plan != null && plan.remainingRunWide.restCount <= 2
                && plan.remainingRunWide.eliteCount >= 3
                && entry.servesPort(Port.BLOCK)) {
            mult = Math.max(mult, 1.35);
        }

        if (plan != null && plan.phase == GlobalRunPlan.RunPhase.LATE
                && plan.remainingRunWide.eliteCount >= 4
                && entry.hasTag("scaling")) {
            mult = Math.max(mult, 1.22);
        }

        if (relics.enginePressureIncreased && entry.servesPort(Port.ENGINE)) {
            mult *= 0.75;
        }
        return mult;
    }

    private static double layer3PortSynergy(
            AbstractCard card,
            CardStatEntry entry,
            DeckSnapshot deck,
            RelicProfile relics,
            GlobalRunPlan plan) {
        if (entry == null) {
            return 1.0;
        }
        double mult = 1.0;
        String cardId = card.cardID;

        Port required = entry.requiredPort();
        if (required != null && entry.requiresMinPoints > 0
                && deck.ports.portPoints(required) < entry.requiresMinPoints) {
            mult = Math.min(mult, 0.78);
        }

        if (entry.hasTag("scaling") && deck.ports.hasScaling && entry.servesPort(Port.DAMAGE)) {
            mult = Math.max(mult, 1.15);
        }

        if (entry.hasTag("dot") && entry.hasTag("scaling") && deck.ports.damagePoints == 0) {
            mult = Math.max(mult, 1.10);
        }

        if ("Acrobatics".equals(cardId) && deck.acrobaticsCount >= 2) {
            mult *= 0.58;
        }
        if ("Prepared".equals(cardId) && deck.preparedCount >= 2) {
            mult *= 0.65;
        }
        // 刀刃之舞：优质 DAMAGE 过渡，可裸抓；不因已有 zeroCost 攻击而降分
        if ("Blade Dance".equals(cardId) && plan != null
                && plan.phase == GlobalRunPlan.RunPhase.EARLY
                && deck.ports.damagePoints < 3) {
            mult = Math.max(mult, 1.18);
        }

        if ("Masterful Stab".equals(cardId) && deck.hasAcrobatics && deck.ports.weakPoints >= 1) {
            mult = Math.max(mult, 1.32);
        }

        if (entry.hasTag("terminal")) {
            if ("Wraith Form v2".equals(cardId)) {
                if (plan != null && plan.phase == GlobalRunPlan.RunPhase.EARLY) {
                    mult *= 0.52;
                } else if (deck.ports.blockPoints >= 3) {
                    mult = Math.max(mult, 1.22);
                }
            } else if (plan != null && plan.phase == GlobalRunPlan.RunPhase.LATE) {
                mult = Math.max(mult, 1.10);
            }
        }

        if (entry.hasTag("future") && plan != null) {
            if (isBareAct1Future(card, entry, deck) && plan.actNumber == 1) {
                mult = Math.min(mult, 0.88);
            }
            if (!plan.nextRoomIsElite() && plan.roomsUntilElite > 2) {
                mult = Math.max(mult, 1.12);
            }
            if (plan.nextRoomIsElite() || plan.eliteWithin(1)) {
                mult = Math.min(mult, 0.88);
            }
        }

        if (relics.damagePortBonus > 0 && entry.servesPort(Port.DAMAGE)) {
            mult = Math.max(mult, 1.0 + relics.damagePortBonus * 0.08);
        }
        if (relics.enginePortBonus > 0 && entry.servesPort(Port.ENGINE)) {
            mult = Math.max(mult, 1.0 + relics.enginePortBonus * 0.08);
        }

        if (plan != null && plan.remainingRunWide.monsterCount >= 10 && entry.hasTag("aoe")) {
            mult = Math.max(mult, 1.22);
        }

        // 组件一致性（白夕）：识别主线方向，同向卡加分，强化主线；
        // BLOCK 是生存硬底线——方向强化绝不作用于 BLOCK 卡（缺 BLOCK 时保命优先）。
        String dominant = deck.directions.dominantDirection();
        if (dominant != null && matchesDirection(entry, dominant) && !entry.hasTag("block")) {
            mult = Math.max(mult, 1.15);
        }

        return mult;
    }

    /** 候选卡是否属于指定方向（组件一致性主线）。 */
    private static boolean matchesDirection(CardStatEntry entry, String direction) {
        if (entry == null || direction == null) {
            return false;
        }
        switch (direction) {
            case "attack":
                return entry.hasTag("attack");
            case "dot":
                return entry.hasTag("dot");
            case "draw":
                return entry.hasTag("draw") || entry.hasTag("discard");
            case "block":
                return entry.hasTag("block");
            default:
                return false;
        }
    }

    private static double layer4Pollution(
            AbstractCard card,
            CardStatEntry entry,
            DeckSnapshot deck,
            RelicProfile relics) {
        if (entry == null) {
            return 1.0;
        }

        double mult = 1.0;
        int cost = Math.max(0, card.costForTurn);
        boolean energyOk = deck.hasEnergySupport || relics.enginePressureReduced;

        if (cost >= 2 && deck.averageCost > 1.5 && !energyOk && !entry.hasTag("energy")) {
            mult *= 0.72;
        }
        if (deck.ports.deckSize >= 18 && !entry.hasTag("scaling") && !entry.hasTag("transition")) {
            mult *= 0.82;
        }
        if (deck.ports.deckSize >= 18 && entry.hasTag("engine") && !entry.hasTag("scaling")) {
            mult *= 0.88;
        }
        int removalUrgency = deck.removalUrgency();
        if (removalUrgency >= 5 && !entry.hasTag("scaling") && !entry.servesPort(Port.ENGINE)) {
            mult *= 0.82;
        }
        if (removalUrgency >= 7) {
            mult *= 0.75;
        }
        if (deck.strikeCount >= 3 && !entry.hasTag("scaling")) {
            mult *= 0.85;
        }
        if (entry.hasTag("pollution")) {
            mult *= 0.55;
        }
        if (entry.hasTag("lowValueAttack")) {
            mult *= 0.55;
        }
        return mult;
    }

    /** 一层裸抓的战未来：无即时价值、且无引擎前置（不含杂技/准备/后空翻）。 */
    private static boolean isBareAct1Future(AbstractCard card, CardStatEntry entry, DeckSnapshot deck) {
        if (entry == null || card == null) {
            return false;
        }
        if (!entry.hasTag("future")) {
            return false;
        }
        if (entry.hasTag("act1Engine") || entry.hasTag("premiumTransition")) {
            return false;
        }
        String id = card.cardID;
        if ("Reflex".equals(id) || "Setup".equals(id)) {
            return true;
        }
        if ("Tactician".equals(id)) {
            return deck.ports.enginePoints < 2;
        }
        if ("Calculated Gamble".equals(id)) {
            return deck.ports.enginePoints < 2 && deck.ports.blockPoints < 2;
        }
        return false;
    }

    private static int countSymbol(java.util.List<String> symbols, String target) {
        int count = 0;
        for (String symbol : symbols) {
            if (target.equals(symbol)) {
                count++;
            }
        }
        return count;
    }

    private static Port effectiveWeakestPort(PortProfile deck, RelicProfile relics, GlobalRunPlan plan) {
        int damage = deck.damagePoints + relics.damagePortBonus;
        int block = deck.blockPoints;
        int engine = deck.enginePoints - SILENT_ENGINE_BIAS + relics.enginePortBonus;

        if (relics.blockPressureReduced) {
            block += 2;
        }
        if (relics.blockPressureIncreased) {
            block -= 2;
        }
        if (relics.enginePressureReduced) {
            engine += 2;
        }
        if (relics.enginePressureIncreased) {
            engine -= 3;
        }

        if (plan != null) {
            if (plan.phase == GlobalRunPlan.RunPhase.EARLY) {
                engine -= 1;
            }
            if (plan.remainingRunWide.restCount <= 2 && plan.remainingRunWide.eliteCount >= 3) {
                block -= 2;
            }
            if (plan.remainingRunWide.monsterCount >= 12 && plan.phase != GlobalRunPlan.RunPhase.EARLY) {
                damage -= 1;
            }
        }

        Port weakest = Port.DAMAGE;
        int min = damage;
        if (block < min) {
            min = block;
            weakest = Port.BLOCK;
        }
        if (engine < min) {
            weakest = Port.ENGINE;
        }
        return weakest;
    }

    private static final class LayerResult {
        final double bonus;
        final String tag;

        LayerResult(double bonus, String tag) {
            this.bonus = bonus;
            this.tag = tag == null ? "" : tag;
        }

        static LayerResult none() {
            return new LayerResult(0.0, "");
        }
    }
}
