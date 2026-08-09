package com.derekjass.sts.weightedpaths.creative;

/**
 * 基于 DeepSeek 的 {@link AttitudeAi} 实现。
 * 把「卡的态度」检测到的违背场景打包成提示词，交给 DeepSeek 生成个性化台词。
 * 结果经「游戏世界观黑名单」校验：若 AI 编造了杀戮尖塔里不存在的设定（神/宗教等），
 * 返回 null，由调用方回落到本地模板，避免跑偏内容上屏。
 */
public final class DeepSeekAttitudeAi implements AttitudeAi {

    /**
     * 杀戮尖塔世界观里明显不存在的设定词——AI 若提到即视为跑偏，回落模板。
     * 注意：不加"神/柱"这类，因为游戏里一层 Boss 有"三柱神"等合法外号；
     * 情境错乱（说错层数）靠传入当前情境解决，不靠词表禁。
     */
    private static final String[] FORBIDDEN_LORE = {
            "上帝", "宗教", "天堂", "创世", "造物主", "神话", "佛祖", "真主"
    };

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
        String line = DeepSeekClient.generateLine(apiKey, prompt);
        if (line == null || containsForbiddenLore(line)) {
            return null;
        }
        return line;
    }

    private static boolean containsForbiddenLore(String line) {
        for (String word : FORBIDDEN_LORE) {
            if (line.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
