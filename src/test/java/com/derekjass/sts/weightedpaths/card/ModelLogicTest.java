package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.card.data.CardStatEntry;
import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 纯逻辑模型类的单元测试（无游戏依赖，覆盖评分/路线核心数据结构的方法）：
 *  CardStatEntry（tag/端口归属/requiresPort）、PortProfile（最弱/次弱端口）、
 *  DeckSnapshot（洁牌压力/删牌需求/一层精英就绪）、PathSymbolCounts（符号计数/合并）。
 */
public class ModelLogicTest {

    // ---------- CardStatEntry ----------

    @Test
    public void hasTagIsCaseInsensitive() {
        CardStatEntry e = new CardStatEntry();
        e.tags.add("attack");
        assertTrue(e.hasTag("attack"));
        assertTrue(e.hasTag("ATTACK"));
        assertTrue(e.hasTag("Attack"));
        assertFalse(e.hasTag("block"));
        assertFalse(e.hasTag(null));
    }

    @Test
    public void servesPortDamageForAttackAoeDot() {
        CardStatEntry e = new CardStatEntry();
        for (String tag : new String[]{"attack", "aoe", "dot"}) {
            CardStatEntry c = new CardStatEntry();
            c.tags.add(tag);
            assertTrue("tag=" + tag, c.servesPort(Port.DAMAGE));
        }
    }

    @Test
    public void servesPortBlockOnlyForBlock() {
        CardStatEntry e = new CardStatEntry();
        e.tags.add("block");
        assertTrue(e.servesPort(Port.BLOCK));
        assertFalse(e.servesPort(Port.DAMAGE));
    }

    @Test
    public void servesPortEngineForEngineTags() {
        for (String tag : new String[]{"draw", "energy", "discard", "engine", "retain"}) {
            CardStatEntry c = new CardStatEntry();
            c.tags.add(tag);
            assertTrue("tag=" + tag, c.servesPort(Port.ENGINE));
        }
    }

    @Test
    public void servesPortNullReturnsFalse() {
        assertFalse(new CardStatEntry().servesPort(null));
    }

    @Test
    public void requiredPortParsesValid() {
        CardStatEntry e = new CardStatEntry();
        e.requiresPort = "DAMAGE";
        assertEquals(Port.DAMAGE, e.requiredPort());
        e.requiresPort = "engine";
        assertEquals(Port.ENGINE, e.requiredPort());
    }

    @Test
    public void requiredPortInvalidOrEmptyReturnsNull() {
        CardStatEntry e = new CardStatEntry();
        assertNull(e.requiredPort());
        e.requiresPort = "";
        assertNull(e.requiredPort());
        e.requiresPort = "NONSENSE";
        assertNull(e.requiredPort());
    }

    // ---------- PortProfile ----------

    private static PortProfile pp(int dmg, int blk, int eng) {
        return new PortProfile(dmg, blk, eng, 0, 0, 10, true, true);
    }

    @Test
    public void weakestPortPicksLowest() {
        assertEquals(Port.DAMAGE, pp(1, 4, 4).weakestPort());
        assertEquals(Port.BLOCK, pp(4, 1, 4).weakestPort());
        assertEquals(Port.ENGINE, pp(4, 4, 1).weakestPort());
    }

    @Test
    public void weakestPortTiePrefersDamage() {
        assertEquals(Port.DAMAGE, pp(2, 2, 2).weakestPort());
    }

    @Test
    public void secondWeakestPort() {
        // damage=1 最弱 → 次弱在 block/engine 中更小的那个
        PortProfile p = pp(1, 3, 2);
        assertEquals(Port.ENGINE, p.secondWeakestPort());
        PortProfile p2 = pp(1, 2, 3);
        assertEquals(Port.BLOCK, p2.secondWeakestPort());
    }

    @Test
    public void portPointsMatchesField() {
        PortProfile p = pp(3, 4, 5);
        assertEquals(3, p.portPoints(Port.DAMAGE));
        assertEquals(4, p.portPoints(Port.BLOCK));
        assertEquals(5, p.portPoints(Port.ENGINE));
    }

    // ---------- DeckSnapshot ----------

    private static DeckSnapshot snap(PortProfile p, int strike, int defend, int acro,
                                     boolean hasAcro, boolean hasMasterful) {
        return new DeckSnapshot(p, new DirectionProfile(0, 0, 0, 0),
                hasAcro, false, hasMasterful, false, false, 1.0,
                strike, defend, acro, 0);
    }

    @Test
    public void removalUrgencyScalesWithStrikesAndDeck() {
        // strike=2, deck=10 → urgency=4
        assertEquals(4, snap(pp(3, 3, 3), 2, 2, 0, false, false).removalUrgency());
        // strike=2, deck=18 → 4 + 2 = 6
        PortProfile bigDeck = new PortProfile(3, 3, 3, 0, 0, 18, true, true);
        assertEquals(6, snap(bigDeck, 2, 2, 0, false, false).removalUrgency());
    }

    @Test
    public void removalUrgencyAddsForManyDefendsWithBlock() {
        // strike=1, defend=4, blockPoints>=3 → 2 + 2 = 4
        assertEquals(4, snap(pp(3, 3, 3), 1, 4, 0, false, false).removalUrgency());
    }

    @Test
    public void needsShopRemovalWhenStrikesHigh() {
        assertTrue(snap(pp(3, 3, 3), 2, 2, 0, false, false).needsShopRemoval());
        assertFalse(snap(pp(3, 3, 3), 1, 1, 0, false, false).needsShopRemoval());
    }

    @Test
    public void act1SanLouMaoEliteComboRequiresAllParts() {
        // weak>=1 + 杂技 + 精巧 + deckSize>=8
        PortProfile p = new PortProfile(3, 3, 3, 1, 0, 10, true, true);
        assertTrue(snap(p, 2, 2, 1, true, true).act1SanLouMaoEliteCombo());
        // 缺 weak
        PortProfile noWeak = new PortProfile(3, 3, 3, 0, 0, 10, true, true);
        assertFalse(snap(noWeak, 2, 2, 1, true, true).act1SanLouMaoEliteCombo());
        // 缺精巧
        assertFalse(snap(p, 2, 2, 1, true, false).act1SanLouMaoEliteCombo());
        // deckSize 不足
        PortProfile small = new PortProfile(3, 3, 3, 1, 0, 6, true, true);
        assertFalse(snap(small, 2, 2, 1, true, true).act1SanLouMaoEliteCombo());
    }

    @Test
    public void act1EliteReadyViaCombo() {
        PortProfile p = new PortProfile(3, 3, 3, 1, 0, 10, true, true);
        assertTrue(snap(p, 2, 2, 1, true, true).act1EliteReady());
    }

    @Test
    public void act1EliteReadyViaPortThresholds() {
        // block>=2 && weak>=1, damage>=2, frontline, deckSize>=8
        PortProfile ok = new PortProfile(2, 2, 3, 1, 0, 10, true, true);
        assertTrue(snap(ok, 2, 2, 0, false, false).act1EliteReady());
        // 缺 weak 且 block<3
        PortProfile noWeak = new PortProfile(2, 2, 3, 0, 0, 10, true, true);
        assertFalse(snap(noWeak, 2, 2, 0, false, false).act1EliteReady());
    }

    // ---------- PathSymbolCounts ----------

    @Test
    public void fromSymbolsCountsCorrectly() {
        PathSymbolCounts c = PathSymbolCounts.fromSymbols(
                Arrays.asList("E", "M", "R", "E", "?", "T", "M", "M"));
        assertEquals(2, c.eliteCount);
        assertEquals(3, c.monsterCount);
        assertEquals(1, c.restCount);
        assertEquals(1, c.eventCount);
        assertEquals(1, c.treasureCount);
        assertEquals(0, c.shopCount);
    }

    @Test
    public void fromSymbolsIgnoresNullAndEmpty() {
        PathSymbolCounts c = PathSymbolCounts.fromSymbols(
                Arrays.asList("", null, "E"));
        assertEquals(1, c.eliteCount);
        assertEquals(0, c.monsterCount);
    }

    @Test
    public void plusCombinesCounts() {
        PathSymbolCounts a = PathSymbolCounts.fromSymbols(Arrays.asList("E", "M"));
        PathSymbolCounts b = PathSymbolCounts.fromSymbols(Arrays.asList("E", "E", "R"));
        PathSymbolCounts sum = a.plus(b);
        assertEquals(3, sum.eliteCount);
        assertEquals(1, sum.monsterCount);
        assertEquals(1, sum.restCount);
    }

    @Test
    public void plusNullReturnsThis() {
        PathSymbolCounts a = PathSymbolCounts.fromSymbols(Collections.singletonList("E"));
        assertEquals(a, a.plus(null));
    }
}
