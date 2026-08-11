package com.derekjass.sts.weightedpaths.paths;

import com.derekjass.sts.weightedpaths.card.SituationalContext;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 沿路线推进时的模拟 run 状态：血量、金币、敲牌需求、AOE、遗物约束。
 * 每评估一个节点先用当前模拟状态打分，再模拟进入该房间后的变化。
 */
public final class RouteSimState {

    private static final Set<String> UPGRADE_PRIORITY = new HashSet<>(Arrays.asList(
            "Neutralize",
            "Footwork",
            "Acrobatics",
            "Catalyst",
            "Accuracy",
            "Blade Dance",
            "PiercingWail",
            "Corpse Explosion",
            "Adrenaline",
            "Leg Sweep",
            "Backflip"
    ));

    public int currentHp;
    public int maxHp;
    public int gold;
    public int neowLamentBattlesLeft;
    public int upgradeNeedCount;
    public int deckSize;
    public int strikeCount;
    public int defendCount;
    public boolean hasAoe;
    /** 牌组是否具备一层打精英的过渡/输出（来自当前 masterDeck 快照）。 */
    public boolean act1EliteReady;
    public int floor;
    /** 当前幕最优路径剩余休息点数量。 */
    public int estimatedRestAhead;
    /** 沿当前评估路径，一层已计数的精英房（用于第二精英重罚）。 */
    public int act1ElitesOnPath;
    /** R1 修复：模拟死亡标记。血量被打到 ≤0 时置 true，该路线整体判死（PathValuation 返回 -1e6）。 */
    public boolean dead = false;
    public final RouteRelicFlags relics;

    public RouteSimState(
            int currentHp,
            int maxHp,
            int gold,
            int neowLamentBattlesLeft,
            int upgradeNeedCount,
            int deckSize,
            int strikeCount,
            int defendCount,
            boolean hasAoe,
            boolean act1EliteReady,
            int floor,
            int estimatedRestAhead,
            int act1ElitesOnPath,
            RouteRelicFlags relics) {
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.gold = gold;
        this.neowLamentBattlesLeft = neowLamentBattlesLeft;
        this.upgradeNeedCount = upgradeNeedCount;
        this.deckSize = deckSize;
        this.strikeCount = strikeCount;
        this.defendCount = defendCount;
        this.hasAoe = hasAoe;
        this.act1EliteReady = act1EliteReady;
        this.floor = floor;
        this.estimatedRestAhead = estimatedRestAhead;
        this.act1ElitesOnPath = act1ElitesOnPath;
        this.relics = relics;
    }

    public static RouteSimState fromCurrentRun() {
        if (AbstractDungeon.player == null) {
            return neutral();
        }
        List<AbstractCard> deck = AbstractDungeon.player.masterDeck.group;
        com.derekjass.sts.weightedpaths.card.DeckSnapshot snapshot =
                com.derekjass.sts.weightedpaths.card.DeckAnalyzer.analyzeSnapshot(deck);
        com.derekjass.sts.weightedpaths.card.GlobalRunPlan plan =
                com.derekjass.sts.weightedpaths.card.GlobalRunPlan.fromCurrentRun();
        int restAhead = countSymbol(plan.upcomingThisAct, "R");
        return new RouteSimState(
                AbstractDungeon.player.currentHealth,
                AbstractDungeon.player.maxHealth,
                AbstractDungeon.player.gold,
                SituationalContext.neowLamentBattlesRemaining(),
                countUpgradeNeed(deck),
                snapshot.ports.deckSize,
                snapshot.strikeCount,
                snapshot.defendCount,
                SilentRouteValuation.canHandleMultiEnemy(deck),
                snapshot.act1EliteReady(),
                AbstractDungeon.floorNum,
                restAhead,
                0,
                RouteRelicFlags.fromPlayer());
    }

    private static RouteSimState neutral() {
        return new RouteSimState(70, 70, 99, 0, 0, 10, 1, 1, false, false, 1, 0, 0, RouteRelicFlags.none());
    }

    /**
     * S3 修复：未来幕路线的估值基线状态——满血、按幕估算金币、沿用当前牌组/遗物。
     * 不再继承当前幕已被消耗的血量/金币（避免 Act2/3 预览路线分数随 Act1 状态抖动）。
     */
    public static RouteSimState forFutureAct(int actNumber) {
        if (AbstractDungeon.player == null) {
            return neutral();
        }
        List<AbstractCard> deck = AbstractDungeon.player.masterDeck.group;
        com.derekjass.sts.weightedpaths.card.DeckSnapshot snapshot =
                com.derekjass.sts.weightedpaths.card.DeckAnalyzer.analyzeSnapshot(deck);
        int maxHp = AbstractDungeon.player.maxHealth;
        int gold = actNumber >= 3 ? 300 : 200;
        return new RouteSimState(
                maxHp, maxHp, gold, 0,
                countUpgradeNeed(deck),
                snapshot.ports.deckSize,
                snapshot.strikeCount,
                snapshot.defendCount,
                SilentRouteValuation.canHandleMultiEnemy(deck),
                snapshot.act1EliteReady(),
                1, 0, 0,
                RouteRelicFlags.fromPlayer());
    }

    public RouteSimState copy() {
        RouteSimState s = new RouteSimState(
                currentHp, maxHp, gold, neowLamentBattlesLeft,
                upgradeNeedCount, deckSize, strikeCount, defendCount, hasAoe, act1EliteReady,
                floor, estimatedRestAhead, act1ElitesOnPath, relics);
        s.dead = dead;
        return s;
    }

    public boolean needsShopRemoval() {
        int urgency = strikeCount * 2;
        if (deckSize >= 16) {
            urgency += 2;
        }
        if (deckSize >= 20) {
            urgency += 3;
        }
        if (defendCount >= 4) {
            urgency += 2;
        }
        return strikeCount >= 2 || urgency >= 5;
    }

    public double hpRatio() {
        return maxHp > 0 ? (double) currentHp / maxHp : 1.0;
    }

    public boolean needsUpgrade() {
        return upgradeNeedCount > 0;
    }

    /** 模拟进入房间后的状态（供下一段路线评分使用）。 */
    public void visitRoom(String symbol, int act) {
        if (symbol == null) {
            return;
        }
        switch (symbol) {
            case "M":
                applyCombatHpLoss(monsterLossFraction(act));
                addGold(combatGold(act, false));
                decrementLament();
                deckSize = Math.min(deckSize + 1, 35);
                break;
            case "E":
                applyCombatHpLoss(eliteLossFraction(act));
                addGold(combatGold(act, true));
                decrementLament();
                deckSize = Math.min(deckSize + 1, 35);
                if (act == 1) {
                    act1ElitesOnPath++;
                }
                break;
            case "?":
                applyCombatHpLoss(eventLossFraction(act));
                decrementLament();
                break;
            case "R":
                applyRest(act);
                break;
            case "$":
                applyShopVisit(act);
                break;
            case "T":
                addGold(18);
                break;
            default:
                break;
        }
    }

    private void applyShopVisit(int act) {
        int removalCost = relics.smilingMask ? 0 : 75;
        if (relics.smilingMask || gold >= removalCost) {
            if (!relics.smilingMask) {
                gold -= removalCost;
            }
            if (strikeCount > 0) {
                strikeCount--;
            } else if (defendCount > 2) {
                defendCount--;
            }
            deckSize = Math.max(5, deckSize - 1);
        }
        gold = (int) (gold * 0.55);
        if (relics.mealTicket && hpRatio() < 0.55) {
            healFraction(0.15);
        }
    }

    private void applyRest(int act) {
        if (relics.fusionHammer) {
            return;
        }
        boolean wantSleep = hpRatio() < 0.35
                || (act >= 3 && !needsUpgrade())
                || (relics.coffeeDripper && !needsUpgrade());
        if (wantSleep && !relics.coffeeDripper) {
            healFraction(0.30);
        } else if (needsUpgrade()) {
            upgradeNeedCount = Math.max(0, upgradeNeedCount - 1);
        } else if (!relics.coffeeDripper) {
            healFraction(0.30);
        }
    }

    private void applyCombatHpLoss(double fraction) {
        int loss = (int) Math.ceil(maxHp * fraction);
        currentHp -= loss;
        if (currentHp <= 0) {
            currentHp = 0;
            dead = true;
        } else {
            currentHp = Math.max(1, currentHp);
        }
    }

    private void healFraction(double fraction) {
        int amount = (int) Math.floor(maxHp * fraction);
        currentHp = Math.min(maxHp, currentHp + amount);
    }

    private void addGold(int amount) {
        if (!relics.ectoPlasm) {
            gold += amount;
        }
    }

    private void decrementLament() {
        if (neowLamentBattlesLeft > 0) {
            neowLamentBattlesLeft--;
        }
    }

    private static double monsterLossFraction(int act) {
        switch (act) {
            case 1:
                return 0.08;
            case 2:
                return 0.15;
            case 3:
                return 0.12;
            default:
                return 0.10;
        }
    }

    private static double eliteLossFraction(int act) {
        switch (act) {
            case 1:
                return 0.28;
            case 2:
                return 0.30;
            case 3:
                return 0.25;
            default:
                return 0.24;
        }
    }

    private static double eventLossFraction(int act) {
        return act == 1 ? 0.05 : 0.08;
    }

    private static int combatGold(int act, boolean elite) {
        if (elite) {
            return act == 1 ? 30 : 35;
        }
        return 15;
    }

    static int countUpgradeNeed(List<AbstractCard> deck) {
        if (deck == null) {
            return 0;
        }
        int count = 0;
        for (AbstractCard card : deck) {
            if (card == null || card.timesUpgraded > 0) {
                continue;
            }
            if (UPGRADE_PRIORITY.contains(card.cardID)) {
                count++;
            }
        }
        return count;
    }

    private static int countSymbol(List<String> symbols, String target) {
        if (symbols == null) {
            return 0;
        }
        int count = 0;
        for (String symbol : symbols) {
            if (target.equals(symbol)) {
                count++;
            }
        }
        return count;
    }
}
