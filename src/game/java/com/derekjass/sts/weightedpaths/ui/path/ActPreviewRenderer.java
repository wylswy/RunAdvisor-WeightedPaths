package com.derekjass.sts.weightedpaths.ui.path;

import basemod.BaseMod;
import basemod.interfaces.RenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.seed.ActMapSummary;
import com.derekjass.sts.weightedpaths.seed.SeedOracle;
import com.derekjass.sts.weightedpaths.ui.ModFonts;
import com.derekjass.sts.weightedpaths.ui.ModUiStrings;
import com.derekjass.sts.weightedpaths.ui.config.Config;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;

/**
 * Seed act previews in the upper-right corner, away from the bottom-left weights menu.
 * Shows route counts from the current act through act 3 (e.g. act 1: 1+2+3, act 2: 2+3, act 3: 3).
 */
public class ActPreviewRenderer implements RenderSubscriber {

    private static final Color TITLE_COLOR = new Color(0.95f, 0.85f, 0.45f, 1.0f);
    private static final Color HINT_COLOR = new Color(0.7f, 0.7f, 0.7f, 0.85f);
    private static final Color PLAN_COLOR = new Color(0.55f, 0.95f, 0.55f, 1.0f);

    private static final float MARGIN_RIGHT = 30.0f;
    private static final float MARGIN_TOP = 90.0f;
    private static final float TITLE_LINE_HEIGHT = 24.0f;
    private static final float BODY_LINE_HEIGHT = 18.0f;
    private static final float BLOCK_GAP = 20.0f;

    private static final float ACT_BLOCK_HEIGHT = TITLE_LINE_HEIGHT + BODY_LINE_HEIGHT + 8.0f;

    private static boolean mapRefreshPending = true;

    private ActPreviewRenderer() {
        BaseMod.subscribe(this);
    }

    public static void initialize() {
        new ActPreviewRenderer();
    }

    private static float previewRightX() {
        return Settings.WIDTH - MARGIN_RIGHT;
    }

    @Override
    public void receiveRender(SpriteBatch sb) {
        if (!Config.showActPreview()) {
            return;
        }
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP) {
            mapRefreshPending = true;
            return;
        }
        if (mapRefreshPending) {
            mapRefreshPending = false;
            WeightedPaths.ensureMapRouteReady();
        }
        if (ModFonts.body == null || ModFonts.header == null) {
            ModFonts.initialize();
        }

        int currentAct = AbstractDungeon.actNum;
        float y = Settings.HEIGHT - MARGIN_TOP;
        boolean renderedAny = false;

        for (int act = currentAct; act <= 3; act++) {
            String routeStats = getRouteStatsForAct(act, currentAct);
            if (routeStats.isEmpty()) {
                continue;
            }
            renderActBlock(sb, act, currentAct, routeStats, y);
            y -= ACT_BLOCK_HEIGHT + BLOCK_GAP;
            renderedAny = true;
        }

        if (!renderedAny) {
            renderUnavailable(sb);
            return;
        }

        FontHelper.renderFontRightAligned(
                sb,
                ModFonts.body,
                ModUiStrings.PREVIEW_HINT,
                previewRightX(),
                Math.max(36.0f, y - 6.0f),
                HINT_COLOR
        );
    }

    private static String getRouteStatsForAct(int act, int currentAct) {
        if (act == currentAct && WeightedPaths.hasCurrentActRouteDisplay()) {
            return WeightedPaths.getCurrentActRouteStats();
        }
        if (!SeedOracle.isAvailable()) {
            return "";
        }
        ActMapSummary summary = SeedOracle.getAct(act);
        if (summary != null && summary.hasPlan()) {
            return summary.getRouteStatSummary();
        }
        return "";
    }

    private static void renderUnavailable(SpriteBatch sb) {
        FontHelper.renderFontRightAligned(
                sb,
                ModFonts.body,
                ModUiStrings.PREVIEW_UNAVAILABLE,
                previewRightX(),
                Settings.HEIGHT - 120.0f,
                HINT_COLOR
        );
    }

    private static void renderActBlock(
            SpriteBatch sb,
            int actNumber,
            int currentAct,
            String routeStats,
            float topY) {
        float y = topY;
        String titleFormat = actNumber == currentAct
                ? ModUiStrings.ACT_ROUTE_TITLE
                : ModUiStrings.ACT_PREVIEW_TITLE;
        String title = String.format(titleFormat, actNumber);
        FontHelper.renderFontRightAligned(sb, ModFonts.header, title, previewRightX(), y, TITLE_COLOR);
        y -= TITLE_LINE_HEIGHT;

        FontHelper.renderFontRightAligned(sb, ModFonts.body, routeStats, previewRightX(), y, PLAN_COLOR);
    }
}
