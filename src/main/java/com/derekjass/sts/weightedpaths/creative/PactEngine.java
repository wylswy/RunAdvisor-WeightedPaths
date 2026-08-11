package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonObject;

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

    /** 契约条件（当前仅保留「以 ≥50% 血量到达下一精英」；攻击牌禁令经实测判定不可生存，已下架）。 */
    public enum Condition {
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
     * @param seedAvailable 种子预览是否可用（决定奖励能否揭示精英）
     */
    public Pact propose(int act, boolean seedAvailable) {
        if (hasActive()) {
            return null;
        }
        Reward reward = seedAvailable ? Reward.REVEAL_NEXT_ELITE : Reward.CONSERVATIVE_ADVICE;
        active = new Pact(Condition.REACH_ELITE_HP_ABOVE_50, reward, act, Status.OFFERED);
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

    /** 幕结束：REACH_ELITE 没遇到精英直接过幕 → 违背。 */
    public int onActEnd() {
        if (active == null || active.status != Status.ACCEPTED) {
            return 0;
        }
        active.status = Status.VIOLATED;
        return FAVOR_VIOLATE;
    }

    /** 导出当前契约状态（供跨 SL 持久化）；无活动契约返回 null。 */
    public JsonObject exportState() {
        if (active == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("condition", active.condition.name());
        o.addProperty("reward", active.reward.name());
        o.addProperty("act", active.acceptedAct);
        o.addProperty("status", active.status.name());
        if (pendingReward != null) {
            o.addProperty("pendingReward", pendingReward.name());
        }
        return o;
    }

    /** 从持久化状态恢复契约；数据非法时静默清空（不崩游戏）。 */
    public void restoreState(JsonObject o) {
        if (o == null) {
            return;
        }
        try {
            Condition c = Condition.valueOf(o.get("condition").getAsString());
            Reward r = Reward.valueOf(o.get("reward").getAsString());
            int act = o.get("act").getAsInt();
            Status s = Status.valueOf(o.get("status").getAsString());
            active = new Pact(c, r, act, s);
            if (o.has("pendingReward")) {
                pendingReward = Reward.valueOf(o.get("pendingReward").getAsString());
            }
        } catch (Exception ignored) {
            active = null;
            pendingReward = null;
        }
    }

    private void complete() {
        pendingReward = active.reward;
        active.status = Status.COMPLETED;
    }
}