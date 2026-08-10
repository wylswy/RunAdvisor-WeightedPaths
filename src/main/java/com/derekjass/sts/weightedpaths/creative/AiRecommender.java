package com.derekjass.sts.weightedpaths.creative;

/**
 * 「AI 拍板推荐」的生成器接口：给定完整提示词，返回 AI 决定推荐哪张卡的原始输出；失败返回 null。
 * 独立成接口以便测试注入 mock。
 *
 * <p>返回的是「AI 的原始回复文本」，由 {@link AiRecommendationEngine#parse} 负责解析成结构化决策。
 */
public interface AiRecommender {

    /**
     * 生成 AI 的推荐决策原文。
     *
     * @param prompt 已构造好的提示词（含候选卡、牌组摘要、好感度、情境）
     * @return AI 原文；失败/超时/无结果返回 null（调用方回落规则兜底）
     */
    String recommendLine(String prompt);
}
