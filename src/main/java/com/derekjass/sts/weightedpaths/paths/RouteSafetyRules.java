package com.derekjass.sts.weightedpaths.paths;

/**
 * 路线安全硬规则（纯逻辑，不依赖游戏类，可单测）。
 */
public final class RouteSafetyRules {

    /** 低血保命硬规则阈值：血量比例低于此值且地图有火堆时，强制只推荐含火堆的路线。 */
    public static final double LOW_HP_FORCE_REST_RATIO = 0.30;

    private RouteSafetyRules() {
    }

    /**
     * 判断低血保命硬规则是否应生效。
     *
     * @param currentHp 当前血量
     * @param maxHp     最大血量
     * @return 血量比例低于阈值时返回 true
     */
    public static boolean shouldForceRestRoute(int currentHp, int maxHp) {
        return maxHp > 0 && currentHp > 0 && (double) currentHp / maxHp < LOW_HP_FORCE_REST_RATIO;
    }
}