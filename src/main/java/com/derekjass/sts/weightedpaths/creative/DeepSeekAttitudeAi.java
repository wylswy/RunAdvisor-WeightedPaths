package com.derekjass.sts.weightedpaths.creative;

/**
 * 基于 DeepSeek 的 {@link AttitudeAi} 实现。
 * 把「卡的态度」检测到的违背场景打包成提示词，交给 DeepSeek 生成个性化台词。
 */
public final class DeepSeekAttitudeAi implements AttitudeAi {

    private final String apiKey;

    public DeepSeekAttitudeAi(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String generateFor(String recommendedId, boolean recommendedSkipAll,
                              String chosenId, boolean skipped, String chosenGrade,
                              String deckContext) {
        String prompt = DeepSeekClient.buildAttitudePrompt(
                recommendedId, recommendedSkipAll, chosenId, skipped, chosenGrade, deckContext);
        return DeepSeekClient.generateLine(apiKey, prompt);
    }
}
