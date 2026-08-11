package com.derekjass.sts.weightedpaths.creative;

/**
 * 契约/赌约引擎 —— 让「卡」提出有后果的新决策（原版不存在的选择空间）。
 *
 * <p>机制（机制式，非许愿式）：
 * <ul>
 *   <li>卡在某幕开始时提出一份契约（条件 + 奖励），玩家接受或拒绝；</li>
 *   <li>接受后，游戏事件（抓攻击牌 / 到达精英 / 幕结束）驱动状态机：
 *       达成 → 奖励 + 好感度↑；违背 → 好感度↓；接受后直接过幕未完成 → 视为违背；</li>
 *   <li>纯逻辑、确定性、可单测；与 {@link CardMoodEngine} 的联动由调用方执行
 *       （本类只返回好感度增量，不碰静态状态）。</li>
 * </ul>
 *
 * <p>好感度约定：完成 +2，违背 -2，拒绝/无契约 0。调用方负责把增量应用到好感度。
 */
public final class PactEngine {

    /** 契约条件。 */
    public enum Condition {
        /** 本幕内不再抓攻击牌（抓到即违背）。 */
        NO_ATTACK_CARDS_THIS_ACT,
        /** 以 ≥50% 血量到达下一精英。 */
        REACH_ELITE_HP_ABOVE_50
    }

    /** 契约奖励。 */
    public enum Reward {
        /** 提前揭示下一精英位置（依赖种子预览；无种子时调用方降级为提示）。 */
        REVEAL_NEXT_ELITE,
        /** 后续建议转向保守（由策略层消费）。 */
        CONSERVATIVE_ADVICE
    }

    /** 契约状态。 */
    public enum Status { OFFERED, ACCEPTED, COMPLETED, VIOLATED }

    /** 一份契约（纯数据；status 由引擎驱动）。 */
    public static final class Pact {
        public final Condition condition;
        public final Reward reward;
        public final int acceptedAct;
        public Status status;

        private Pact(Condition condition, Reward reward, int acceptedAct, Status status) {
            this.condition = condition;
            this.reward = reward;
            this.acceptedAct = acceptedAct;
            this.status = status;
        }
    }

    /** 完成契约的好感度增量。 */
    public static final int FAVOR_COMPLETE = 2;
    /** 违背契约的好感度增量。 */
    public static final int FAVOR_VIOLATE = -2;

    private Pact active;
    private Reward pendingReward;

    /** 当前契约（可为 null）。 */
    public Pact current() {
        return active;
    }

    /** 是否存在未决（待接受/已接受）契约。 */
    public boolean hasActive() {
        return active != null && (active.status == Status.OFFERED || active.status == Status.ACCEPTED);
    }

    /** 待发放的奖励（完成契约时设置；调用方用 {@link #consumeReward()} 取走一次）。 */
    public Reward pendingReward() {
        return pendingReward;
    }

    /** 取走奖励（只发一次）。 */
    public Reward consumeReward() {
        Reward r = pendingReward;
        pendingReward = null;
        return r;
    }

    /**
     * 提出一份契约；已有未决契约时返回 null（一次只谈一份）。
     *
     * @param act 当前幕（1 起）
     * @param deckNeedsDiscipline 牌组是否需要约束（如缺防/贪攻 → 提「不抓攻击牌」）
     * @param seedAvailable 种子预览是否可用（决定奖励能否揭示精英）
     */
    public Pact propose(int act, boolean deckNeedsDiscipline, boolean seedAvailable) {
        if (hasActive()) {
            return null;
        }
        Condition condition;
        if (act <= 1 && deckNeedsDiscipline) {
            condition = Condition.NO_ATTACK_CARDS_THIS_ACT;
        } else {
            condition = Condition.REACH_ELITE_HP_ABOVE_50;
        }
        Reward reward = seedAvailable ? Reward.REVEAL_NEXT_ELITE : Reward.CONSERVATIVE_ADVICE;
        active = new Pact(condition, reward, act, Status.OFFERED);
        return active;
    }

    /** 接受契约（好感度不变）。 */
    public void accept() {
        if (active != null && active.status == Status.OFFERED) {
            active.status = Status.ACCEPTED;
        }
    }

    /** 拒绝契约（好感度不变；契约作废）。 */
    public void decline() {
        if (active != null && active.status == Status.OFFERED) {
            active = null;
        }
    }

    /** 玩家抓了一张卡（isAttack=攻击牌）。违背返回 -2，无影响返回 0。 */
    public int onCardPicked(boolean isAttack) {
        if (active == null || active.status != Status.ACCEPTED) {
            return 0;
        }
        if (active.condition == Condition.NO_ATTACK_CARDS_THIS_ACT && isAttack) {
            active.status = Status.VIOLATED;
            return FAVOR_VIOLATE;
        }
        return 0;
    }

    /** 到达精英（仅 REACH_ELITE_HP_ABOVE_50 契约使用）。达标 → 完成 +2，未达标 → 违背 -2。 */
    public int onEliteReached(int hpPercent) {
        if (active == null || active.status != Status.ACCEPTED) {
            return 0;
        }
        if (active.condition != Condition.REACH_ELITE_HP_ABOVE_50) {
            return 0;
        }
        if (hpPercent >= 50) {
            complete();
            return FAVOR_COMPLETE;
        }
        active.status = Status.VIOLATED;
        return FAVOR_VIOLATE;
    }

    /** 幕结束：NO_ATTACK 未违背 → 完成；REACH_ELITE 没遇到精英直接过幕 → 违背。 */
    public int onActEnd() {
        if (active == null || active.status != Status.ACCEPTED) {
            return 0;
        }
        if (active.condition == Condition.NO_ATTACK_CARDS_THIS_ACT) {
            complete();
            return FAVOR_COMPLETE;
        }
        active.status = Status.VIOLATED;
        return FAVOR_VIOLATE;
    }

    private void complete() {
        pendingReward = active.reward;
        active.status = Status.COMPLETED;
    }
}