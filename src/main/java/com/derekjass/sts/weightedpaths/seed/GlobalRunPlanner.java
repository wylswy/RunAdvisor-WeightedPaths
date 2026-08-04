package com.derekjass.sts.weightedpaths.seed;

import com.derekjass.sts.weightedpaths.paths.OracleMapPath;
import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;
import com.derekjass.sts.weightedpaths.paths.RouteFormatUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GlobalRunPlanner {

    private static final Logger logger = LogManager.getLogger(GlobalRunPlanner.class.getName());

    private GlobalRunPlanner() {
    }

    public static void recompute() {
        if (!SeedOracle.isAvailable()) {
            return;
        }
        if (AbstractDungeon.player == null) {
            return;
        }
        List<DecodedActMap> acts = SeedOracle.getDecodedActs();
        for (DecodedActMap act : acts) {
            recomputeForAct(act);
        }
    }

    private static void recomputeForAct(DecodedActMap act) {
        List<OracleMapPath> paths = OracleMapPath.generateAll(act.map);
        if (paths.isEmpty()) {
            act.summary.clearRecommendedPlan();
            return;
        }
        HashMap<MapRoomNode, Double> storeGold = new HashMap<>();
        for (OracleMapPath path : paths) {
            path.valuate(storeGold, act.actNumber);
        }
        paths.sort(Collections.reverseOrder());
        OracleMapPath best = paths.get(0);
        PathSymbolCounts routeCounts = PathSymbolCounts.fromNodes(best);
        act.summary.setRecommendedPlan(routeCounts, routeSymbols(best));
        logger.info("Act {} planned route (score {}): {}", act.actNumber, best.getValue(), best.toSymbolRoute());
    }

    private static List<String> routeSymbols(OracleMapPath path) {
        List<String> symbols = new ArrayList<>();
        for (MapRoomNode node : path) {
            String symbol = RouteFormatUtil.symbolOrEmpty(node);
            if (!symbol.isEmpty()) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }
}
