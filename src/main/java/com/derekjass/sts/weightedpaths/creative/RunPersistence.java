package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Run 状态持久化 —— 让「卡」在玩家 SL（强杀重进同一局）后还能记得对话与好感度。
 *
 * <p>核心思想：SL 是同一局重进（seed 指纹不变），新开一局是全新 seed。
 * 把上次的 run 指纹(seed + seedSourceTimestamp)、聊天记录、好感度落盘到
 * {@code ~/RunAdvisorLogs/run_state.json}，重进时对比指纹即可分辨是 SL 还是新局。
 *
 * <p>纯逻辑 + 文件 IO，可单测（用临时目录测文件读写；指纹判断不依赖 IO）。
 */
public final class RunPersistence {

    /** 存储文件名（与 RunAdvisorLogger 共用 ~/RunAdvisorLogs 目录）。 */
    public static final String FILE_NAME = "run_state.json";

    /** 一条聊天消息的持久化结构。 */
    public static final class PersistedMessage {
        public final ChatBoxCore.Sender sender;
        public final String text;

        public PersistedMessage(ChatBoxCore.Sender sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }

    private static String customDir = ""; // 测试可注入临时目录

    private RunPersistence() {
    }

    /** 仅供测试注入存储目录（空串=用默认 user.home/RunAdvisorLogs）。 */
    public static void setCustomDirForTest(String dir) {
        customDir = dir == null ? "" : dir;
    }

    private static File stateFile() {
        String dir = customDir;
        if (dir == null || dir.isEmpty()) {
            dir = System.getProperty("user.home") + File.separator + "RunAdvisorLogs";
        }
        return new File(dir, FILE_NAME);
    }

    /**
     * 保存当前 run 指纹 + 聊天记录 + 好感度。seed 为 null 时视为无 run（不覆盖已有指纹）。
     */
    public static void saveCurrentRun(Long seed, long seedSourceTimestamp,
                                      List<ChatBoxCore.ChatMessage> messages, int favor) {
        if (seed == null) {
            return;
        }
        try {
            File dir = stateFile().getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            JsonObject root = new JsonObject();
            root.addProperty("seed", seed);
            root.addProperty("seedSourceTimestamp", seedSourceTimestamp);
            root.addProperty("favor", favor);
            JsonArray arr = new JsonArray();
            if (messages != null) {
                for (ChatBoxCore.ChatMessage m : messages) {
                    if (m == null) {
                        continue;
                    }
                    JsonObject jo = new JsonObject();
                    jo.addProperty("sender", m.sender == ChatBoxCore.Sender.CARD ? "CARD" : "PLAYER");
                    jo.addProperty("text", m.text);
                    arr.add(jo);
                }
            }
            root.add("messages", arr);
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(stateFile()), StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
        } catch (Exception ignored) {
            // 落盘失败不致命：下次 SL 只是恢复不了对话，不崩游戏
        }
    }

    /**
     * 判断给定指纹是否与上次保存的是同一局（SL 重进）。
     *
     * @return true = 同一局（上次存过且 seed 匹配）；false = 新局或未存过
     */
    public static boolean isSameRun(Long seed, long seedSourceTimestamp) {
        if (seed == null) {
            return false;
        }
        JsonObject root = readState();
        if (root == null || !root.has("seed")) {
            return false;
        }
        Long savedSeed = safeLong(root.get("seed"));
        long savedTs = root.has("seedSourceTimestamp")
                ? safeLong(root.get("seedSourceTimestamp"))
                : -1L;
        return savedSeed != null && savedSeed.equals(seed) && savedTs == seedSourceTimestamp;
    }

    /**
     * 恢复上次保存的聊天记录与好感度。
     *
     * @return 解析出的消息列表（可能为空）；null 表示无可恢复状态
     */
    public static List<PersistedMessage> loadMessages() {
        JsonObject root = readState();
        if (root == null || !root.has("messages")) {
            return new ArrayList<>();
        }
        List<PersistedMessage> result = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("messages")) {
            if (e == null || !e.isJsonObject()) {
                continue;
            }
            JsonObject jo = e.getAsJsonObject();
            String sender = jo.has("sender") ? jo.get("sender").getAsString() : "PLAYER";
            String text = jo.has("text") ? jo.get("text").getAsString() : "";
            ChatBoxCore.Sender s = "CARD".equals(sender) ? ChatBoxCore.Sender.CARD : ChatBoxCore.Sender.PLAYER;
            result.add(new PersistedMessage(s, text));
        }
        return result;
    }

    /** 恢复上次保存的好感度。无可恢复/无字段返回 0。 */
    public static int loadFavor() {
        JsonObject root = readState();
        if (root == null || !root.has("favor")) {
            return 0;
        }
        return (int) safeLong(root.get("favor"));
    }

    /** 清空状态文件（新局开始，放弃旧对话）。 */
    public static void clear() {
        File f = stateFile();
        if (f.exists()) {
            f.delete();
        }
    }

    private static JsonObject readState() {
        File f = stateFile();
        if (!f.exists()) {
            return null;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            if (sb.length() == 0) {
                return null;
            }
            JsonElement el = new JsonParser().parse(sb.toString());
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long safeLong(JsonElement e) {
        try {
            return e.getAsLong();
        } catch (Exception ex) {
            return 0L;
        }
    }
}
