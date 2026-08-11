package com.derekjass.sts.weightedpaths.creative;

import java.util.List;
import java.util.Set;

/**
 * AgentCore（工具调用协议）与 AiRecommendationEngine（UI 消费模型）之间的桥接。
 *
 * <p>职责：把 AgentCore 的结构化决策 {@link AgentCore.Decision} 翻译成
 * 渲染层直接消费的 {@link AiRecommendationEngine.AiRecommendation}，
 * 让 AgentCore 能真正接进游戏推荐链路，同时不改动任何渲染代码。
 *
 * <p>工具语义映射：
 * <ul>
 *   <li>{@link AgentCore.Tool#ADJUST_RECOMMENDATION} → 推荐抓 argument 指定卡</li>
 *   <li>{@link AgentCore.Tool#SKIP_ALL} → 整次跳过</li>
 *   <li>{@link AgentCore.Tool#SET_WARNING} / {@link AgentCore.Tool#DO_NOTHING} → 无效（回落规则兜底）</li>
 * </ul>
 */
public final class AgentBridge {

    private AgentBridge() {
    }

    /**
     * 把 AgentCore 决策翻译成 UI 可消费的 AiRecommendation。
     *
     * @param decision AgentCore 结构化决策
     * @return 对应 AiRecommendation；决策无效或工具不产生推荐动作 → 返回 invalid（调用方回落规则兜底）
     */
    public static AiRecommendationEngine.AiRecommendation toRecommendation(AgentCore.Decision decision) {
        if (decision == null || !decision.valid) {
            return AiRecommendationEngine.AiRecommendation.invalid();
        }
        switch (decision.tool) {
            case ADJUST_RECOMMENDATION:
                // argument 已在 AgentCore.parse 校验为候选集内卡 ID
                return AiRecommendationEngine.AiRecommendation.pick(decision.argument, decision.reason);
            case SKIP_ALL:
                return AiRecommendationEngine.AiRecommendation.skip(decision.reason);
            case SET_WARNING:
            case DO_NOTHING:
            default:
                // 不产生推荐动作：让规则兜底决定推荐哪张/是否跳过
                return AiRecommendationEngine.AiRecommendation.invalid();
        }
    }

    /**
     * 构建 AgentCore 感知状态，供 AI 决策。
     *
     * @param act 当前层数
     * @param hpPercent 当前血量百分比
     * @param situation 牌组/情境摘要（不含层数/血量，它们已由 act/hpPercent 表达；可为空）
     * @param favor 当前好感度（负=记仇，正=友好）
     * @param candidateIds 本次卡奖候选卡 ID
     */
    public static AgentCore.State buildState(int act, int hpPercent, String situation, int favor,
                                             List<String> candidateIds) {
        return new AgentCore.State(act, hpPercent, situation, favor, candidateIds);
    }

    /** 便捷：把候选卡 ID 列表转成合法候选集（供 parse 校验）。 */
    public static Set<String> toIdSet(List<String> ids) {
        return AgentCore.toIdSet(ids);
    }
}
