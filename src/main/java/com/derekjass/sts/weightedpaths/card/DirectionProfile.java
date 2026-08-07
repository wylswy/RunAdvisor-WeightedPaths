package com.derekjass.sts.weightedpaths.card;

/**
 * 卡组"方向"统计：识别当前主攻方向（白夕"组件一致性"）。
 *
 * 方向 = 端口下的子分类，用于判断牌组往哪个方向积累：
 * - attack（攻击高频）：tag `attack`
 * - dot（持续伤）：tag `dot`
 * - draw（抽牌循环）：tag `draw` 或 `discard`
 * - block（格挡循环）：tag `block`
 *
 * 用途：主方向强化（抓同向卡加分），同时 BLOCK 仍作为生存硬底线。
 */
public final class DirectionProfile {

    public final int attackCount;
    public final int dotCount;
    public final int drawCount;
    public final int blockCount;

    public DirectionProfile(int attackCount, int dotCount, int drawCount, int blockCount) {
        this.attackCount = attackCount;
        this.dotCount = dotCount;
        this.drawCount = drawCount;
        this.blockCount = blockCount;
    }

    /**
     * 当前最厚的"主攻方向"（组件一致性主线）。平局时返回 null，避免误判。
     * 主攻方向只考虑 attack / dot / draw——block 是防御，不参与"主攻方向"判定，
     * 靠 layer2 补弱端口（BLOCK < 3 → ×1.35）保证，避免 block 主线时方向强化死分支。
     */
    public String dominantDirection() {
        int max = Math.max(attackCount, Math.max(dotCount, drawCount));
        if (max <= 0) {
            return null;
        }
        int count = 0;
        String best = null;
        if (attackCount == max) {
            count++;
            best = "attack";
        }
        if (dotCount == max) {
            count++;
            best = "dot";
        }
        if (drawCount == max) {
            count++;
            best = "draw";
        }
        // 平局时不确定主线，不强化
        return count == 1 ? best : null;
    }
}
