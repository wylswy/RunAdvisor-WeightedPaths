package com.derekjass.sts.weightedpaths.paths;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheEnding;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class MapPath extends LinkedList<MapRoomNode> implements Comparable<MapPath> {

    private static final Logger logger = LogManager.getLogger(MapPath.class.getName());

    private double value = 0.0f;

    private static List<MapPath> generateStarterPaths() {
        List<MapPath> paths = new LinkedList<>();
        List<MapRoomNode> firstFloor = CardCrawlGame.dungeon.getMap().get(0);
        for (MapRoomNode room : firstFloor) {
            if (!room.hasEdges()) {
                continue;
            }
            MapPath path = new MapPath();
            path.add(room);
            paths.add(path);
        }
        return paths;
    }

    public static List<MapPath> generateAll() throws UnexpectedStateException {
        addSentryBreadcrumb("Begin path generation.");
        addSentryBreadcrumb();
        List<MapPath> paths = new ArrayList<>();
        if (CardCrawlGame.dungeon instanceof TheEnding) {
            addSentryBreadcrumb("In the ending, so don't generate anything.");
            return paths;
        } else if (!AbstractDungeon.firstRoomChosen) {
            addSentryBreadcrumb("Act is fresh, so generate starter paths.");
            paths = generateStarterPaths();
        } else if (AbstractDungeon.getCurrMapNode() == null) {
            throw new UnexpectedStateException("AbstractDungeon current map node is null.");
        } else if (AbstractDungeon.getCurrMapNode().y < AbstractDungeon.MAP_HEIGHT - 2) {
            addSentryBreadcrumb("Generating from current room.");
            if (!AbstractDungeon.getCurrMapNode().hasEdges()) {
                throw new UnexpectedStateException("Current map node has no edges.");
            }
            for (MapEdge edge : AbstractDungeon.getCurrMapNode().getEdges()) {
                MapPath path = new MapPath();
                path.addRoomToPath(edge);
                paths.add(path);
            }
        } else {
            addSentryBreadcrumb("Floor is not eligible for path generation.");
            return paths;
        }
        generateRemaining(paths);
        logger.info("Total paths found: " + paths.size());
        Sentry.clearBreadcrumbs();
        return paths;
    }

    private static void generateRemaining(List<MapPath> paths) throws UnexpectedStateException {
        List<MapPath> newPaths = new LinkedList<>();
        Iterator<MapPath> iter = paths.iterator();
        while (iter.hasNext()) {
            MapPath path = iter.next();
            MapRoomNode lastRoom = path.peekLast();
            if (lastRoom == null) {
                throw new UnexpectedStateException("During path generation, last node in path returned null.");
            } else if (lastRoom.y == AbstractDungeon.MAP_HEIGHT - 2) {
                continue;
            } else if (!lastRoom.hasEdges()) {
                addSentryBreadcrumb("Removing path. Last room in path has no edges.");
                iter.remove();
                continue;
            }
            for (int i = 1; i < lastRoom.getEdges().size(); i++) {
                MapPath newPath = (MapPath) path.clone();
                newPath.addRoomToPath(lastRoom.getEdges().get(i));
                newPaths.add(newPath);
            }
            path.addRoomToPath(lastRoom.getEdges().get(0));
        }
        paths.addAll(newPaths);
        //noinspection ConstantConditions
        if (paths.stream().anyMatch(path -> path.peekLast().y < AbstractDungeon.MAP_HEIGHT - 2)) {
            generateRemaining(paths);
        }
    }

    private static void addSentryBreadcrumb(String note) {
        logger.info(note);
        Breadcrumb crumb = new Breadcrumb();
        crumb.setCategory("map-generation");
        crumb.setMessage(note);
        Sentry.addBreadcrumb(crumb);
    }

    private static void addSentryBreadcrumb() {
        Breadcrumb crumb = new Breadcrumb();
        crumb.setCategory("map-generation");
        crumb.setData("floor", String.valueOf(AbstractDungeon.floorNum));
        crumb.setData("act", CardCrawlGame.dungeon.getClass().getSimpleName());
        crumb.setData("room", AbstractDungeon.getCurrMapNode() == null ?
                "NULL" : AbstractDungeon.getCurrMapNode().room.getClass().getSimpleName());
        crumb.setData("seed", SeedHelper.getString(Settings.seed));
        if (AbstractDungeon.player != null) {
            crumb.setData("character", AbstractDungeon.player.getClass().getSimpleName());
        }
        Sentry.addBreadcrumb(crumb);
    }

    private void addRoomToPath(MapEdge edge) {
        MapRoomNode room = CardCrawlGame.dungeon.getMap().get(edge.dstY).get(edge.dstX);
        add(room);
    }

    public void valuate() {
        if (AbstractDungeon.player == null) {
            this.value = 0.0;
            return;
        }
        int act = AbstractDungeon.actNum > 0 ? AbstractDungeon.actNum : 1;
        this.value = PathValuation.valuate(this, act, WeightedPaths.storeGold);
    }

    public double getValue() {
        return value;
    }

    public boolean hasEmerald() {
        for (MapRoomNode room : this) {
            if (room.hasEmeraldKey) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(MapPath o) {
        return Double.compare(value, o.value);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("Value: " + value + ", Nodes:");
        for (MapRoomNode room : this) {
            out.append(" ").append(room.getRoomSymbol(true));
        }
        return out.toString();
    }
}