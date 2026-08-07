package com.derekjass.sts.weightedpaths.ui.menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.ui.ClickableUIElement;

public class WeightSelector extends ClickableUIElement {

    private final boolean increase;
    private final String nodeType;

    WeightSelector(Texture texture, float x, float y, String nodeType, boolean increase) {
        super(texture, x, y);
        this.increase = increase;
        this.nodeType = nodeType;
    }

    @Override
    protected void onClick() {
        double inc = isShiftPressed() ? 1.0 : 0.1;
        double next = WeightedPaths.weights.get(nodeType) + (increase ? inc : -inc);
        // U1 修复：权重上下界，防止调到负数/离谱值产生负路线权重
        WeightedPaths.weights.put(nodeType, Math.max(0.1, Math.min(10.0, next)));
        WeightedPaths.refreshPathValues();
    }

    public static boolean isShiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
}
