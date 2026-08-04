package com.derekjass.sts.weightedpaths.card.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class CardStatsLoader {

    private static final Logger logger = LogManager.getLogger(CardStatsLoader.class.getName());
    private static final String RESOURCE = "/card/silent_cards_a20.json";

    private static Map<String, CardStatEntry> cards = new HashMap<>();
    private static boolean loaded = false;

    private CardStatsLoader() {
    }

    public static void initialize() {
        if (loaded) {
            return;
        }
        try (InputStream in = CardStatsLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                logger.error("Card stats resource missing: {}", RESOURCE);
                return;
            }
            InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            Type type = new TypeToken<SilentCardDataFile>() {}.getType();
            SilentCardDataFile file = gson.fromJson(reader, type);
            if (file != null && file.cards != null) {
                cards = file.cards;
            }
            loaded = true;
            logger.info("Loaded {} Silent card stat entries.", cards.size());
        } catch (Exception e) {
            logger.error("Failed to load card stats.", e);
        }
    }

    public static CardStatEntry get(String cardId) {
        if (!loaded) {
            initialize();
        }
        CardStatEntry entry = cards.get(cardId);
        return entry == null ? null : entry;
    }

    public static boolean isLoaded() {
        return loaded && !cards.isEmpty();
    }

    private static final class SilentCardDataFile {
        Map<String, CardStatEntry> cards;
    }
}
