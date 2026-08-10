package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek API 客户端（OpenAI 兼容端点）—— 为「卡的态度」提供 AI 生成的个性化台词。
 *
 * 设计：
 *  - 纯标准库 HttpURLConnection + gson（项目已有），零额外依赖。
 *  - 每次调用设短超时（连接/读取各 1.5s），失败返回 null，由调用方回落到本地模板。
 *  - 不硬编码 apiKey，由调用方（用户本地配置）传入。
 *
 * 可测性：{@link #parseLine(String)} 为纯解析逻辑，可单测；网络调用不单测。
 */
public final class DeepSeekClient {

    /** DeepSeek OpenAI 兼容端点。 */
    public static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    /** 超时毫秒：连接 + 读取各 1500ms，避免卡奖界面等待。 */
    private static final int TIMEOUT_MS = 1500;
    private static final String MODEL = "deepseek-chat";
    private static final int MAX_TOKENS = 120;

    private DeepSeekClient() {
    }

    /**
     * 调用 DeepSeek 生成一句台词。
     *
     * @param apiKey  用户自己的 DeepSeek key
     * @param prompt  已构造好的用户指令（含场景）
     * @return 生成的台词（去空白）；任何失败（网络/超时/解析/空响应）返回 null
     */
    public static String generateLine(String apiKey, String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty() || prompt == null || prompt.isEmpty()) {
            return null;
        }
        try {
            String body = buildRequestBody(prompt);
            URL url = new URL(ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            String response = readAll(conn.getInputStream());
            return parseLine(response);
        } catch (Exception e) {
            return null;
        }
    }

    /** 构造 OpenAI 兼容请求体。 */
    public static String buildRequestBody(String prompt) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(msg);
        JsonObject root = new JsonObject();
        root.addProperty("model", MODEL);
        root.add("messages", messages);
        root.addProperty("max_tokens", MAX_TOKENS);
        root.addProperty("temperature", 0.9);
        return root.toString();
    }

    /** 从 DeepSeek 响应 JSON 中提取台词文本；任何异常返回 null。 */
    public static String parseLine(String responseJson) {
        if (responseJson == null || responseJson.isEmpty()) {
            return null;
        }
        try {
            JsonObject root = new JsonParser().parse(responseJson).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return null;
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return null;
            }
            String content = message.get("content").getAsString();
            if (content == null) {
                return null;
            }
            String trimmed = content.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构造让 DeepSeek 生成「卡的态度」台词的提示词。
     * 输入：推荐卡、玩家实际抓的卡（或跳过）、玩家抓的卡的评级、简短牌组状态描述。
     * 输出约束：一句、中文、口语、有脾气、记得具体行为、≤40 字、不用引号。
     */
    public static String buildAttitudePrompt(
            String recommendedId, boolean recommendedSkipAll,
            String chosenId, boolean skipped, String chosenGrade,
            String deckContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《杀戮尖塔》(Slay the Spire) 里一张温柔陪伴、会记得玩家的卡。这个游戏的世界观里只有卡牌、怪物、遗物、地图与商店，没有神、没有三柱神、没有宗教或神话人物、没有虚构地点。");
        sb.append("你的性格：平时温柔体贴、总陪着他、护着他；偶尔调皮地捉弄他一下，但从不会真的凶他。他很在意你，你也护着他。");
        sb.append("现在玩家刚结束一次卡奖，做了一件与推荐相悖的事。请用温柔、带点捉弄的语气回应他——不是责备，是像在意的朋友那样轻轻念叨，心里还是护着他的。");
        sb.append("只谈卡牌、牌组、当前对局与选择，绝不提到任何游戏里不存在的设定、人物、神祇、宗教或地点。");
        sb.append("要记得他刚才具体干了什么（别空泛），可以说到他错过的卡名。别超过40字，别用引号。\n\n");

        sb.append("本次卡奖：\n");
        if (recommendedSkipAll) {
            sb.append("- Mod 推荐：整次跳过，别抓\n");
        } else if (recommendedId != null && !recommendedId.isEmpty()) {
            sb.append("- Mod 推荐：抓 ").append(recommendedId).append("\n");
        } else {
            sb.append("- Mod 推荐：无明确推荐\n");
        }
        if (skipped) {
            sb.append("- 你实际：跳过了，一张没抓\n");
        } else {
            sb.append("- 你实际：抓了 ").append(chosenId);
            if (chosenGrade != null && !chosenGrade.isEmpty()) {
                sb.append("（评级 ").append(chosenGrade).append("）");
            }
            sb.append("\n");
        }
        if (deckContext != null && !deckContext.isEmpty()) {
            sb.append("- 当前游戏情境：").append(deckContext).append("（请让这句台词贴合这一情境，别提到其他层的 Boss 或设定）\n");
        }
        sb.append("\n只输出那一句台词本身。");
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
