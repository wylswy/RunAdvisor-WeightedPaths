package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.card.data.CardStatEntry;
import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 四层评分器单元测试（对应审查 B3：评分器零测试）。
 * 依据知识库「端口化.md / 设计原则.md」：
 *  - 弱端口补强（layer2）、requiresPort（layer3）、组件一致性主线强化、BLOCK 硬底线
 *  - B1 校准修复：正常卡分不顶格 100
 *  - null 安全（防 NPE 闪退）
 *
 * 注：用轻量 {@link CardInfo} 代替游戏 AbstractCard（游戏卡在纯 JUnit 环境无法初始化）。
 */
public class FourLayerScorerTest {

    // ---------- helpers ----------

    /** 用真实卡 ID 造一个轻量卡信息（cardId + 典型费用）。 */
    private static CardInfo card(String id, int cost) {
        return new CardInfo(id, cost);
    }

    private static CardStatEntry entry(double base, String... tags) {
        CardStatEntry e = new CardStatEntry();
        e.baseScore = (int) base;
        for (String t : tags) {
            e.tags.add(t);
        }
        return e;
    }

    private static CardStatEntry entryRequires(double base, String port, int min, String... tags) {
        CardStatEntry e = entry(base, tags);
        e.requiresPort = port;
        e.requiresMinPoints = min;
        return e;
    }

    private static PortProfile ports(int dmg, int blk, int eng, int weak, int deckSize, boolean aoe, boolean scaling) {
        return new PortProfile(dmg, blk, eng, weak, 0, deckSize, aoe, scaling);
    }

    private static DeckSnapshot deck(PortProfile p, DirectionProfile d) {
        return new DeckSnapshot(p, d, false, false, false, false, false,
                1.0, 1, 1, 0, 0);
    }

    private static DeckSnapshot deckAcrobatics(PortProfile p, DirectionProfile d, int acroCount) {
        return new DeckSnapshot(p, d, acroCount >= 1, false, false, false, false,
                1.0, 1, 1, acroCount, 0);
    }

    private static SituationalContext sit(int act, int hp, int maxHp, boolean early, int lament) {
        return new SituationalContext(0, act, hp, maxHp, lament, lament > 0, early);
    }

    private static PathSymbolCounts runWide(int elite, int rest, int monster) {
        return new PathSymbolCounts(elite, rest, 0, 0, 0, monster);
    }

    private static GlobalRunPlan plan(GlobalRunPlan.RunPhase phase, int act,
                                      PathSymbolCounts runWide, List<String> upcoming,
                                      String next, int untilElite) {
        return new GlobalRunPlan(true, phase, act, 4 - act,
                PathSymbolCounts.EMPTY, runWide, upcoming, next, untilElite);
    }

    private static double score(CardInfo c, CardStatEntry entry, double base,
                                DeckSnapshot deck, SituationalContext sit) {
        return FourLayerScorer.evaluateDetailed(c, entry, base, deck,
                RelicProfile.empty(), sit, null).recommendation.score;
    }

    private static double scoreFull(CardInfo c, CardStatEntry entry, double base,
                                    DeckSnapshot deck, SituationalContext sit, GlobalRunPlan plan) {
        return FourLayerScorer.evaluateDetailed(c, entry, base, deck,
                RelicProfile.empty(), sit, plan).recommendation.score;
    }

    // ---------- null 安全（防闪退） ----------

    @Test
    public void nullCardReturnsCAndZero() {
        FourLayerScorer.DetailedResult r = FourLayerScorer.evaluateDetailed(
                null, entry(50, "attack"), 50, deck(ports(3, 3, 3, 1, 10, true, true),
                        new DirectionProfile(1, 1, 1, 0)),
                RelicProfile.empty(), sit(1, 70, 70, true, 0), null);
        assertEquals(CardGrade.C, r.recommendation.grade);
        assertEquals(0.0, r.recommendation.score, 0.001);
    }

    @Test
    public void nullEntryReturnsCAndZero() {
        FourLayerScorer.DetailedResult r = FourLayerScorer.evaluateDetailed(
                card("Neutralize", 0), null, 50, deck(ports(3, 3, 3, 1, 10, true, true),
                        new DirectionProfile(1, 1, 1, 0)),
                RelicProfile.empty(), sit(1, 70, 70, true, 0), null);
        assertEquals(CardGrade.C, r.recommendation.grade);
        assertEquals(0.0, r.recommendation.score, 0.001);
    }

    // ---------- 弱端口补强（layer2） ----------

    @Test
    public void damageWeakestBoostsAttackCardsAboveNonPortCards() {
        // DAMAGE=1 最弱，攻击卡 serve DAMAGE → mult 1.20；future 卡不 serve 任何端口 → 1.0
        PortProfile p = ports(1, 4, 4, 1, 10, true, true);
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));
        SituationalContext s = sit(1, 70, 70, true, 0);

        double attack = score(card("Neutralize", 0), entry(50, "attack", "weak"), 50, d, s);
        double nonPort = score(card("Defend_Green", 1), entry(50, "future"), 50, d, s);
        assertTrue("弱 DAMAGE 端口应提升攻击卡", attack > nonPort);
    }

    @Test
    public void engineWeakestBoostsEngineCardsAboveAttackCards() {
        // ENGINE=1 最弱（SILENT_ENGINE_BIAS 再 -1 → 0），引擎卡 serve weakest → 1.35
        PortProfile p = ports(4, 4, 1, 1, 10, true, true);
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));
        SituationalContext s = sit(2, 70, 70, false, 0);

        double engineCard = score(card("Acrobatics", 1), entry(50, "draw", "engine"), 50, d, s);
        double attackCard = score(card("Neutralize", 0), entry(50, "attack"), 50, d, s);
        assertTrue("弱 ENGINE 端口应更偏引擎卡", engineCard > attackCard);
    }

    // ---------- B1 校准：正常卡不顶格 100 ----------

    @Test
    public void moderateBaseScoreDoesNotHitCeiling() {
        PortProfile p = ports(3, 3, 3, 1, 10, true, true);
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));
        SituationalContext s = sit(2, 70, 70, false, 0);

        double sc = score(card("Neutralize", 0), entry(75, "attack"), 75, d, s);
        assertTrue("baseScore 75 不应被顶格到 100（B1 修复）", sc < 100.0);
    }

    // ---------- requiresPort（layer3）----------

    @Test
    public void catalystWithoutDamageBaseGetsReduced() {
        // 催化剂 requiresPort=DAMAGE≥2：DAMAGE 不足时 ×0.78 降权
        CardStatEntry cat = entryRequires(60, "DAMAGE", 2, "dot", "scaling", "terminal");

        PortProfile weak = ports(1, 4, 4, 1, 12, true, false);
        DeckSnapshot dWeak = deck(weak, new DirectionProfile(0, 0, 0, 0));
        SituationalContext s = sit(2, 70, 70, false, 0);

        PortProfile strong = ports(3, 4, 4, 1, 12, true, true);
        DeckSnapshot dStrong = deck(strong, new DirectionProfile(1, 1, 1, 0));

        double withBase = score(card("Catalyst", 1), cat, 60, dStrong, s);
        double withoutBase = score(card("Catalyst", 1), cat, 60, dWeak, s);
        assertTrue("DAMAGE 不满足 requiresPort 时催化剂应降权",
                withoutBase < withBase);
    }

    // ---------- 组件一致性：主线强化 + BLOCK 硬底线 ----------

    @Test
    public void dominantAttackDirectionStrengthensAttackButNotBlock() {
        // attack 主线（3 张攻击最多）。牌组端口均衡 → 攻击卡仅靠组件一致性 ×1.10，block 卡不加
        PortProfile p = ports(3, 3, 3, 1, 12, true, true);
        DirectionProfile dir = new DirectionProfile(3, 1, 1, 0); // attack 主线
        DeckSnapshot d = deck(p, dir);
        SituationalContext s = sit(2, 70, 70, false, 0);

        double attack = score(card("Neutralize", 0), entry(50, "attack"), 50, d, s);
        double block = score(card("Defend_Green", 1), entry(50, "block"), 50, d, s);
        assertTrue("attack 主线应强化攻击卡（组件一致性）", attack > block);
        // 验证 BLOCK 硬底线：block 卡不被主线强化（否则此处会追上/超过）
        double delta = attack - block;
        assertTrue("BLOCK 卡不应被方向强化抬高", delta > 0);
    }

    // ---------- 生存危机加分（layer1） ----------

    @Test
    public void lowHpPlusElitePressureBoostsBlock() {
        // HP 20%（0.20<0.30）+ 前方精英压力(remainingRunWide.eliteCount>=3) → block/transition +22
        GlobalRunPlan pressured = plan(GlobalRunPlan.RunPhase.LATE, 3,
                runWide(4, 3, 6), Collections.singletonList("M"), "M", -1);

        PortProfile p = ports(3, 3, 3, 1, 12, true, true);
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));
        SituationalContext lowHp = sit(3, 20, 100, false, 0);
        SituationalContext healthy = sit(3, 90, 100, false, 0);

        double low = scoreFull(card("Defend_Green", 1), entry(50, "block"), 50, d, lowHp, pressured);
        double hi = scoreFull(card("Defend_Green", 1), entry(50, "block"), 50, d, healthy, pressured);
        assertTrue("低血+精英压力应给 block 生存加分", low > hi);
    }

    @Test
    public void needAoeBoostsAoeCards() {
        // 无 aoe + 下一房精英 + upcoming≥3 M → needAoe，aoe 卡 +24
        List<String> upcoming = Arrays.asList("M", "M", "M", "E");
        GlobalRunPlan plan = plan(GlobalRunPlan.RunPhase.EARLY, 1,
                runWide(1, 3, 9), upcoming, "E", 4);

        PortProfile p = ports(3, 3, 3, 1, 12, false, true); // 无 aoe
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));
        SituationalContext s = sit(1, 70, 70, true, 0);

        double aoe = scoreFull(card("Blade Dance", 1), entry(50, "attack", "aoe"), 50, d, s, plan);
        double noAoe = scoreFull(card("Neutralize", 0), entry(50, "attack", "weak"), 50, d, s, plan);
        assertTrue("前方多怪且无 aoe 时应提升 aoe 卡", aoe > noAoe);
    }

    // ---------- 污染 / 低值攻击降权（layer4） ----------

    @Test
    public void pollutionTagReducesScore() {
        PortProfile p = ports(3, 3, 4, 1, 12, true, true);
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));
        SituationalContext s = sit(2, 70, 70, false, 0);

        double clean = score(card("Neutralize", 0), entry(50, "attack"), 50, d, s);
        double polluted = score(card("Neutralize", 0), entry(50, "attack", "pollution"), 50, d, s);
        assertTrue("pollution 卡应降权（×0.55）", polluted < clean);
    }

    // ---------- 杂技重复降权（layer3） ----------

    @Test
    public void secondAcrobaticsIsPenalized() {
        PortProfile p = ports(4, 4, 2, 1, 14, true, true); // ENGINE 最弱，引擎卡天然受偏
        SituationalContext s = sit(2, 70, 70, false, 0);
        // attack 主线（非 draw），避免 draw 方向 boost 覆盖杂技重复惩罚（×0.58）
        DirectionProfile dir = new DirectionProfile(2, 1, 1, 0);

        double zero = score(card("Acrobatics", 1), entry(50, "draw", "engine"), 50,
                deckAcrobatics(p, dir, 0), s);
        double two = score(card("Acrobatics", 1), entry(50, "draw", "engine"), 50,
                deckAcrobatics(p, dir, 2), s);
        assertTrue("已有 2 张杂技后再抓应降权（×0.58）", two < zero);
    }

    // ---------- 刀刃之舞：早期可裸抓（端口化 §5） ----------

    @Test
    public void bladeDanceEarlyAndLowDamageIsBoosted() {
        PortProfile p = ports(2, 4, 4, 1, 10, true, true); // DAMAGE<3
        DeckSnapshot d = deck(p, new DirectionProfile(0, 0, 0, 0));

        GlobalRunPlan early = plan(GlobalRunPlan.RunPhase.EARLY, 1,
                runWide(0, 4, 4), Collections.emptyList(), "", -1);
        GlobalRunPlan late = plan(GlobalRunPlan.RunPhase.LATE, 3,
                runWide(0, 4, 4), Collections.emptyList(), "", -1);

        double inEarly = scoreFull(card("Blade Dance", 1), entry(50, "attack", "aoe", "premiumTransition"),
                50, d, sit(1, 70, 70, true, 0), early);
        double inLate = scoreFull(card("Blade Dance", 1), entry(50, "attack", "aoe", "premiumTransition"),
                50, d, sit(3, 70, 70, false, 0), late);
        assertTrue("一层前期 DAMAGE 不足时刀刃之舞应获 boost", inEarly > inLate);
    }
}
