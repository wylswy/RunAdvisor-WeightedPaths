package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.paths.MapPath;
import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;
import com.derekjass.sts.weightedpaths.paths.RouteFormatUtil;
import com.derekjass.sts.weightedpaths.seed.ActMapSummary;
import com.derekjass.sts.weightedpaths.seed.SeedOracle;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 整局规划上下文：当前幕 live 剩余路线 + 种子解码的未来幕最优路线。
 * 选牌推荐必须基于此，而非单卡/单幕快照。
 */
public final class GlobalRunPlan {

    public enum RunPhase {
        /** Act1 前段，求稳发育 */
        EARLY,
        /** Act1 后段 / Act2，确立方向 */
        DEVELOPMENT,
        /** Act3，冲刺与补齐短板 */
        LATE
    }

    public final boolean seedAvailable;
    public final RunPhase phase;
    public final int actNumber;
    public final int actsRemaining;

    /** 当前幕：从玩家位置沿 live 最优路径的剩余统计 */
    public final PathSymbolCounts remainingThisAct;
    /** 全 run：本幕剩余 + 种子规划的未来幕 */
    public final PathSymbolCounts remainingRunWide;

    /** 当前幕前方房间符号序列（不含当前格） */
    public final List<String> upcomingThisAct;
    /** 下一房符号；空串表示未知或已无后续 */
    public final String nextRoom;
    /** 距下一精英还有几房；-1 表示前方无精英 */
    public final int roomsUntilElite;

    public GlobalRunPlan(
            boolean seedAvailable,
            RunPhase phase,
            int actNumber,
            int actsRemaining,
            PathSymbolCounts remainingThisAct,
            PathSymbolCounts remainingRunWide,
            List<String> upcomingThisAct,
            String nextRoom,
            int roomsUntilElite) {
        this.seedAvailable = seedAvailable;
        this.phase = phase;
        this.actNumber = actNumber;
        this.actsRemaining = actsRemaining;
        this.remainingThisAct = remainingThisAct == null ? PathSymbolCounts.EMPTY : remainingThisAct;
        this.remainingRunWide = remainingRunWide == null ? PathSymbolCounts.EMPTY : remainingRunWide;
        this.upcomingThisAct = upcomingThisAct == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(upcomingThisAct);
        this.nextRoom = nextRoom == null ? "" : nextRoom;
        this.roomsUntilElite = roomsUntilElite;
    }

    public static GlobalRunPlan fromCurrentRun() {
        int act = AbstractDungeon.actNum;
        RunPhase phase = resolvePhase(act, AbstractDungeon.floorNum);
        int actsRemaining = Math.max(0, 4 - act);

        List<String> upcoming = collectUpcomingThisAct();
        PathSymbolCounts thisAct = PathSymbolCounts.fromSymbols(upcoming);
        String next = upcoming.isEmpty() ? "" : upcoming.get(0);
        int untilElite = roomsUntilSymbol(upcoming, "E");

        boolean seedOk = SeedOracle.isAvailable();
        PathSymbolCounts runWide = thisAct;
        if (seedOk) {
            for (ActMapSummary summary : SeedOracle.getPreviews()) {
                if (summary.actNumber <= act) {
                    continue;
                }
                runWide = runWide.plus(summary.getPlannedCounts());
            }
        }

        return new GlobalRunPlan(
                seedOk,
                phase,
                act,
                actsRemaining,
                thisAct,
                runWide,
                upcoming,
                next,
                untilElite);
    }

    public boolean nextRoomIsElite() {
        return "E".equals(nextRoom);
    }

    public boolean eliteWithin(int rooms) {
        return roomsUntilElite >= 0 && roomsUntilElite <= rooms;
    }

    private static RunPhase resolvePhase(int act, int floor) {
        if (act == 1 && floor <= 8) {
            return RunPhase.EARLY;
        }
        if (act >= 3) {
            return RunPhase.LATE;
        }
        return RunPhase.DEVELOPMENT;
    }

    private static List<String> collectUpcomingThisAct() {
        MapPath best = WeightedPaths.getBestPath();
        if (best == null || best.isEmpty()) {
            return Collections.emptyList();
        }
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        List<String> upcoming = new ArrayList<>();
        boolean afterCurrent = current == null;

        for (MapRoomNode node : best) {
            if (!afterCurrent) {
                if (node == current) {
                    afterCurrent = true;
                }
                continue;
            }
            String symbol = RouteFormatUtil.symbolOrEmpty(node);
            if (!symbol.isEmpty()) {
                upcoming.add(symbol);
            }
        }

        if (upcoming.isEmpty() && current == null) {
            for (MapRoomNode node : best) {
                String symbol = RouteFormatUtil.symbolOrEmpty(node);
                if (!symbol.isEmpty()) {
                    upcoming.add(symbol);
                }
            }
        }
        return upcoming;
    }

    private static int roomsUntilSymbol(List<String> upcoming, String target) {
        for (int i = 0; i < upcoming.size(); i++) {
            if (target.equals(upcoming.get(i))) {
                return i + 1;
            }
        }
        return -1;
    }
}
