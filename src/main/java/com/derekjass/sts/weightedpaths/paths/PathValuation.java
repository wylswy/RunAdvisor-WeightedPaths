package com.derekjass.sts.weightedpaths.paths;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.helpers.RelicTracker;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.Map;

/** 按模拟状态逐步评估路线总分。 */
public final class PathValuation {

    private PathValuation() {
    }

    public static double valuate(Iterable<MapRoomNode> nodes, int actNumber, Map<MapRoomNode, Double> storeGold) {
        return valuate(nodes, actNumber, storeGold, RouteSimState.fromCurrentRun());
    }

    /**
     * 估值入口：接受注入的模拟状态（浅解耦，便于测试与实时重算）。
     * 原入口 {@link #valuate(Iterable, int, Map)} 内部委托本方法，游戏内行为不变。
     */
    public static double valuate(Iterable<MapRoomNode> nodes, int actNumber, Map<MapRoomNode, Double> storeGold, RouteSimState state) {
        double summedValue = 0.0;
        double estimatedGold = state.gold;
        boolean hasMaw = RelicTracker.hasMaw;

        for (MapRoomNode room : nodes) {
            if (room == null) {
                continue;
            }
            String roomSymbol = room.getRoomSymbol(true);
            if (roomSymbol == null || roomSymbol.isEmpty()) {
                continue;
            }

            if (!RelicTracker.hasEcto) {
                estimatedGold += (hasMaw ? 12.0 : 0.0);
            }

            switch (roomSymbol) {
                case "T":
                    if (!RelicTracker.hasEcto) {
                        estimatedGold += 18.4;
                    }
                    summedValue += SilentRouteValuation.roomWeight(roomSymbol, actNumber, state);
                    break;
                case "M":
                    summedValue += SilentRouteValuation.roomWeight(roomSymbol, actNumber, state);
                    if (!RelicTracker.hasEcto) {
                        estimatedGold += 15.0 + (RelicTracker.hasIdol ? 3.7 : 0.0);
                    }
                    break;
                case "?":
                    summedValue += SilentRouteValuation.roomWeight(roomSymbol, actNumber, state);
                    if (!RelicTracker.hasEcto) {
                        estimatedGold += (RelicTracker.hasFace ? 50.0 : 0.0);
                    }
                    break;
                case "E":
                    summedValue += SilentRouteValuation.roomWeight(roomSymbol, actNumber, state);
                    if (!RelicTracker.hasEcto) {
                        estimatedGold += 30.0 + (RelicTracker.hasIdol ? 7.8 : 0.0);
                    }
                    break;
                case "R":
                    summedValue += SilentRouteValuation.roomWeight(roomSymbol, actNumber, state);
                    break;
                case "$":
                    if (storeGold != null) {
                        Double existing = storeGold.get(room);
                        storeGold.put(room, existing == null ? estimatedGold : Math.max(estimatedGold, existing));
                    }
                    summedValue += estimatedGold / 100.0
                            / (RelicTracker.hasMembership ? 0.5 : 1.0)
                            / (RelicTracker.hasCourier ? 0.8 : 1.0)
                            * SilentRouteValuation.roomWeight(roomSymbol, actNumber, state);
                    estimatedGold = 0.0;
                    hasMaw = false;
                    break;
                default:
                    break;
            }

            state.visitRoom(roomSymbol, actNumber);
        }
        return summedValue;
    }
}
