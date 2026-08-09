package com.derekjass.sts.weightedpaths;

import com.derekjass.sts.weightedpaths.card.CardGrade;
import com.derekjass.sts.weightedpaths.paths.RouteRelicFlags;
import com.derekjass.sts.weightedpaths.paths.RouteSimState;
import com.derekjass.sts.weightedpaths.paths.SilentRouteValuation;
import com.derekjass.sts.weightedpaths.seed.SeedOracle;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 验证用例的 JUnit 断言（审查 #9：把 _验证用例/ 文档里的数字变成可执行断言）。
 * 覆盖：seed 偏移、房间基础权重、卡牌等级边界。
 * 精英率 ×1.6 在游戏反编译层，无法在纯 JUnit 环境断言，见文档。
 */
public class VerificationCasesTest {

    // ---- seed 偏移（反射测 private mapRngSeedForAct）----
    @Test
    public void seedOffsetsArePlus1Plus200Plus600() throws Exception {
        Method m = SeedOracle.class.getDeclaredMethod("mapRngSeedForAct", int.class, long.class);
        m.setAccessible(true);
        long seed = 12345L;
        assertEquals("Act1 偏移应为 +1", seed + 1, ((Long) m.invoke(null, 1, seed)).longValue());
        assertEquals("Act2 偏移应为 +200", seed + 200, ((Long) m.invoke(null, 2, seed)).longValue());
        assertEquals("Act3 偏移应为 +600(非+400)", seed + 600, ((Long) m.invoke(null, 3, seed)).longValue());
    }

    // ---- Act1 房间基础权重（无状态修正）----
    private static RouteSimState neutralState() {
        return new RouteSimState(
                70, 70, 99, 0, 0, 12, 1, 1, false, false, 1, 0, 0, RouteRelicFlags.none());
    }

    @Test
    public void act1BaseWeightsMatchDocumentation() {
        RouteSimState state = neutralState();
        // 用 roomWeightDetailed().baseWeight 取纯基础分（不含动态修正/menuMult）
        // 一层权重（用户 2026-08-09 定稿）：火堆≈商店 > 小怪 > 事件 > 精英
        assertClose("精英 E 应≈3.78", 3.78, baseWeight("E", state));
        assertClose("怪物 M 应≈7.98", 7.98, baseWeight("M", state));
        assertClose("休息 R 应≈10.85", 10.85, baseWeight("R", state));
        assertClose("商店 $ 应≈10.00", 10.00, baseWeight("$", state));
        assertClose("事件 ? 应≈6.00", 6.00, baseWeight("?", state));
        assertClose("宝箱 T 应=4.00", 4.00, baseWeight("T", state));
    }

    private static double baseWeight(String symbol, RouteSimState state) {
        return SilentRouteValuation.roomWeightDetailed(symbol, 1, state).baseWeight;
    }

    // ---- 卡牌等级边界（CardGrade.fromScore）----
    @Test
    public void cardGradeBoundaries() {
        assertEquals("75 应为 S", CardGrade.S, CardGrade.fromScore(75.0));
        assertEquals("74.99 应为 A", CardGrade.A, CardGrade.fromScore(74.99));
        assertEquals("60 应为 A", CardGrade.A, CardGrade.fromScore(60.0));
        assertEquals("59.99 应为 B", CardGrade.B, CardGrade.fromScore(59.99));
        assertEquals("45 应为 B", CardGrade.B, CardGrade.fromScore(45.0));
        assertEquals("44.99 应为 C", CardGrade.C, CardGrade.fromScore(44.99));
        assertEquals("0 应为 C", CardGrade.C, CardGrade.fromScore(0.0));
    }

    private static void assertClose(String msg, double expected, double actual) {
        assertTrue(msg + " 期望≈" + expected + " 实际=" + actual,
                Math.abs(expected - actual) < 0.01);
    }
}
