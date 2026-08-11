package com.derekjass.sts.weightedpaths.paths;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 涅奥白嫖硬规则单元测试：验证 hasEliteWithinLament 判定"白嫖窗口内能否碰到精英"。
 * 测纯逻辑重载（接受符号序列），不依赖游戏房间类，脱离游戏实例。
 * 规则：战斗位 = 怪物(M)/精英(E)/事件(?)各计一次；休息(R)/商店($)/宝箱(T)不计。
 * 到第一个精英 E 前已发生的战斗数 &lt; 剩余白嫖次数 → 能白嫖到。
 */
public class NeowLamentEliteRuleTest {

    private static List<String> path(String... symbols) {
        return Arrays.asList(symbols);
    }

    private static boolean canLament(int left, String... symbols) {
        return MapPath.hasEliteWithinLament(left, path(symbols));
    }

    @Test
    public void eliteFirstBattleIsWithinWindow() {
        // E 就是第一个战斗位，白嫖 3 次 → 能白嫖
        assertTrue("首战即精英应能白嫖", canLament(3, "E"));
    }

    @Test
    public void eliteAfterTwoBattlesWithinWindow() {
        // M M E：到精英前 2 战，白嫖 3 次 → 能
        assertTrue("M M E 应能白嫖(3次内)", canLament(3, "M", "M", "E"));
    }

    @Test
    public void eliteAfterThreeBattlesOutsideWindow() {
        // M M M E：到精英前 3 战，白嫖已耗尽 → 不能
        assertFalse("M M M E 不能白嫖(3次已耗尽)", canLament(3, "M", "M", "M", "E"));
    }

    @Test
    public void restAndShopDoNotConsumeWindow() {
        // R $ M E：休息/商店不耗白嫖，到精英前仅 1 战 → 能白嫖
        assertTrue("R $ M E 应能白嫖(休息商店不耗)", canLament(3, "R", "$", "M", "E"));
    }

    @Test
    public void noEliteMeansFalse() {
        assertFalse("无精英不应通过", canLament(3, "M", "M", "M"));
        assertFalse("空路径不应通过", MapPath.hasEliteWithinLament(3, Collections.<String>emptyList()));
    }

    @Test
    public void lamentExhaustedMeansFalse() {
        // 白嫖剩余 0 或负数 → 无白嫖机会
        assertFalse("白嫖剩余0不应通过", canLament(0, "E"));
        assertFalse("白嫖剩余负数不应通过", canLament(-1, "M", "E"));
    }

    @Test
    public void onlyFirstEliteMatters() {
        // 方案B：只看第一个精英。M E E → 第一个精英前1战，能白嫖
        assertTrue("只看第一个精英: M E E 应能白嫖", canLament(3, "M", "E", "E"));
    }

    @Test
    public void eventCountsAsBattle() {
        // ? E：事件也算战斗，到精英前1战 → 能白嫖
        assertTrue("? E 应能白嫖", canLament(3, "?", "E"));
        // ? ? ? E：3个事件耗光 → 不能
        assertFalse("? ? ? E 不能白嫖", canLament(3, "?", "?", "?", "E"));
    }

    @Test
    public void treasureDoesNotConsumeWindow() {
        // T M E：宝箱不耗白嫖，到精英前1战 → 能白嫖
        assertTrue("T M E 应能白嫖(宝箱不耗)", canLament(3, "T", "M", "E"));
    }
}
