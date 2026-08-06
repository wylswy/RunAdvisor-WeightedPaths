package com.derekjass.sts.weightedpaths.card;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 方向识别（组件一致性）单元测试。
 * 验证 DirectionProfile 的主线判定：最厚方向识别、平局不误判。
 */
public class DirectionProfileTest {

    @Test
    public void attackDominantWhenMostAttackCards() {
        // 攻击卡 3 张，其余少 → 主线应为 attack
        DirectionProfile d = new DirectionProfile(3, 1, 1, 1);
        assertEquals("attack", d.dominantDirection());
    }

    @Test
    public void dotDominantWhenMostDotCards() {
        DirectionProfile d = new DirectionProfile(1, 3, 1, 1);
        assertEquals("dot", d.dominantDirection());
    }

    @Test
    public void drawDominantWhenMostDrawCards() {
        DirectionProfile d = new DirectionProfile(1, 1, 3, 1);
        assertEquals("draw", d.dominantDirection());
    }

    @Test
    public void blockDominantWhenMostBlockCards() {
        DirectionProfile d = new DirectionProfile(1, 1, 1, 3);
        assertEquals("block", d.dominantDirection());
    }

    @Test
    public void tieReturnsNullToAvoidMisjudgment() {
        // attack 和 draw 平局 → 不确定主线，返回 null（不强化，避免误判）
        DirectionProfile d = new DirectionProfile(2, 1, 2, 1);
        assertNull(d.dominantDirection());
    }

    @Test
    public void emptyReturnsNull() {
        DirectionProfile d = new DirectionProfile(0, 0, 0, 0);
        assertNull(d.dominantDirection());
    }
}
