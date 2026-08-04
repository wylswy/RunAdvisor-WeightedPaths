package com.derekjass.sts.weightedpaths.seed;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapGenerator;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.map.RoomTypeAssigner;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SeedOracle {

    private static final Logger logger = LogManager.getLogger(SeedOracle.class.getName());

    private static final int MAP_HEIGHT = 15;
    private static final int MAP_WIDTH = 7;
    private static final int PATHS = 6;

    private static List<ActMapSummary> cached = null;
    private static List<DecodedActMap> decodedActs = null;
    private static long cachedSeed = 0;
    private static boolean decodeFailed = false;

    private static Method generateRoomTypesMethod;

    private SeedOracle() {
    }

    public static void decode(long seed) {
        if (cached != null && cachedSeed == seed) {
            return;
        }
        cachedSeed = seed;
        decodeFailed = false;
        List<ActMapSummary> summaries = new ArrayList<>();
        List<DecodedActMap> decoded = new ArrayList<>();
        try {
            for (int act = 1; act <= 3; act++) {
                DecodedActMap decodedAct = generateActMap(act, seed);
                decoded.add(decodedAct);
                summaries.add(decodedAct.summary);
            }
            decodedActs = Collections.unmodifiableList(decoded);
            cached = Collections.unmodifiableList(summaries);
            logger.info("SeedOracle decoded seed {} for 3 acts.", seed);
            GlobalRunPlanner.recompute();
        } catch (Exception e) {
            decodeFailed = true;
            cached = null;
            decodedActs = null;
            logger.error("SeedOracle failed to decode seed {}.", seed, e);
        }
    }

    public static List<ActMapSummary> getPreviews() {
        return cached == null ? Collections.emptyList() : cached;
    }

    public static ActMapSummary getAct(int actNumber) {
        if (cached == null || actNumber < 1 || actNumber > cached.size()) {
            return null;
        }
        return cached.get(actNumber - 1);
    }

    public static List<DecodedActMap> getDecodedActs() {
        return decodedActs == null ? Collections.emptyList() : decodedActs;
    }

    public static boolean isAvailable() {
        return cached != null && !decodeFailed;
    }

    public static void clear() {
        cached = null;
        decodedActs = null;
        cachedSeed = 0;
        decodeFailed = false;
    }

    private static long mapRngSeedForAct(int actIndex, long seed) {
        int actNumAtGeneration = actIndex;
        switch (actIndex) {
            case 1:
                return seed + actNumAtGeneration;
            case 2:
                return seed + (long) actNumAtGeneration * 100L;
            case 3:
                return seed + (long) actNumAtGeneration * 200L;
            default:
                throw new IllegalArgumentException("Invalid act index: " + actIndex);
        }
    }

    private static DecodedActMap generateActMap(int actIndex, long seed) throws Exception {
        long mapRngSeed = mapRngSeedForAct(actIndex, seed);
        Random mapRng = new Random(mapRngSeed);
        ArrayList<ArrayList<MapRoomNode>> map =
                MapGenerator.generateDungeon(MAP_HEIGHT, MAP_WIDTH, PATHS, mapRng);

        int nodeCount = countAssignableNodes(map);
        ArrayList<AbstractRoom> roomTypes = new ArrayList<>();

        DungeonRoomChances.Snapshot savedChances = DungeonRoomChances.capture();
        try {
            DungeonRoomChances.applyForAct(actIndex);
            invokeGenerateRoomTypes(roomTypes, nodeCount);
        } finally {
            DungeonRoomChances.restore(savedChances);
        }

        RoomTypeAssigner.assignRowAsRoomType(map.get(map.size() - 1), RestRoom.class);
        RoomTypeAssigner.assignRowAsRoomType(map.get(0), MonsterRoom.class);

        if (Settings.isEndless && AbstractDungeon.player != null
                && AbstractDungeon.player.hasBlight("MimicInfestation")) {
            RoomTypeAssigner.assignRowAsRoomType(map.get(8), MonsterRoomElite.class);
        } else {
            RoomTypeAssigner.assignRowAsRoomType(map.get(8), TreasureRoom.class);
        }

        map = RoomTypeAssigner.distributeRoomsAcrossMap(mapRng, map, roomTypes);

        // Fixed rows must stay correct even if distribution left gaps on special floors.
        forceAssignRow(map.get(0), MonsterRoom.class);
        forceAssignRow(map.get(8), TreasureRoom.class);
        forceAssignRow(map.get(map.size() - 1), RestRoom.class);

        logger.info("SeedOracle Act {} map (seed offset {}):", actIndex, mapRngSeed);
        logger.info(MapGenerator.toString(map, true));

        ActMapSummary summary = summarize(actIndex, map);
        return new DecodedActMap(actIndex, map, summary);
    }

    private static int countAssignableNodes(ArrayList<ArrayList<MapRoomNode>> map) {
        int count = 0;
        int penultimateFloor = map.size() - 2;
        for (ArrayList<MapRoomNode> floor : map) {
            for (MapRoomNode node : floor) {
                if (!node.hasEdges()) {
                    continue;
                }
                if (node.y == penultimateFloor) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }

    private static void invokeGenerateRoomTypes(ArrayList<AbstractRoom> roomTypes, int nodeCount) throws Exception {
        if (generateRoomTypesMethod == null) {
            generateRoomTypesMethod = AbstractDungeon.class.getDeclaredMethod(
                    "generateRoomTypes", ArrayList.class, int.class);
            generateRoomTypesMethod.setAccessible(true);
        }
        generateRoomTypesMethod.invoke(null, roomTypes, nodeCount);
    }

    private static ActMapSummary summarize(int actIndex, ArrayList<ArrayList<MapRoomNode>> map) {
        List<List<String>> floorSymbols = new ArrayList<>();

        for (int floor = map.size() - 1; floor >= 0; floor--) {
            List<String> symbols = new ArrayList<>();
            for (MapRoomNode node : map.get(floor)) {
                if (!node.hasEdges()) {
                    continue;
                }
                String symbol = node.getRoomSymbol(true);
                if (symbol != null) {
                    symbols.add(symbol);
                }
            }
            if (!symbols.isEmpty()) {
                floorSymbols.add(symbols);
            }
        }

        return new ActMapSummary(actIndex, floorSymbols);
    }

    private static void forceAssignRow(
            ArrayList<MapRoomNode> row,
            Class<? extends AbstractRoom> roomClass) {
        if (row == null) {
            return;
        }
        for (MapRoomNode node : row) {
            if (node == null || !node.hasEdges()) {
                continue;
            }
            try {
                node.setRoom(roomClass.newInstance());
            } catch (Exception e) {
                logger.warn("Failed to force-assign {} on map node.", roomClass.getSimpleName(), e);
            }
        }
    }
}
