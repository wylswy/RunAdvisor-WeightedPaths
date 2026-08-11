package com.derekjass.sts.weightedpaths.paths;

import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoom;

/**
 * 游戏层：从地图节点统计路径符号（依赖游戏类，故与纯逻辑的 {@link PathSymbolCounts} 分离）。
 */
public final class NodePathSymbolCounts {

    private NodePathSymbolCounts() {
    }

    /** 从地图节点序列统计符号计数（null 节点与无法解析的房间跳过）。 */
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
}