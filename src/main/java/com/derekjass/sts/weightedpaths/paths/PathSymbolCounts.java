package com.derekjass.sts.weightedpaths.paths;

import com.derekjass.sts.weightedpaths.ui.ModUiStrings;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoom;

public final class PathSymbolCounts {

    public final int eliteCount;
    public final int restCount;
    public final int shopCount;
    public final int eventCount;
    public final int treasureCount;
    public final int monsterCount;

    public PathSymbolCounts(
            int eliteCount,
            int restCount,
            int shopCount,
            int eventCount,
            int treasureCount,
            int monsterCount) {
        this.eliteCount = eliteCount;
        this.restCount = restCount;
        this.shopCount = shopCount;
        this.eventCount = eventCount;
        this.treasureCount = treasureCount;
        this.monsterCount = monsterCount;
    }

    public static PathSymbolCounts fromNodes(Iterable<MapRoomNode> nodes) {
        int elites = 0;
        int rests = 0;
        int shops = 0;
        int events = 0;
        int treasures = 0;
        int monsters = 0;
        for (MapRoomNode node : nodes) {
            if (node == null) {
                continue;
            }
            String symbol;
            try {
                symbol = resolveSymbol(node);
            } catch (Exception ignored) {
                continue;
            }
            if (symbol == null || symbol.isEmpty()) {
                continue;
            }
            switch (symbol) {
                case "E":
                    elites++;
                    break;
                case "R":
                    rests++;
                    break;
                case "$":
                    shops++;
                    break;
                case "?":
                    events++;
                    break;
                case "T":
                    treasures++;
                    break;
                case "M":
                    monsters++;
                    break;
                default:
                    break;
            }
        }
        return new PathSymbolCounts(elites, rests, shops, events, treasures, monsters);
    }

    public static PathSymbolCounts fromSymbols(Iterable<String> symbols) {
        int elites = 0;
        int rests = 0;
        int shops = 0;
        int events = 0;
        int treasures = 0;
        int monsters = 0;
        if (symbols == null) {
            return EMPTY;
        }
        for (String symbol : symbols) {
            if (symbol == null || symbol.isEmpty()) {
                continue;
            }
            switch (symbol) {
                case "E":
                    elites++;
                    break;
                case "R":
                    rests++;
                    break;
                case "$":
                    shops++;
                    break;
                case "?":
                    events++;
                    break;
                case "T":
                    treasures++;
                    break;
                case "M":
                    monsters++;
                    break;
                default:
                    break;
            }
        }
        return new PathSymbolCounts(elites, rests, shops, events, treasures, monsters);
    }

    private static String resolveSymbol(MapRoomNode node) {
        try {
            AbstractRoom room = node.getRoom();
            if (room instanceof TreasureRoom) {
                return "T";
            }
            String symbol = RouteFormatUtil.symbolOrEmpty(node);
            if (!symbol.isEmpty()) {
                return symbol;
            }
            if (room != null) {
                return RouteFormatUtil.normalizeSymbol(room.getMapSymbol());
            }
        } catch (Exception ignored) {
            // Room may not be assigned yet during map transitions.
        }
        return "";
    }

    public String formatRouteSummary() {
        return String.format(
                ModUiStrings.ROUTE_STAT_SUMMARY,
                eliteCount, restCount, shopCount, eventCount, treasureCount, monsterCount);
    }

    public static final PathSymbolCounts EMPTY = new PathSymbolCounts(0, 0, 0, 0, 0, 0);

    public PathSymbolCounts plus(PathSymbolCounts other) {
        if (other == null) {
            return this;
        }
        return new PathSymbolCounts(
                eliteCount + other.eliteCount,
                restCount + other.restCount,
                shopCount + other.shopCount,
                eventCount + other.eventCount,
                treasureCount + other.treasureCount,
                monsterCount + other.monsterCount);
    }
}
