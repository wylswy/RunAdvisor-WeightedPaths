package com.derekjass.sts.weightedpaths.paths;

import com.megacrit.cardcrawl.map.MapRoomNode;

public final class RouteFormatUtil {

    private RouteFormatUtil() {
    }

    public static String symbolOrEmpty(MapRoomNode node) {
        if (node == null) {
            return "";
        }
        try {
            return normalizeSymbol(node.getRoomSymbol(true));
        } catch (Exception e) {
            return "";
        }
    }

    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty() || "null".equalsIgnoreCase(symbol) || "*".equals(symbol)) {
            return "";
        }
        return symbol;
    }

    public static String joinSymbols(Iterable<MapRoomNode> nodes) {
        StringBuilder builder = new StringBuilder();
        for (MapRoomNode node : nodes) {
            String symbol = symbolOrEmpty(node);
            if (symbol.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(symbol);
        }
        return builder.toString();
    }
}
