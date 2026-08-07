package com.derekjass.sts.weightedpaths.paths;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 静默猎手 A20 路线估值：基础分 × 层数系数 × 当前模拟状态（血量/敲牌/遗物/AOE）。
 */
public final class SilentRouteValuation {

    public static final Map<String, Double> DEFAULT_MENU_WEIGHTS = new HashMap<>();

    private static final double BASE_ELITE = 9.0;
    private static final double BASE_MONSTER = 7.0;
    private static final double BASE_EVENT = 5.0;
    private static final double BASE_SHOP = 8.0;
    private static final double BASE_REST = 7.0;
    private static final double BASE_TREASURE = 4.0;

    private static final double[] ELITE_ACT = {0.42, 0.5, 1.0};
    private static final double[] MONSTER_ACT = {1.48, 0.6, 0.4};
    private static final double[] EVENT_ACT = {0.65, 1.3, 1.1};
    private static final double[] SHOP_ACT = {0.85, 1.3, 1.2};
    private static final double[] REST_ACT = {1.55, 1.0, 0.8};

    private static final double HP_REST_BOOST_THRESHOLD = 0.35;
    private static final double HP_REST_BOOST_MULT = 2.0;

    // 血线阈值（审查#7 统一散落魔法数字，值不变，纯可读性/可维护性）
    /** 血线 ≤ 此值：怪物房间权重 ×0.70（低血避战）。 */
    private static final double HP_LOW_MONSTER_THRESHOLD = 0.40;
    /** 血线 ≥ 此值且需敲牌：火堆/精英加成条件。 */
    private static final double HP_UPGRADE_GOOD = 0.45;
    /** 血线 ≤ 此值：精英低血惩罚、商店 MealTicket、事件等共用。 */
    private static final double HP_MID = 0.50;
    /** 血线 ≥ 此值：一层火堆敲牌 / 二层精英加成。 */
    private static final double HP_REST_ACT1 = 0.55;
    /** 血线 < 此值：二层/三层精英与怪物强惩罚。 */
    private static final double HP_HIGH = 0.65;
    /** 血线 < 此值：一层精英低血额外惩罚。 */
    private static final double HP_ELITE_LOW = 0.70;

    private static final double GOLD_SHOP_THRESHOLD = 300.0;
    private static final double GOLD_SHOP_MULT = 1.5;
    private static final double ACT2_NO_AOE_MONSTER_MULT = 0.5;
    private static final double NEOW_LAMENT_ELITE_MULT = 2.0;
    private static final double ACT1_ELITE_NOT_READY_MULT = 0.12;
    private static final double ACT1_ELITE_READY_LOW_HP_MULT = 0.35;
    /** 猎手一层战力弱小：即使端口就绪，也按收益/风险对比打折（Q7=b 风险折扣）。 */
    private static final double ACT1_ELITE_READY_OK_MULT = 0.75;

    private static final double ACT1_ELITE_NO_REST_MULT = 0.30;
    private static final double ACT1_ELITE_LOW_HP_MULT = 0.38;
    private static final double ACT1_EARLY_FLOOR_ELITE_MULT = 0.35;
    private static final double ACT1_SECOND_ELITE_MULT = 0.18;
    private static final int ACT1_EARLY_FLOOR_CUTOFF = 8;

    static {
        DEFAULT_MENU_WEIGHTS.put("E", BASE_ELITE * ELITE_ACT[0]);
        DEFAULT_MENU_WEIGHTS.put("M", BASE_MONSTER * MONSTER_ACT[0]);
        DEFAULT_MENU_WEIGHTS.put("?", BASE_EVENT * EVENT_ACT[0]);
        DEFAULT_MENU_WEIGHTS.put("$", BASE_SHOP * SHOP_ACT[0]);
        DEFAULT_MENU_WEIGHTS.put("R", BASE_REST * REST_ACT[0]);
        DEFAULT_MENU_WEIGHTS.put("T", BASE_TREASURE);
    }

    private SilentRouteValuation() {
    }

    public static double roomWeight(String symbol, int actNumber, RouteSimState state) {
        return roomWeightDetailed(symbol, actNumber, state).weight;
    }

    public static RoomWeightDetail roomWeightDetailed(String symbol, int actNumber, RouteSimState state) {
        RoomWeightDetail detail = new RoomWeightDetail();
        if (symbol == null || symbol.isEmpty()) {
            return detail;
        }
        int act = clampAct(actNumber);
        double weight = a20BaseWeight(symbol, act);
        detail.baseWeight = weight;
        if (state != null) {
            weight = applyDynamicOverrides(symbol, act, weight, state, detail);
        }
        double menuMult = menuMultiplier(symbol);
        detail.menuMultiplier = menuMult;
        weight *= menuMult;
        detail.weight = weight;
        return detail;
    }

    public static java.util.Map<String, Double> snapshotRoomWeights(int actNumber, RouteSimState state) {
        java.util.Map<String, Double> weights = new java.util.HashMap<>();
        for (String symbol : new String[] {"M", "E", "R", "?", "$", "T"}) {
            weights.put(symbol, roomWeight(symbol, actNumber, state));
        }
        return weights;
    }

    /** 无模拟状态时的快捷入口（仅菜单预览等场景）。 */
    public static double roomWeight(String symbol) {
        int act = AbstractDungeon.actNum > 0 ? AbstractDungeon.actNum : 1;
        return roomWeight(symbol, act, RouteSimState.fromCurrentRun());
    }

    public static double roomWeightForAct(String symbol, int actNumber) {
        return roomWeight(symbol, actNumber, RouteSimState.fromCurrentRun());
    }

    private static double a20BaseWeight(String symbol, int act) {
        int idx = act - 1;
        switch (symbol) {
            case "E":
                return BASE_ELITE * ELITE_ACT[idx];
            case "M":
                return BASE_MONSTER * MONSTER_ACT[idx];
            case "?":
                return BASE_EVENT * EVENT_ACT[idx];
            case "$":
                return BASE_SHOP * SHOP_ACT[idx];
            case "R":
                return BASE_REST * REST_ACT[idx];
            case "T":
                return BASE_TREASURE;
            default:
                Double menu = WeightedPaths.weights.get(symbol);
                return menu == null ? 0.0 : menu;
        }
    }

    private static double applyDynamicOverrides(
            String symbol, int act, double weight, RouteSimState state, RoomWeightDetail detail) {
        double hpRatio = state.hpRatio();
        RouteRelicFlags relics = state.relics;

        switch (symbol) {
            case "R":
                if (relics.fusionHammer) {
                    weight *= 0.15;
                    break;
                }
                if (act == 1 && !state.act1EliteReady) {
                    weight *= 1.35;
                }
                if (hpRatio < HP_REST_BOOST_THRESHOLD) {
                    weight *= HP_REST_BOOST_MULT;
                } else if (state.needsUpgrade() && hpRatio >= HP_UPGRADE_GOOD && act <= 2) {
                    weight *= 1.45;
                } else if (relics.coffeeDripper && state.needsUpgrade()) {
                    weight *= 1.35;
                } else if (act == 1 && state.needsUpgrade() && hpRatio >= HP_REST_ACT1) {
                    weight *= 1.25;
                }
                break;
            case "$":
                if (state.gold > GOLD_SHOP_THRESHOLD) {
                    weight *= GOLD_SHOP_MULT;
                }
                if (state.needsShopRemoval()) {
                    if (state.gold >= 75 || relics.smilingMask) {
                        weight *= 1.45;
                    } else if (state.gold >= 50) {
                        weight *= 1.15;
                    }
                }
                if (state.strikeCount >= 3 && act <= 2) {
                    weight *= 1.25;
                }
                if (relics.smilingMask && state.deckSize >= 14) {
                    weight *= 1.25;
                }
                if (relics.mealTicket && hpRatio < HP_MID) {
                    weight *= 1.20;
                }
                break;
            case "M":
                if (act == 2 && !state.hasAoe) {
                    weight *= ACT2_NO_AOE_MONSTER_MULT;
                }
                if (hpRatio < HP_LOW_MONSTER_THRESHOLD) {
                    weight *= 0.70;
                }
                if (act >= 3 && hpRatio < HP_HIGH) {
                    weight *= 0.55;
                }
                break;
            case "E":
                boolean neowActive = state.neowLamentBattlesLeft > 0;
                if (neowActive) {
                    // 涅奥祝福(前3战敌1血)：白嫖机会，大幅提升精英权重，无需端口就绪/高血量门槛
                    weight *= NEOW_LAMENT_ELITE_MULT;
                    detail.addNote("neowLamentElite");
                }
                if (relics.blackStar && hpRatio >= HP_UPGRADE_GOOD && state.act1EliteReady) {
                    weight *= 1.20;
                }
                if (relics.preservedInsect && act <= 2 && hpRatio >= HP_LOW_MONSTER_THRESHOLD && state.act1EliteReady) {
                    weight *= 1.15;
                }
                if (act == 1) {
                    if (state.act1ElitesOnPath >= 1) {
                        weight *= ACT1_SECOND_ELITE_MULT;
                        detail.addNote("secondEliteAct1");
                    }
                    if (!state.act1EliteReady && !neowActive) {
                        weight *= ACT1_ELITE_NOT_READY_MULT;
                        detail.addNote("act1NotReady");
                    } else if (hpRatio < HP_MID) {
                        weight *= ACT1_ELITE_READY_LOW_HP_MULT;
                        detail.addNote("act1ReadyLowHp");
                    } else if (hpRatio < HP_HIGH) {
                        weight *= 0.78;
                        detail.addNote("act1ReadyMidHp");
                    } else {
                        weight *= ACT1_ELITE_READY_OK_MULT;
                    }
                    if (state.deckSize < 10) {
                        weight *= 0.72;
                        detail.addNote("deckSmall");
                    }
                    if (hpRatio < HP_ELITE_LOW) {
                        weight *= ACT1_ELITE_LOW_HP_MULT;
                        detail.addNote("hpBelow70");
                    }
                    if (state.estimatedRestAhead <= 0) {
                        weight *= ACT1_ELITE_NO_REST_MULT;
                        detail.addNote("noRestAhead");
                    }
                    if (state.floor <= ACT1_EARLY_FLOOR_CUTOFF) {
                        weight *= ACT1_EARLY_FLOOR_ELITE_MULT;
                        detail.addNote("earlyFloor");
                    }
                } else if (hpRatio < HP_REST_BOOST_THRESHOLD) {
                    weight *= 0.55;
                } else if (act == 2 && hpRatio < HP_REST_ACT1) {
                    weight *= 0.45;
                }
                break;
            case "?":
                if (act == 1) {
                    weight *= hpRatio < HP_REST_ACT1 ? 0.62 : 0.82;
                } else if (act == 2 && hpRatio < HP_MID) {
                    weight *= 1.15;
                }
                break;
            default:
                break;
        }
        return weight;
    }

    /**
     * 能否应付二层多怪战：真 AOE 或 DAMAGE 端口充足（端口化，非流派）。
     */
    static boolean canHandleMultiEnemy(List<AbstractCard> deck) {
        if (deck == null || deck.isEmpty()) {
            return false;
        }
        com.derekjass.sts.weightedpaths.card.DeckSnapshot snap =
                com.derekjass.sts.weightedpaths.card.DeckAnalyzer.analyzeSnapshot(deck);
        if (snap.ports.hasAoe) {
            return true;
        }
        if (snap.ports.damagePoints >= 4) {
            return true;
        }
        return snap.ports.damagePoints >= 2 && snap.ports.hasScaling;
    }

    private static double menuMultiplier(String symbol) {
        Double menu = WeightedPaths.weights.get(symbol);
        Double baseline = DEFAULT_MENU_WEIGHTS.get(symbol);
        if (menu == null || baseline == null || baseline <= 0.0) {
            return 1.0;
        }
        return menu / baseline;
    }

    private static int clampAct(int actNumber) {
        if (actNumber <= 1) {
            return 1;
        }
        if (actNumber >= 3) {
            return 3;
        }
        return actNumber;
    }

    public static final class RoomWeightDetail {
        public double weight;
        public double baseWeight;
        public double menuMultiplier = 1.0;
        public final java.util.List<String> notes = new java.util.ArrayList<>();

        void addNote(String note) {
            if (note != null && !note.isEmpty()) {
                notes.add(note);
            }
        }
    }
}
