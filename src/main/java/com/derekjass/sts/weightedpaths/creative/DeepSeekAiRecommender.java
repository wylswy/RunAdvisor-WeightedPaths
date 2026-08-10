package com.derekjass.sts.weightedpaths.creative;

/**
 * 基于 DeepSeek 的 {@link AiRecommender} 实现。
 * 把「AI 拍板推荐」的提示词交给 DeepSeek，取回原文；超时/失败返回 null（调用方回落规则）。
 */
public final class DeepSeekAiRecommender implements AiRecommender {

    private final String apiKey;

    public DeepSeekAiRecommender(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String recommendLine(String prompt) {
        return DeepSeekClient.generateLine(apiKey, prompt);
    }
}
