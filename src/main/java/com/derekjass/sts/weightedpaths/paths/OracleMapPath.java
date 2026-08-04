package com.derekjass.sts.weightedpaths.paths;

import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Path enumeration and scoring on a decoded seed map (not the live dungeon map).
 */
public class OracleMapPath extends LinkedList<MapRoomNode> implements Comparable<OracleMapPath> {

    private double value = 0.0;

    public static List<OracleMapPath> generateAll(ArrayList<ArrayList<MapRoomNode>> map) {
        List<OracleMapPath> paths = new LinkedList<>();
        if (map == null || map.isEmpty()) {
            return paths;
        }
        for (MapRoomNode room : map.get(0)) {
            if (!room.hasEdges()) {
                continue;
            }
            OracleMapPath path = new OracleMapPath();
            path.add(room);
            paths.add(path);
        }
        generateRemaining(paths, map, map.size());
        return paths;
    }

    private static void generateRemaining(
            List<OracleMapPath> paths,
            ArrayList<ArrayList<MapRoomNode>> map,
            int mapHeight) {
        List<OracleMapPath> newPaths = new LinkedList<>();
        Iterator<OracleMapPath> iter = paths.iterator();
        while (iter.hasNext()) {
            OracleMapPath path = iter.next();
            MapRoomNode lastRoom = path.peekLast();
            if (lastRoom == null) {
                iter.remove();
                continue;
            }
            if (lastRoom.y == mapHeight - 2) {
                continue;
            }
            if (!lastRoom.hasEdges()) {
                iter.remove();
                continue;
            }
            for (int i = 1; i < lastRoom.getEdges().size(); i++) {
                OracleMapPath newPath = (OracleMapPath) path.clone();
                newPath.addRoomToPath(lastRoom.getEdges().get(i), map);
                newPaths.add(newPath);
            }
            path.addRoomToPath(lastRoom.getEdges().get(0), map);
        }
        paths.addAll(newPaths);
        boolean canExpand = false;
        for (OracleMapPath path : paths) {
            MapRoomNode last = path.peekLast();
            if (last != null && last.y < mapHeight - 2 && last.hasEdges()) {
                canExpand = true;
                break;
            }
        }
        if (canExpand) {
            generateRemaining(paths, map, mapHeight);
        }
    }

    private void addRoomToPath(MapEdge edge, ArrayList<ArrayList<MapRoomNode>> map) {
        add(map.get(edge.dstY).get(edge.dstX));
    }

    public void valuate(Map<MapRoomNode, Double> storeGold, int actNumber) {
        this.value = PathValuation.valuate(this, actNumber, storeGold);
    }

    public double getValue() {
        return value;
    }

    public String toSymbolRoute() {
        return RouteFormatUtil.joinSymbols(this);
    }

    @Override
    public int compareTo(OracleMapPath other) {
        return Double.compare(value, other.value);
    }
}
