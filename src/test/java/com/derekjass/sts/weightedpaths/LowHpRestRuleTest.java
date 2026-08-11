package com.derekjass.sts.weightedpaths;

import com.derekjass.sts.weightedpaths.paths.RouteSafetyRules;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 低血保命硬规则判断的单元测试（纯逻辑，不依赖游戏实例）。
 */
public class LowHpRestRuleTest {

    @Test
    public void hpBelowThirtyPercentForcesRest() {
        // 15/70 ≈ 0.214 < 0.30 → 应强制
        assertTrue("15/70 应触发强制火堆", RouteSafetyRules.shouldForceRestRoute(15, 70));
    }

    @Test
    public void hpAtThirtyPercentDoesNotForce() {
        // 21/70 = 0.30，等于阈值 → 不应强制（严格小于）
        assertFalse("21/70 等于阈值不应触发", RouteSafetyRules.shouldForceRestRoute(21, 70));
    }

    @Test
    public void healthyHpDoesNotForce() {
        assertFalse("55/70 健康不应触发", RouteSafetyRules.shouldForceRestRoute(55, 70));
    }

    @Test
    public void zeroHpEdgeCase() {
        // 死亡边缘（currentHp=0）不应触发强制（避免在已死亡状态误判）
        assertFalse("currentHp=0 不应触发", RouteSafetyRules.shouldForceRestRoute(0, 70));
    }

    @Test
    public void zeroMaxHpEdgeCase() {
        // maxHp=0 非法状态，不应触发
        assertFalse("maxHp=0 不应触发", RouteSafetyRules.shouldForceRestRoute(10, 0));
    }
}
