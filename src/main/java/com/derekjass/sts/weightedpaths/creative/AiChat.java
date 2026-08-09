package com.derekjass.sts.weightedpaths.creative;

/**
 * 聊天回复生成器：给定完整提示词，返回一句卡的话；失败返回 null。
 * 独立接口以便测试注入 mock。
 */
public interface AiChat {

    /** 生成一句聊天回复；失败/无结果返回 null（调用方回落或静默）。 */
    String reply(String fullPrompt);
}
