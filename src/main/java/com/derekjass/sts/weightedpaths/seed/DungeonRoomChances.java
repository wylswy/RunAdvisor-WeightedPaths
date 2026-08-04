package com.derekjass.sts.weightedpaths.seed;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

/**
 * Applies the same room-type pool weights the game uses in {@code generateRoomTypes}.
 * Seed decode runs before any act dungeon ctor, so static chances are still 0 unless set here.
 */
final class DungeonRoomChances {

    private static final Logger logger = LogManager.getLogger(DungeonRoomChances.class.getName());

    /** Map pool weights (same for all acts); floor-8 treasure is assigned separately. */
    private static final float SHOP = 0.05f;
    private static final float REST = 0.12f;
    private static final float TREASURE = 0.0f;
    private static final float EVENT = 0.22f;
    private static final float ELITE = 0.08f;

    private static Field shopField;
    private static Field restField;
    private static Field treasureField;
    private static Field eventField;
    private static Field eliteField;

    static final class Snapshot {
        final float shop;
        final float rest;
        final float treasure;
        final float event;
        final float elite;
        final int actNum;

        Snapshot(float shop, float rest, float treasure, float event, float elite, int actNum) {
            this.shop = shop;
            this.rest = rest;
            this.treasure = treasure;
            this.event = event;
            this.elite = elite;
            this.actNum = actNum;
        }
    }

    private DungeonRoomChances() {
    }

    static Snapshot capture() {
        ensureFields();
        try {
            return new Snapshot(
                    shopField.getFloat(null),
                    restField.getFloat(null),
                    treasureField.getFloat(null),
                    eventField.getFloat(null),
                    eliteField.getFloat(null),
                    AbstractDungeon.actNum);
        } catch (Exception e) {
            logger.warn("Failed to capture dungeon room chances.", e);
            return null;
        }
    }

    static void applyForAct(int actIndex) {
        ensureFields();
        try {
            shopField.setFloat(null, SHOP);
            restField.setFloat(null, REST);
            treasureField.setFloat(null, TREASURE);
            eventField.setFloat(null, EVENT);
            eliteField.setFloat(null, ELITE);
            AbstractDungeon.actNum = actIndex;
        } catch (Exception e) {
            logger.error("Failed to apply room chances for act {}.", actIndex, e);
        }
    }

    static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ensureFields();
        try {
            shopField.setFloat(null, snapshot.shop);
            restField.setFloat(null, snapshot.rest);
            treasureField.setFloat(null, snapshot.treasure);
            eventField.setFloat(null, snapshot.event);
            eliteField.setFloat(null, snapshot.elite);
            AbstractDungeon.actNum = snapshot.actNum;
        } catch (Exception e) {
            logger.warn("Failed to restore dungeon room chances.", e);
        }
    }

    private static void ensureFields() {
        if (shopField != null) {
            return;
        }
        try {
            shopField = AbstractDungeon.class.getDeclaredField("shopRoomChance");
            restField = AbstractDungeon.class.getDeclaredField("restRoomChance");
            treasureField = AbstractDungeon.class.getDeclaredField("treasureRoomChance");
            eventField = AbstractDungeon.class.getDeclaredField("eventRoomChance");
            eliteField = AbstractDungeon.class.getDeclaredField("eliteRoomChance");
            shopField.setAccessible(true);
            restField.setAccessible(true);
            treasureField.setAccessible(true);
            eventField.setAccessible(true);
            eliteField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Unable to access AbstractDungeon room chance fields.", e);
        }
    }
}
