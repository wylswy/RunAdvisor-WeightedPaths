package com.derekjass.sts.weightedpaths.creative;

/**
 * 「卡的态度」AI 生成器接口。
 * 由 {@link CardAttitudeEngine} 在检测到违背推荐后调用，异步生成个性化台词。
 * 独立成接口以便测试注入 mock。
 */
public interface AttitudeAi {

    /**
     * 生成一句「记得玩家刚才干了什么」的台词。
     *
     * @return 生成的台词；失败/无结果返回 null（调用方回落本地模板）。
     */
    String generateFor(String recommendedId, boolean recommendedSkipAll,
                       String chosenId, boolean skipped, String chosenGrade,
                       String deckContext);
}
