package com.derekjass.sts.weightedpaths.ui.config;

import basemod.BaseMod;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import com.badlogic.gdx.graphics.Texture;
import com.derekjass.sts.weightedpaths.ui.ModFonts;
import com.derekjass.sts.weightedpaths.ui.ModUiStrings;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.megacrit.cardcrawl.core.Settings;

import java.io.IOException;
import java.util.Properties;

public class Config {

    private static final String COLORED_WEIGHTS_KEY = "coloredWeights";
    private static final String FORCE_EMERALD_KEY = "forceEmerald";
    private static final String SHOW_MAP_PATH_KEY = "showMapPath";
    private static final String SHOW_ACT_PREVIEW_KEY = "showActPreview";
    private static final String SHOW_CARD_SCORES_KEY = "showCardScores";
    private static final String SHOW_NODE_WEIGHTS_KEY = "showNodeWeights";
    private static final String ENABLE_DECISION_LOG_KEY = "enableDecisionLog";

    private static SpireConfig config;
    private static boolean useColoredWeights;
    private static boolean forceEmerald;
    private static boolean showMapPath;
    private static boolean showActPreview;
    private static boolean showCardScores;
    private static boolean showNodeWeights;
    private static boolean enableDecisionLog;

    public static void initialize() {
        Properties defaults = new Properties();
        defaults.setProperty(COLORED_WEIGHTS_KEY, "true");
        defaults.setProperty(FORCE_EMERALD_KEY, "false");
        defaults.setProperty(SHOW_MAP_PATH_KEY, "true");
        defaults.setProperty(SHOW_ACT_PREVIEW_KEY, "true");
        defaults.setProperty(SHOW_CARD_SCORES_KEY, "true");
        defaults.setProperty(SHOW_NODE_WEIGHTS_KEY, "true");
        defaults.setProperty(ENABLE_DECISION_LOG_KEY, "true");
        try {
            config = new SpireConfig("WeightedPaths", "config", defaults);
            config.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
        useColoredWeights = config.getBool(COLORED_WEIGHTS_KEY);
        forceEmerald = config.getBool(FORCE_EMERALD_KEY);
        showMapPath = config.getBool(SHOW_MAP_PATH_KEY);
        showActPreview = config.getBool(SHOW_ACT_PREVIEW_KEY);
        showCardScores = config.getBool(SHOW_CARD_SCORES_KEY);
        showNodeWeights = config.getBool(SHOW_NODE_WEIGHTS_KEY);
        enableDecisionLog = config.getBool(ENABLE_DECISION_LOG_KEY);

        ModPanel panel = new ModPanel();
        float y = 700.0f;
        y = addToggle(panel, ModUiStrings.CONFIG_SHOW_MAP_PATH, showMapPath, SHOW_MAP_PATH_KEY, y);
        y = addToggle(panel, ModUiStrings.CONFIG_SHOW_ACT_PREVIEW, showActPreview, SHOW_ACT_PREVIEW_KEY, y);
        y = addToggle(panel, ModUiStrings.CONFIG_SHOW_CARD_SCORES, showCardScores, SHOW_CARD_SCORES_KEY, y);
        y = addToggle(panel, ModUiStrings.CONFIG_SHOW_NODE_WEIGHTS, showNodeWeights, SHOW_NODE_WEIGHTS_KEY, y);
        y = addToggle(panel, ModUiStrings.CONFIG_ENABLE_DECISION_LOG, enableDecisionLog, ENABLE_DECISION_LOG_KEY, y);
        y = addToggle(panel, ModUiStrings.CONFIG_COLORED_WEIGHTS, useColoredWeights, COLORED_WEIGHTS_KEY, y);
        addToggle(panel, ModUiStrings.CONFIG_FORCE_EMERALD, forceEmerald, FORCE_EMERALD_KEY, y);

        BaseMod.registerModBadge(new Texture("badge.png"),
                ModUiStrings.MOD_NAME, "Derek Jass",
                ModUiStrings.MOD_DESCRIPTION,
                panel);
    }

    private static float addToggle(
            ModPanel panel,
            String label,
            boolean initial,
            String key,
            float y) {
        ModLabeledToggleButton toggle = new ModLabeledToggleButton(
                label,
                400.0f, y, Settings.CREAM_COLOR, ModFonts.body,
                initial, panel,
                (unused) -> {},
                (button) -> {
                    setBool(key, button.enabled);
                    saveConfig();
                });
        panel.addUIElement(toggle);
        return y - 50.0f;
    }

    private static void setBool(String key, boolean value) {
        switch (key) {
            case COLORED_WEIGHTS_KEY:
                useColoredWeights = value;
                break;
            case FORCE_EMERALD_KEY:
                forceEmerald = value;
                break;
            case SHOW_MAP_PATH_KEY:
                showMapPath = value;
                break;
            case SHOW_ACT_PREVIEW_KEY:
                showActPreview = value;
                break;
            case SHOW_CARD_SCORES_KEY:
                showCardScores = value;
                break;
            case SHOW_NODE_WEIGHTS_KEY:
                showNodeWeights = value;
                break;
            case ENABLE_DECISION_LOG_KEY:
                enableDecisionLog = value;
                break;
            default:
                break;
        }
        config.setBool(key, value);
    }

    public static boolean useColoredWeights() {
        return useColoredWeights;
    }

    public static boolean forceEmerald() {
        return forceEmerald;
    }

    public static boolean showMapPath() {
        return showMapPath;
    }

    public static boolean showActPreview() {
        return showActPreview;
    }

    public static boolean showCardScores() {
        return showCardScores;
    }

    public static boolean showNodeWeights() {
        return showNodeWeights;
    }

    public static boolean enableDecisionLog() {
        return enableDecisionLog;
    }

    private static void saveConfig() {
        try {
            config.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
