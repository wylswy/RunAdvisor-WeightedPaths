package com.derekjass.sts.weightedpaths.ui.path;

import basemod.BaseMod;
import basemod.interfaces.RenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.paths.MapPath;
import com.derekjass.sts.weightedpaths.ui.config.Config;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.ArrayList;
import java.util.List;

public class BestPathRenderer implements RenderSubscriber {

    private static final Color PATH_COLOR = new Color(0.95f, 0.15f, 0.15f, 0.85f);
    private static final Color NEXT_STEP_COLOR = new Color(1.0f, 0.35f, 0.35f, 0.95f);
    private static final float LINE_THICKNESS = 6.0f;
    private static final float NEXT_RING_SIZE = 42.0f;

    private static Texture lineTexture;
    private static boolean mapRefreshPending = true;

    private BestPathRenderer() {
        BaseMod.subscribe(this);
    }

    public static void initialize() {
        if (lineTexture == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            lineTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        new BestPathRenderer();
    }

    @Override
    public void receiveRender(SpriteBatch sb) {
        if (!Config.showMapPath()) {
            return;
        }
        if (!isMapVisible()) {
            mapRefreshPending = true;
            return;
        }
        if (mapRefreshPending) {
            mapRefreshPending = false;
            WeightedPaths.ensureMapRouteReady();
        }

        MapPath bestPath = WeightedPaths.getBestPath();
        if (bestPath == null || bestPath.isEmpty()) {
            return;
        }

        List<MapRoomNode> nodes = buildDrawNodes(bestPath);
        if (nodes.size() < 2) {
            return;
        }

        sb.setColor(PATH_COLOR);
        for (int i = 0; i < nodes.size() - 1; i++) {
            drawLine(sb, nodes.get(i), nodes.get(i + 1), LINE_THICKNESS);
        }

        MapRoomNode nextStep = nodes.get(1);
        drawNextStepRing(sb, nextStep);
    }

    private static List<MapRoomNode> buildDrawNodes(MapPath bestPath) {
        List<MapRoomNode> nodes = new ArrayList<>();
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        if (current != null) {
            nodes.add(current);
        }
        nodes.addAll(bestPath);
        return nodes;
    }

    private static void drawLine(SpriteBatch sb, MapRoomNode from, MapRoomNode to, float thickness) {
        if (!isValidDrawNode(from) || !isValidDrawNode(to)) {
            return;
        }
        float x1 = from.hb.cX;
        float y1 = from.hb.cY;
        float x2 = to.hb.cX;
        float y2 = to.hb.cY;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0f) {
            return;
        }
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        sb.draw(
                lineTexture,
                x1, y1 - thickness / 2.0f,
                0.0f, thickness / 2.0f,
                length, thickness,
                1.0f, 1.0f,
                angle,
                0, 0,
                1, 1,
                false, false
        );
    }

    private static void drawNextStepRing(SpriteBatch sb, MapRoomNode nextStep) {
        if (!isValidDrawNode(nextStep)) {
            return;
        }
        sb.setColor(NEXT_STEP_COLOR);
        float size = NEXT_RING_SIZE;
        float x = nextStep.hb.cX - size / 2.0f;
        float y = nextStep.hb.cY - size / 2.0f;
        sb.draw(lineTexture, x, y, size, size);
    }

    private static boolean isMapVisible() {
        return AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP;
    }

    private static boolean isValidDrawNode(MapRoomNode node) {
        if (node == null || node.hb == null) {
            return false;
        }
        float x = node.hb.cX;
        float y = node.hb.cY;
        if (x < 1.0f || y < 1.0f) {
            return false;
        }
        if (x > Settings.WIDTH + 200.0f || y > Settings.HEIGHT + 200.0f) {
            return false;
        }
        return true;
    }
}