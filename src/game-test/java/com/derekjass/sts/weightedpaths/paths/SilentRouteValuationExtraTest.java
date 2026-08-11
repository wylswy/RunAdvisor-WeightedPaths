package com.derekjass.sts.weightedpaths.paths;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * SilentRouteValuation 补充测试（覆盖现有测试未覆盖的关键路线动态逻辑）：
 *  - act1NotReady：一层端口未就绪 + 无涅奥 → 精英 ×0.12 重罚
 *  - 二层无 AOE：怪物降权
 *  - 低血避怪：怪物 ×0.70
 *  - 商店：高金币 ×1.5、删牌需求加成
 *  - 火堆：需敲牌 + 血线良好 ×1.45
 *  - 二层低血精英惩罚
 */
public class SilentRouteValuationExtraTest {

    private static RouteSimState state(int hp, int maxHp, int gold, int upgradeNeed,
                                       int deckSize, int strike, int defend,
                                       boolean aoe, boolean ready,
                                       int floor, int restAhead, int elitesOnPath) {
        return new RouteSimState(hp, maxHp, gold, 0, upgradeNeed, deckSize,
                strike, defend, aoe, ready, floor, restAhead, elitesOnPath,
                RouteRelicFlags.none());
    }

    @Test
    public void act1NotReadyPenalizesEliteHard() {
        // 一层端口未就绪(ready=false) + 无涅奥 → 精英 ×0.12；就绪高血 → ×0.75
        RouteSimState notReady = state(60, 70, 99, 0, 12, 2, 2,
                true, false, 10, 1, 0);
        RouteSimState ready = state(60, 70, 99, 0, 12, 2, 2,
                true, true, 10, 1, 0);

        double eliteNotReady = SilentRouteValuation.roomWeight("E", 1, notReady);
        double eliteReady = SilentRouteValuation.roomWeight("E", 1, ready);
        assertTrue("一层端口未就绪时精英应被重罚(×0.12): notReady=" + eliteNotReady
                        + " ready=" + eliteReady,
                eliteNotReady < eliteReady * 0.5);
    }

    @Test
    public void act2NoAoeReducesMonsterWeight() {
        // 二层无 AOE：怪物 ×0.5
        RouteSimState noAoe = state(50, 70, 99, 0, 12, 2, 2,
                false, true, 14, 1, 0);
        RouteSimState withAoe = state(50, 70, 99, 0, 12, 2, 2,
                true, true, 14, 1, 0);

        double mNoAoe = SilentRouteValuation.roomWeight("M", 2, noAoe);
        double mWithAoe = SilentRouteValuation.roomWeight("M", 2, withAoe);
        assertTrue("二层无 AOE 时怪物权重应降(×0.5): noAoe=" + mNoAoe + " withAoe=" + mWithAoe,
                mNoAoe < mWithAoe * 0.7);
    }

    @Test
    public void lowHpReducesMonsterWeight() {
        // 低血(<0.40) 怪物 ×0.70 避战
        RouteSimState lowHp = state(20, 70, 99, 0, 12, 2, 2,
                true, true, 10, 1, 0);   // 0.29
        RouteSimState healthy = state(55, 70, 99, 0, 12, 2, 2,
                true, true, 10, 1, 0);    // 0.79

        double mLow = SilentRouteValuation.roomWeight("M", 1, lowHp);
        double mHealthy = SilentRouteValuation.roomWeight("M", 1, healthy);
        assertTrue("低血怪物权重应降(×0.70): low=" + mLow + " healthy=" + mHealthy,
                mLow < mHealthy);
    }

    @Test
    public void highGoldBoostsShopWeight() {
        // 金币>300：商店 ×1.5
        RouteSimState rich = state(50, 70, 400, 0, 10, 1, 1,
                true, true, 10, 1, 0);
        RouteSimState poor = state(50, 70, 100, 0, 10, 1, 1,
                true, true, 10, 1, 0);

        double shopRich = SilentRouteValuation.roomWeight("$", 1, rich);
        double shopPoor = SilentRouteValuation.roomWeight("$", 1, poor);
        assertTrue("金币>300 时商店应 ×1.5: rich=" + shopRich + " poor=" + shopPoor,
                shopRich > shopPoor);
    }

    @Test
    public void shopRemovalNeedBoostsShopWeight() {
        // 删牌需求 + 金币≥75 → 商店 ×1.45
        RouteSimState needRemoveRich = state(50, 70, 100, 0, 12, 2, 2,
                true, true, 10, 1, 0);   // strike>=2 → needsShopRemoval
        RouteSimState needRemovePoor = state(50, 70, 30, 0, 12, 2, 2,
                true, true, 10, 1, 0);   // 金币不足，无加成

        double rich = SilentRouteValuation.roomWeight("$", 1, needRemoveRich);
        double poor = SilentRouteValuation.roomWeight("$", 1, needRemovePoor);
        assertTrue("有删牌需求且金币足时应提商店: rich=" + rich + " poor=" + poor,
                rich > poor);
    }

    @Test
    public void upgradeNeedBoostsRestWeight() {
        // 需敲牌 + 血线≥0.45 + act<=2 → 火堆 ×1.45
        RouteSimState needUpgrade = state(50, 70, 99, 1, 12, 2, 2,
                true, true, 10, 1, 0);   // upgradeNeed=1, hp 0.71, ready
        RouteSimState noUpgrade = state(50, 70, 99, 0, 12, 2, 2,
                true, true, 10, 1, 0);

        double restUpgrade = SilentRouteValuation.roomWeight("R", 1, needUpgrade);
        double restNo = SilentRouteValuation.roomWeight("R", 1, noUpgrade);
        assertTrue("需敲牌且血线好时火堆应加成: upgrade=" + restUpgrade + " no=" + restNo,
                restUpgrade > restNo);
    }

    @Test
    public void act2LowHpPenalizesElite() {
        // 二层低血(<0.55) 精英 ×0.45
        RouteSimState lowHp = state(30, 70, 99, 0, 12, 2, 2,
                true, true, 20, 1, 0);   // 0.43
        RouteSimState healthy = state(60, 70, 99, 0, 12, 2, 2,
                true, true, 20, 1, 0);    // 0.86

        double eLow = SilentRouteValuation.roomWeight("E", 2, lowHp);
        double eHealthy = SilentRouteValuation.roomWeight("E", 2, healthy);
        assertTrue("二层低血精英应降权(×0.45): low=" + eLow + " healthy=" + eHealthy,
                eLow < eHealthy);
    }
}
