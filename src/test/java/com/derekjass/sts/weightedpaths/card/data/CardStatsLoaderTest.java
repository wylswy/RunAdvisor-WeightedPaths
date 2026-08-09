package com.derekjass.sts.weightedpaths.card.data;

import com.derekjass.sts.weightedpaths.card.Port;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 卡牌数据地基完整性测试（验证 silent_cards_a20.json 全量加载与关键配置）。
 * 依据知识库「端口化.md」：三端口标签体系、requiresPort 依赖、premiumTransition 优质过渡。
 * 全 75 卡数据合法性 + 关键卡配置锚点（Catalyst/NoxiousFumes/Tactician/BladeDance/Acrobatics）。
 */
public class CardStatsLoaderTest {

    private static Map<String, CardStatEntry> loadAll() throws Exception {
        CardStatsLoader.initialize();
        assertTrue("卡牌数据应成功加载", CardStatsLoader.isLoaded());
        Field f = CardStatsLoader.class.getDeclaredField("cards");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CardStatEntry> map = (Map<String, CardStatEntry>) f.get(null);
        return map;
    }

    @Test
    public void loadsAll75SilentCards() throws Exception {
        assertEquals("猎手共有 75 张卡（对账台账：75/75 ID 全对）", 75, loadAll().size());
    }

    @Test
    public void everyCardBaseScoreWithinRange() throws Exception {
        for (CardStatEntry e : loadAll().values()) {
            assertTrue("baseScore 应在 0-100: " + e.baseScore,
                    e.baseScore >= 0 && e.baseScore <= 100);
        }
    }

    @Test
    public void everyCardHasTagsAndValidRequiredPort() throws Exception {
        for (Map.Entry<String, CardStatEntry> en : loadAll().entrySet()) {
            assertFalse("卡 " + en.getKey() + " 缺少 tags", en.getValue().tags.isEmpty());
            if (!en.getValue().requiresPort.isEmpty()) {
                assertNotNull("卡 " + en.getKey() + " requiresPort 非法",
                        en.getValue().requiredPort());
            }
        }
    }

    @Test
    public void catalystRequiresDamageBase() {
        CardStatEntry c = CardStatsLoader.get("Catalyst");
        assertNotNull(c);
        assertEquals("催化剂需已有伤害端口基础", Port.DAMAGE, c.requiredPort());
        assertEquals(2, c.requiresMinPoints);
        assertTrue(c.hasTag("dot"));
    }

    @Test
    public void noxiousFumesIsDamageStarterNoRequirement() {
        // 毒雾 = 伤害端口起点，第一张 dot 不惩罚 → 无 requiresPort
        CardStatEntry c = CardStatsLoader.get("Noxious Fumes");
        assertNotNull(c);
        assertTrue(c.hasTag("dot"));
        assertTrue(c.hasTag("scaling"));
        assertTrue("毒雾无 requiresPort（伤害起点）", c.requiresPort.isEmpty());
    }

    @Test
    public void tacticianRequiresEngineBase() {
        CardStatEntry c = CardStatsLoader.get("Tactician");
        assertNotNull(c);
        assertEquals("战术需已有运转端口基础", Port.ENGINE, c.requiredPort());
        assertEquals(2, c.requiresMinPoints);
    }

    @Test
    public void bladeDanceIsPremiumTransitionNoRequirement() {
        // 刀刃之舞 = 优质 DAMAGE 过渡，可裸抓 → 无 requiresPort
        CardStatEntry c = CardStatsLoader.get("Blade Dance");
        assertNotNull(c);
        assertTrue(c.hasTag("premiumTransition"));
        assertTrue("刀刃之舞可裸抓（无端口前置）", c.requiresPort.isEmpty());
        assertTrue(c.servesPort(Port.DAMAGE));
    }

    @Test
    public void acrobaticsIsAct1EngineAndServesEngine() {
        CardStatEntry c = CardStatsLoader.get("Acrobatics");
        assertNotNull(c);
        assertTrue("杂技是一层可抓运转", c.hasTag("act1Engine"));
        assertTrue(c.servesPort(Port.ENGINE));
    }

    @Test
    public void unknownCardIdReturnsNull() {
        assertNull(CardStatsLoader.get("Nonexistent Card"));
    }
}
