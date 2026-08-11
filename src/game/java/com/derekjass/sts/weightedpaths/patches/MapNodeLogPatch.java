package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.card.GlobalRunPlan;
import com.derekjass.sts.weightedpaths.logging.RunAdvisorLogger;
import com.derekjass.sts.weightedpaths.paths.RouteSimState;
import com.derekjass.sts.weightedpaths.paths.SilentRouteValuation;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.HashMap;
import java.util.Map;

/** 记录玩家实际选择的地图节点（需开启决策日志）。 */
public final class MapNodeLogPatch {

    private MapNodeLogPatch() {
    }

    public static void logIfEnabled() {
        if (!RunAdvisorLogger.isEnabled() || AbstractDungeon.player == null) {
            return;
        }
        MapRoomNode node = AbstractDungeon.getCurrMapNode();
        if (node == null) {
            return;
        }
        RunAdvisorLogger.ensureSession();
        RouteSimState state = RouteSimState.fromCurrentRun();
        GlobalRunPlan plan = GlobalRunPlan.fromCurrentRun();
        String symbol = node.getRoomSymbol(true);
        if (symbol == null) {
            symbol = "";
        }

        Map<String, Double> weights = SilentRouteValuation.snapshotRoomWeights(
                AbstractDungeon.actNum, state);
        Map<String, String> notes = new HashMap<>();
        SilentRouteValuation.RoomWeightDetail eliteDetail =
                SilentRouteValuation.roomWeightDetailed("E", AbstractDungeon.actNum, state);
        if (!eliteDetail.notes.isEmpty()) {
            notes.put("E", String.join(",", eliteDetail.notes));
        }

        double pathValue = 0.0;
        if (WeightedPaths.getBestPath() != null) {
            pathValue = WeightedPaths.getBestPath().getValue();
        }

        RunAdvisorLogger.logMapDecision(
                symbol,
                plan.nextRoom,
                pathValue,
                state.act1EliteReady,
                state.hpRatio(),
                state.estimatedRestAhead,
                weights,
                notes);
    }
}
