package com.derekjass.sts.weightedpaths.paths;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * SilentRouteValuation 基础权重测试（浅解耦验证）：
 * 通过注入 RouteSimState，验证评分方法在脱离游戏实例时仍可测试。
 */
public class SilentRouteValuationTest {

    /**
     * 构造一个指定血量比例的状态，脱离 AbstractDungeon.player。
     */
    private static RouteSimState stateWithHp(int currentHp, int maxHp) {
        return new RouteSimState(
                currentHp, maxHp,
                99,            // gold
                0,             // neowLamentBattlesLeft
                0,             // upgradeNeedCount
                12,            // deckSize
                2,             // strikeCount
                2,             // defendCount
                false,         // hasAoe
                false,         // act1EliteReady
                5,             // floor
                1,             // estimatedRestAhead
                0,             // act1ElitesOnPath
                RouteRelicFlags.none());
    }

    @Test
    public void lowHpRestWeightIsHigherThanHealthyRest() {
        RouteSimState lowHp = stateWithHp(15, 70);    // 15/70 ≈ 0.21 < 0.35 阈值
        RouteSimState healthy = stateWithHp(55, 70);  // 55/70 ≈ 0.79，健康

        double lowRest = SilentRouteValuation.roomWeight("R", 1, lowHp);
        double healthyRest = SilentRouteValuation.roomWeight("R", 1, healthy);

        // 低血时火堆权重应显著高于健康时（HP_REST_BOOST_MULT = 2.0）
        assertTrue("低血火堆权重应大于健康火堆权重: low=" + lowRest + " healthy=" + healthyRest,
                lowRest > healthyRest);
    }

    @Test
    public void lowHpRestWeightExceedsHealthyRestWeight() {
        RouteSimState lowHp = stateWithHp(15, 70);
        double lowRest = SilentRouteValuation.roomWeight("R", 1, lowHp);
        double healthyRest = SilentRouteValuation.roomWeight("R", 1, stateWithHp(55, 70));
        // 验证 2 倍加成确实生效
        assertTrue("低血火堆权重应约为健康时的2倍",
                lowRest >= healthyRest * 1.8 && lowRest <= healthyRest * 2.2);
    }

    /** 构造指定涅奥剩余战斗次数、且端口未就绪（act1EliteReady=false）的一层状态。 */
    private static RouteSimState stateWithNeow(int neowLeft) {
        return new RouteSimState(
                40, 70,            // 血量 40/70，健康但非满血
                99,                // gold
                neowLeft,          // neowLamentBattlesLeft
                0,                 // upgradeNeedCount
                10,                // deckSize
                3,                 // strikeCount
                2,                 // defendCount
                false,             // hasAoe
                false,             // act1EliteReady=false：端口未就绪，正常应重罚精英
                6,                 // floor
                1,                 // estimatedRestAhead
                0,                 // act1ElitesOnPath
                RouteRelicFlags.none());
    }

    @Test
    public void neowLamentBoostsEliteWeightWhenPortNotReady() {
        // 端口未就绪 + 无涅奥 → 精英被 act1NotReady(×0.12) 重罚
        double eliteNoNeow = SilentRouteValuation.roomWeight("E", 1, stateWithNeow(0));
        // 端口未就绪 + 涅奥活跃 → 白嫖，精英大幅提升
        double eliteWithNeow = SilentRouteValuation.roomWeight("E", 1, stateWithNeow(2));

        // 涅奥活跃时精英权重应远高于不活跃时
        assertTrue("涅奥活跃时精英权重应大于无涅奥时: neow=" + eliteWithNeow + " noneow=" + eliteNoNeow,
                eliteWithNeow > eliteNoNeow);
    }

    @Test
    public void neowLamentGivesEliteMultiplierBoost() {
        // 无涅奥：act1EliteReady=false 触发 act1NotReady ×0.12
        double eliteNoNeow = SilentRouteValuation.roomWeight("E", 1, stateWithNeow(0));
        // 涅奥活跃：×NEOW_LAMENT_ELITE_MULT(2.0)，且跳过 act1NotReady
        double eliteWithNeow = SilentRouteValuation.roomWeight("E", 1, stateWithNeow(1));

        // 涅奥加成应是显著的乘法提升（此处不应低于无涅奥的 1.5 倍）
        assertTrue("涅奥活跃时精英权重应显著提升: with=" + eliteWithNeow + " without=" + eliteNoNeow,
                eliteWithNeow >= eliteNoNeow * 1.5);
    }

    /** 构造端口就绪、血量健康的一层状态（act1EliteReady=true）。 */
    private static RouteSimState stateEliteReadyHealthy() {
        return new RouteSimState(
                55, 70,            // 血量 55/70，健康
                99,                // gold
                0,                 // neowLamentBattlesLeft
                0,                 // upgradeNeedCount
                14,                // deckSize
                3,                 // strikeCount
                3,                 // defendCount
                true,              // hasAoe
                true,              // act1EliteReady=true：端口就绪
                10,                // floor（>8，避免 earlyFloor 干扰）
                1,                 // estimatedRestAhead
                0,                 // act1ElitesOnPath
                RouteRelicFlags.none());
    }

    @Test
    public void act1EliteWeightLowerThanMonsterEvenWhenReady() {
        // 猎手一层战力弱小：即使端口就绪、血量健康，精英权重也应低于怪物（避精英）
        RouteSimState readyHealthy = stateEliteReadyHealthy();
        double elite = SilentRouteValuation.roomWeight("E", 1, readyHealthy);
        double monster = SilentRouteValuation.roomWeight("M", 1, readyHealthy);

        assertTrue("一层就绪时精英权重应低于怪物: elite=" + elite + " monster=" + monster,
                elite < monster);
    }
}
