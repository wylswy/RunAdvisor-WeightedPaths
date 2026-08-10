package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 「长期陪伴」跨局关系档案持久化 —— 让卡跨局记得玩家、越玩越熟。
 *
 * <p>与 {@link RunPersistence} 不同：run_state.json 记录的是「某一局」的状态（SL 用），
 * 新局会清空；而这里的 player_relation.json 是「你俩长期的关系」，新局永远不清，
 * 只追加累积——这才是"长期陪伴"。
 *
 * <p>纯文件 IO，可单测（用临时目录）。逻辑层见 {@link PlayerRelation}。
 */
public final class RelationPersistence {

    /** 跨局关系档案文件名（独立于 run_state.json，永不因新局清零）。 */
    public static final String FILE_NAME = "player_relation.json";

    /** 一份跨局关系档案的完整数据（与 JSON 字段一一对应）。 */
    public static final class RelationData {
        public int bonding;          // 羁绊值：玩得越多、聊得越多越高
        public int totalRuns;        // 累计对局数
        public int totalVictories;   // 累计胜利局数
        public int maxFloorReached;  // 历史最高到达层数
        public int lastTierIndex;    // 上次结算时的关系阶段（用于识别"刚升级"）
        public boolean pendingUpgrade; // 刚升过级、还没在新局开场"显摆"过
        public LastRunSummary lastRunSummary; // 卡真正记得的"上把发生的事"
    }

    /** 卡对"上把"的真实记忆（长期陪伴的核心：真记得，不假装）。 */
    public static final class LastRunSummary {
        public int floorReached;         // 上把打到第几层
        public boolean victory;          // 上把赢没赢
        public java.util.List<String> pickedCardIds = new java.util.ArrayList<>(); // 上把抓的牌
        public java.util.List<String> chatHighlights = new java.util.ArrayList<>(); // 上把玩家说过的话
    }

    private static String customDir = ""; // 测试可注入临时目录

    private RelationPersistence() {
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
     * 保存跨局关系档案。写入失败不致命（下次结算重写）。
     */
    public static void save(RelationData data) {
        if (data == null) {
            return;
        }
        try {
            File dir = stateFile().getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            JsonObject root = new JsonObject();
            root.addProperty("bonding", data.bonding);
            root.addProperty("totalRuns", data.totalRuns);
            root.addProperty("totalVictories", data.totalVictories);
            root.addProperty("maxFloorReached", data.maxFloorReached);
            root.addProperty("lastTierIndex", data.lastTierIndex);
            root.addProperty("pendingUpgrade", data.pendingUpgrade);
            if (data.lastRunSummary != null) {
                root.add("lastRunSummary", toJson(data.lastRunSummary));
            }
            // 原子写入：先写临时文件再整体替换，避免游戏崩溃/断电时档案损坏
            File target = stateFile();
            File tmp = new File(target.getParentFile(), FILE_NAME + ".tmp");
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
            try {
                java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                // 替换失败：至少把临时文件清掉，不阻塞游戏
                if (!tmp.delete()) {
                    tmp.deleteOnExit();
                }
            }
        } catch (Exception ignored) {
            // 落盘失败不致命
        }
    }

    private static JsonObject toJson(LastRunSummary s) {
        JsonObject o = new JsonObject();
        o.addProperty("floorReached", s.floorReached);
        o.addProperty("victory", s.victory);
        com.google.gson.JsonArray picked = new com.google.gson.JsonArray();
        for (String c : s.pickedCardIds) {
            if (c != null && !c.trim().isEmpty()) {
                picked.add(c.trim());
            }
        }
        o.add("pickedCardIds", picked);
        com.google.gson.JsonArray chats = new com.google.gson.JsonArray();
        for (String c : s.chatHighlights) {
            if (c != null && !c.trim().isEmpty()) {
                chats.add(c.trim());
            }
        }
        o.add("chatHighlights", chats);
        return o;
    }

    /**
     * 加载跨局关系档案。无文件/解析失败返回 null（视为全新玩家）。
     */
    public static RelationData load() {
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
            JsonObject root = new JsonParser().parse(sb.toString()).getAsJsonObject();
            if (root == null) {
                return null;
            }
            RelationData d = new RelationData();
            d.bonding = safeInt(root, "bonding");
            d.totalRuns = safeInt(root, "totalRuns");
            d.totalVictories = safeInt(root, "totalVictories");
            d.maxFloorReached = safeInt(root, "maxFloorReached");
            d.lastTierIndex = safeInt(root, "lastTierIndex");
            d.pendingUpgrade = safeBool(root, "pendingUpgrade");
            if (root.has("lastRunSummary") && root.get("lastRunSummary").isJsonObject()) {
                d.lastRunSummary = fromJson(root.getAsJsonObject("lastRunSummary"));
            }
            return d;
        } catch (Exception e) {
            // 档案损坏（解析失败）：备份原文件再视为新玩家，避免下次结算静默覆盖掉可恢复数据
            try {
                File bak = new File(stateFile().getParentFile(), FILE_NAME + ".bak");
                java.nio.file.Files.move(f.toPath(), bak.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private static LastRunSummary fromJson(JsonObject o) {
        LastRunSummary s = new LastRunSummary();
        s.floorReached = safeInt(o, "floorReached");
        s.victory = safeBool(o, "victory");
        if (o.has("pickedCardIds") && o.get("pickedCardIds").isJsonArray()) {
            for (com.google.gson.JsonElement e : o.getAsJsonArray("pickedCardIds")) {
                if (e != null && e.isJsonPrimitive()) {
                    String v = e.getAsString();
                    if (v != null && !v.trim().isEmpty()) {
                        s.pickedCardIds.add(v.trim());
                    }
                }
            }
        }
        if (o.has("chatHighlights") && o.get("chatHighlights").isJsonArray()) {
            for (com.google.gson.JsonElement e : o.getAsJsonArray("chatHighlights")) {
                if (e != null && e.isJsonPrimitive()) {
                    String v = e.getAsString();
                    if (v != null && !v.trim().isEmpty()) {
                        s.chatHighlights.add(v.trim());
                    }
                }
            }
        }
        return s;
    }

    private static int safeInt(JsonObject root, String key) {
        try {
            if (!root.has(key)) {
                return 0;
            }
            return root.get(key).getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean safeBool(JsonObject root, String key) {
        try {
            return root.has(key) && root.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}
