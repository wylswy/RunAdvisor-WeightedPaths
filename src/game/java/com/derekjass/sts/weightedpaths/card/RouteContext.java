package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.paths.MapPath;
import com.derekjass.sts.weightedpaths.paths.NodePathSymbolCounts;
import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.ArrayList;
import java.util.List;

/** @deprecated 请使用 {@link GlobalRunPlan}。保留以兼容旧调用。 */
public final class RouteContext {

    public final int elitesAhead;
    public final int restsAhead;
    public final int shopsAhead;
    public final int actNumber;

    public RouteContext(int elitesAhead, int restsAhead, int shopsAhead, int actNumber) {
        this.elitesAhead = elitesAhead;
        this.restsAhead = restsAhead;
        this.shopsAhead = shopsAhead;
        this.actNumber = actNumber;
    }

    public static RouteContext fromCurrentRun() {
        GlobalRunPlan plan = GlobalRunPlan.fromCurrentRun();
        return new RouteContext(
                plan.remainingThisAct.eliteCount,
                plan.remainingThisAct.restCount,
                plan.remainingThisAct.shopCount,
                plan.actNumber);
    }

    public static RouteContext fromLegacyPath() {
        int act = AbstractDungeon.actNum;
        MapPath best = WeightedPaths.getBestPath();
        if (best == null || best.isEmpty()) {
            return new RouteContext(0, 0, 0, act);
        }
        List<MapRoomNode> nodes = new ArrayList<>();
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        if (current != null) {
            nodes.add(current);
        }
        for (MapRoomNode node : best) {
            if (node != null) {
                nodes.add(node);
            }
        }
        PathSymbolCounts counts = NodePathSymbolCounts.fromNodes(nodes);
        return new RouteContext(counts.eliteCount, counts.restCount, counts.shopCount, act);
    }
}
