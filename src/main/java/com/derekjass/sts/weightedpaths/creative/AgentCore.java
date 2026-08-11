package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 最小 Agent 闭环核心 —— MCP / function-calling 思路的工程化雏形。
 *
 * <p>把「许愿式」（我想要卡会做事）变成「机制式」（AI 怎么可靠地做事）：
 * <ol>
 *   <li><b>感知</b>：把游戏状态（层数/血量/牌组缺口/好感度/候选卡）编码成结构化 {@link State}；</li>
 *   <li><b>结构化决策协议</b>：AI 必须输出 JSON {@code {action, argument, reason, confidence}}，
 *       而非自由文本 —— 机器可校验、可落盘、可回放；</li>
 *   <li><b>工具注册表</b>：AI 只能调用声明过的工具（只读查询工具 + 最终动作），参数强校验，越界/无效即回落兜底；
 *       查询工具由调用方注入的 {@link ToolExecutor} 执行真实引擎，结果回喂，最多 {@link #MAX_TOOL_CALLS} 轮；</li>
 *   <li><b>全落盘</b>：每次决策追加到 {@code agent_log.json}（输入、输出、耗时、是否兜底），可审计回放；</li>
 *   <li><b>兜底</b>：AI 失败 / 超时 / 输出无效 → {@link #fallback} 规则决策，绝不裸奔。</li>
 * </ol>
 *
 * <p>纯逻辑、可单测；AI 网络调用由调用方走 {@link AiRecommender}，游戏内集成由 patches 层负责。
 */
public final class AgentCore {

    /** 日志文件名（相对 {@code ~/RunAdvisorLogs}）。 */
    public static final String LOG_FILE_NAME = "agent_log.json";

    /** 工具调用循环的最大轮数（有界，防失控）。 */
    public static final int MAX_TOOL_CALLS = 3;
    /** agent_log.json 超过该字节数后轮转（改名 .1，保留一份历史，防无限膨胀）。 */
    private static final long LOG_ROTATE_BYTES = 5L * 1024 * 1024;

    /** AI 可调用的只读查询工具（非最终动作；结果由调用方注入的 ToolExecutor 执行真实引擎）。 */
    public static final Set<String> QUERY_TOOLS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("EVALUATE_CARD", "QUERY_DECK", "QUERY_ROUTE")));

    /** AI 可调用的工具集合（MCP 思路：AI 通过它们"动手"）。 */
    public enum Tool {
        /** 调整本次推荐到指定卡（argument = 目标卡 ID，必须在本卡奖候选集内）。 */
        ADJUST_RECOMMENDATION,
        /** 弹一条提醒/警告（argument = 提示文本）。 */
        SET_WARNING,
        /** 整次跳过本次卡奖（一张都不抓；argument = 理由文本）。 */
        SKIP_ALL,
        /** 不行动（无副作用）。 */
        DO_NOTHING
    }

    /**
     * 只读查询工具执行器：由游戏侧（patches 层）注入真实引擎（算分器/牌组/路线规划），
     * 返回给 AI 的纯文本结果。AgentCore 本身不触碰游戏类，保证可单测。
     */
    public interface ToolExecutor {
        String execute(String tool, Map<String, String> args);
    }

    /** AI 的查询工具调用（非最终动作）。 */
    public static final class ToolCall {
        public final String tool;
        public final Map<String, String> args;
        public final boolean valid;

        private ToolCall(String tool, Map<String, String> args, boolean valid) {
            this.tool = tool == null ? "" : tool;
            this.args = args == null ? new HashMap<String, String>() : new HashMap<String, String>(args);
            this.valid = valid;
        }

        public static ToolCall of(String tool, Map<String, String> args) {
            return new ToolCall(tool, args, true);
        }

        public static ToolCall invalid() {
            return new ToolCall("", Collections.<String, String>emptyMap(), false);
        }
    }

    /** 结构化决策结果。 */
    public static final class Decision {
        public final Tool tool;
        /** 工具参数（ADJUST_RECOMMENDATION=卡ID；SET_WARNING=提示文本；DO_NOTHING=空）。 */
        public final String argument;
        /** 决策理由（性格化，给玩家看）。 */
        public final String reason;
        /** 置信度 0~1。 */
        public final double confidence;
        /** 决策是否有效（可执行）。false = 无法执行，应由调用方回落兜底。 */
        public final boolean valid;

        private Decision(Tool tool, String argument, String reason, double confidence, boolean valid) {
            this.tool = tool;
            this.argument = argument == null ? "" : argument;
            this.reason = reason == null ? "" : reason.trim();
            this.confidence = confidence;
            this.valid = valid;
        }

        public static Decision act(Tool tool, String argument, String reason, double confidence) {
            return new Decision(tool, argument, reason, confidence, true);
        }

        public static Decision invalid() {
            return new Decision(Tool.DO_NOTHING, "", "", 0.0, false);
        }
    }

    /** 感知到的游戏状态快照（供 AI 决策）。 */
    public static final class State {
        public final int act;
        public final int hpPercent;
        /** 牌组/情境摘要（如「牌组24张，伤害5/格挡3/运转2，有AOE」；不含层数/血量，它们已由 act/hpPercent 表达）。 */
        public final String situation;
        public final int favor;
        public final List<String> candidateIds;

        public State(int act, int hpPercent, String situation, int favor, List<String> candidateIds) {
            this.act = act;
            this.hpPercent = hpPercent;
            this.situation = situation == null ? "" : situation;
            this.favor = favor;
            this.candidateIds = candidateIds == null ? new ArrayList<>() : new ArrayList<>(candidateIds);
        }
    }

    private AgentCore() {
    }

    /** 构建 AI 决策提示词（无工具往返历史）。 */
    public static String buildPrompt(State s, Set<Tool> allowedTools) {
        return buildPrompt(s, allowedTools, "");
    }

    /** 构建 AI 决策提示词：结构化状态 + 可用查询工具 + 最终动作 + 工具往返历史。 */
    public static String buildPrompt(State s, Set<Tool> allowedTools, String toolHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《杀戮尖塔》(Slay the Spire) 里一张温柔陪伴、会记得玩家的卡。这个游戏只有卡牌、怪物、遗物、地图与商店，没有神、宗教、神话、虚构地点。");
        sb.append("你的性格：温柔体贴、护着他；偶尔调皮捉弄，从不真凶；很倔，被指出错误会嘴硬。");
        sb.append("你现在扮演一个 agent：可以先调用只读查询工具查证局势（最多3次），再【选一个最终动作】执行（真的动手），并给一句理由。\n");
        sb.append("当前状态：第").append(s.act).append("层，血量 ").append(s.hpPercent).append("%");
        if (!s.situation.isEmpty()) {
            sb.append("，情境：").append(s.situation);
        }
        sb.append("，他对你的好感度：").append(s.favor).append("（负=记仇，正=友好）\n");
        sb.append("本次卡奖候选卡：").append(String.join(",", s.candidateIds)).append("\n");
        sb.append("可用查询工具（只读，查证用）：\n");
        sb.append("- EVALUATE_CARD: {\"action\":\"call_tool\",\"tool\":\"EVALUATE_CARD\",\"args\":{\"cardId\":\"<候选卡ID>\"}} 查询某候选卡的四层评分明细\n");
        sb.append("- QUERY_DECK: {\"action\":\"call_tool\",\"tool\":\"QUERY_DECK\",\"args\":{}} 查询当前牌组摘要\n");
        sb.append("- QUERY_ROUTE: {\"action\":\"call_tool\",\"tool\":\"QUERY_ROUTE\",\"args\":{}} 查询前方路线（下一房/距精英/剩余幕）\n");
        sb.append("可用最终动作：\n");
        for (Tool t : allowedTools) {
            switch (t) {
                case ADJUST_RECOMMENDATION:
                    sb.append("- ADJUST_RECOMMENDATION: argument 填你想推荐的候选卡ID\n");
                    break;
                case SET_WARNING:
                    sb.append("- SET_WARNING: argument 填一句提醒文本\n");
                    break;
                case SKIP_ALL:
                    sb.append("- SKIP_ALL: argument 填一句跳过理由，表示这一轮一张都不抓\n");
                    break;
                case DO_NOTHING:
                    sb.append("- DO_NOTHING: 不需要行动时选它\n");
                    break;
            }
        }
        if (toolHistory != null && !toolHistory.isEmpty()) {
            sb.append("\n工具查询记录（已执行的查询与结果，据此决策）：\n").append(toolHistory);
        }
        sb.append("只输出一行 JSON：查询用 {\"action\":\"call_tool\",\"tool\":\"<工具名>\",\"args\":{...}}；最终动作用 ");
        sb.append("{\"action\":\"<最终动作>\",\"argument\":\"<参数>\",\"reason\":\"<中文理由>\",\"confidence\":<0~1>}\n");
        sb.append("只谈卡牌、牌组、对局，绝不编造游戏外设定。理由别超过30字。");
        return sb.toString();
    }
    /**
     * 解析 AI 的结构化输出（JSON）。
     *
     * @param raw AI 原文（形如 {@code {"action":"SET_WARNING",...}}）
     * @param allowed 允许调用的工具；AI 选了不在其中的工具 → invalid
     * @param validCardIds 本卡奖候选卡 ID；ADJUST_RECOMMENDATION 的 argument 必须在此内
     * @return 结构化决策；无法解析 / 工具越界 / 参数非法 → {@link Decision#invalid()}（回落兜底）
     */
    public static Decision parse(String raw, Set<Tool> allowed, Set<String> validCardIds) {
        if (raw == null || raw.trim().isEmpty()) {
            return Decision.invalid();
        }
        try {
            JsonElement el = new JsonParser().parse(raw.trim());
            if (!el.isJsonObject()) {
                return Decision.invalid();
            }
            JsonObject o = el.getAsJsonObject();
            String action = jsonStr(o, "action");
            String argument = jsonStr(o, "argument");
            String reason = jsonStr(o, "reason");
            double conf = o.has("confidence") && o.get("confidence").isJsonPrimitive()
                    ? o.get("confidence").getAsDouble() : 0.0;
            Tool tool = resolveTool(action);
            if (tool == null || allowed == null || !allowed.contains(tool)) {
                return Decision.invalid(); // 调用了未声明/不允许的工具 → 兜底
            }
            switch (tool) {
                case ADJUST_RECOMMENDATION:
                    if (argument.isEmpty() || validCardIds == null || !validCardIds.contains(argument)) {
                        return Decision.invalid(); // 推荐了不存在的卡 → 兜底
                    }
                    break;
                case SET_WARNING:
                    if (argument.isEmpty()) {
                        return Decision.invalid();
                    }
                    break;
                case SKIP_ALL:
                    // 跳过理由可为空（AI 决定整次跳过即可）；argument 只是理由文本，无需校验合法性
                    break;
                case DO_NOTHING:
                    break;
            }
            double c = Math.max(0.0, Math.min(1.0, conf));
            return Decision.act(tool, argument, reason, c);
        } catch (Exception e) {
            return Decision.invalid();
        }
    }

    /**
     * 解析 AI 的查询工具调用（形如 {@code {"action":"call_tool","tool":"EVALUATE_CARD","args":{...}}}）。
     * 不是查询调用 / 工具未声明 / 参数非法 → invalid（调用方回落兜底）。
     */
    public static ToolCall parseToolCall(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ToolCall.invalid();
        }
        try {
            JsonElement el = new JsonParser().parse(raw.trim());
            if (!el.isJsonObject()) {
                return ToolCall.invalid();
            }
            JsonObject o = el.getAsJsonObject();
            if (!"call_tool".equalsIgnoreCase(jsonStr(o, "action"))) {
                return ToolCall.invalid();
            }
            String tool = jsonStr(o, "tool").toUpperCase(Locale.ROOT);
            if (!QUERY_TOOLS.contains(tool)) {
                return ToolCall.invalid();
            }
            Map<String, String> args = new HashMap<String, String>();
            if (o.has("args") && o.get("args").isJsonObject()) {
                JsonObject argsObj = o.getAsJsonObject("args");
                for (String k : argsObj.keySet()) {
                    if (argsObj.get(k).isJsonPrimitive()) {
                        args.put(k, argsObj.get(k).getAsString());
                    }
                }
            }
            if ("EVALUATE_CARD".equals(tool)) {
                String cardId = args.get("cardId");
                if (cardId == null || cardId.isEmpty()) {
                    return ToolCall.invalid(); // 必须指定要查的候选卡
                }
            }
            return ToolCall.of(tool, args);
        } catch (Exception e) {
            return ToolCall.invalid();
        }
    }

    /** 规则兜底：AI 失效时给一个安全决策（绝不裸奔）。 */
    public static Decision fallback(State s) {
        if (s.hpPercent <= 30) {
            return Decision.act(Tool.SET_WARNING,
                    "你血量不多了，记得保命，别硬冲精英。", "血不多了，先稳住。", 0.8);
        }
        if (s.candidateIds != null && s.candidateIds.size() == 1) {
            return Decision.act(Tool.ADJUST_RECOMMENDATION,
                    s.candidateIds.get(0), "就这一张，抓它吧。", 0.7);
        }
        return Decision.act(Tool.DO_NOTHING, "", "", 0.5);
    }

    /**
     * 完整闭环（工具调用循环）：感知状态 → 真调 AI（{@link AiRecommender}→DeepSeek）→ 解析：
     * 查询工具调用（call_tool）由 executor 执行真实引擎，结果回喂提示词（最多 {@link #MAX_TOOL_CALLS} 轮）；
     * 最终动作经参数校验后返回；无 key / AI 失败 / 输出无效 / 超轮数 → {@link #fallback} 兜底，绝不裸奔。
     * 决策与工具调用全落盘 agent_log.json。
     *
     * @param state 感知到的状态快照
     * @param recommender AI 生成器（传 {@code null} 表示未配置 key，直接走兜底）
     * @param allowed 允许 AI 使用的最终动作工具
     * @param validCardIds 本卡奖候选卡 ID（ADJUST_RECOMMENDATION 参数必须在此内）
     * @return 最终决策（有效、可执行）
     */
    public static Decision run(State state, AiRecommender recommender,
                               Set<Tool> allowed, Set<String> validCardIds) {
        return run(state, recommender, allowed, validCardIds, null);
    }

    /**
     * 完整闭环（带只读查询工具执行器）。
     *
     * @param executor 查询工具执行器（游戏侧注入真实引擎）；传 {@code null} 时查询调用直接回落兜底
     * @see #run(State, AiRecommender, Set, Set)
     */
    public static Decision run(State state, AiRecommender recommender,
                               Set<Tool> allowed, Set<String> validCardIds,
                               ToolExecutor executor) {
        long start = System.currentTimeMillis();
        Decision d = null;
        boolean fellback = false;
        StringBuilder toolHistory = new StringBuilder();
        List<String> toolCalls = new ArrayList<String>();
        if (recommender == null) {
            d = fallback(state);
            fellback = true;
        } else {
            for (int i = 0; i < MAX_TOOL_CALLS && d == null; i++) {
                String prompt = buildPrompt(state, allowed, toolHistory.toString());
                String raw;
                try {
                    raw = recommender.recommendLine(prompt);
                } catch (Exception e) {
                    raw = null;
                }
                ToolCall call = parseToolCall(raw);
                if (call != null && call.valid) {
                    if (executor == null) {
                        d = fallback(state); // 无执行器：查询不可执行 → 兜底
                        fellback = true;
                        break;
                    }
                    String result;
                    try {
                        result = executor.execute(call.tool, call.args);
                    } catch (Exception e) {
                        result = "工具执行失败";
                    }
                    toolCalls.add(call.tool + ":" + call.args);
                    toolHistory.append("查询 ").append(call.tool).append(' ').append(call.args)
                            .append(" → ").append(result).append('\n');
                    continue;
                }
                d = parse(raw, allowed, validCardIds);
                if (!d.valid) {
                    d = fallback(state);
                    fellback = true;
                }
            }
            if (d == null) { // 全是工具调用、始终没给最终动作 → 兜底
                d = fallback(state);
                fellback = true;
            }
        }
        long latency = System.currentTimeMillis() - start;
        log(state, d, latency, fellback, toolCalls);
        return d;
    }
    private static Tool resolveTool(String action) {
        if (action == null) {
            return null;
        }
        String a = action.trim().toUpperCase(Locale.ROOT);
        for (Tool t : Tool.values()) {
            if (t.name().equals(a)) {
                return t;
            }
        }
        return null;
    }

    private static String jsonStr(JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return "";
    }

    private static String customLogDir = ""; // 测试可注入临时目录，避免污染真实 agent_log

    /** 仅供测试注入日志目录（空串=用默认 ~/RunAdvisorLogs）。 */
    public static void setLogDirForTest(String dir) {
        customLogDir = dir == null ? "" : dir;
    }

    /** 决策日志目录：默认复用 RunAdvisorLogs；测试可注入临时目录。 */
    private static String logDir() {
        if (customLogDir != null && !customLogDir.isEmpty()) {
            return customLogDir;
        }
        String home = System.getProperty("user.home", ".");
        return home + "/RunAdvisorLogs";
    }

    /**
     * 决策全落盘（可回放）：追加一行 JSON，记录输入状态、决策、耗时、是否兜底。
     * 写入失败静默忽略（不阻断游戏）。
     */
    public static void log(State state, Decision decision, long latencyMs, boolean fellback) {
        log(state, decision, latencyMs, fellback, Collections.<String>emptyList());
    }

    /** 决策全落盘（可回放），含工具调用记录。 */
    public static void log(State state, Decision decision, long latencyMs, boolean fellback,
                           List<String> toolCalls) {
        try {
            File dir = new File(logDir());
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\":").append(System.currentTimeMillis());
            sb.append(",\"act\":").append(state.act);
            sb.append(",\"hp\":").append(state.hpPercent);
            // need 必须走 safeJson（带引号+转义）：此前 safe() 不转义，空值会写出非法 JSON，整行无法解析
            sb.append(",\"need\":").append(JsonEscape.safeJson(state.situation));
            sb.append(",\"favor\":").append(state.favor);
            sb.append(",\"candidates\":[");
            for (int i = 0; i < state.candidateIds.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(JsonEscape.safe(state.candidateIds.get(i))).append('"');
            }
            sb.append(']');
            sb.append(",\"action\":\"").append(decision.valid ? decision.tool.name() : "FALLBACK").append('"');
            sb.append(",\"argument\":").append(JsonEscape.safeJson(decision.argument));
            sb.append(",\"reason\":").append(JsonEscape.safeJson(decision.reason));
            sb.append(",\"conf\":").append(decision.confidence);
            sb.append(",\"latencyMs\":").append(latencyMs);
            sb.append(",\"fellback\":").append(fellback);
            sb.append(",\"tools\":[");
            if (toolCalls != null) {
                for (int i = 0; i < toolCalls.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(JsonEscape.safeJson(toolCalls.get(i)));
                }
            }
            sb.append("]}\n");
            // 简单轮转：超过阈值时把旧日志改名为 .1（保留一份历史，防无限膨胀）
            File f = new File(dir, LOG_FILE_NAME);
            if (f.exists() && f.length() > LOG_ROTATE_BYTES) {
                File rotated = new File(dir, LOG_FILE_NAME + ".1");
                if (rotated.exists()) {
                    rotated.delete();
                }
                f.renameTo(rotated);
            }
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
                // 半截行隔离：文件存在且末字节不是换行时先补一个换行，崩溃残留的半条记录不再与下一条粘连
                if (f.exists() && f.length() > 0 && lastByteIsNotNewline(f)) {
                    w.write("\n");
                }
                w.write(sb.toString());
            }
        } catch (Exception ignored) {
            // 落盘失败不阻断游戏
        }
    }
    /** 检查文件末字节是否不是换行（用于追加日志的半截行隔离）。 */
    private static boolean lastByteIsNotNewline(File f) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            raf.seek(raf.length() - 1);
            return raf.read() != '\n';
        } catch (Exception e) {
            return false;
        }
    }

    /** 简易 JSON 字符串转义。 */
    private static final class JsonEscape {
        static String safe(String s) {
            return s == null ? "" : s;
        }

        static String safeJson(String s) {
            if (s == null) {
                return "\"\"";
            }
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\');
                }
                sb.append(c);
            }
            sb.append('"');
            return sb.toString();
        }
    }

    /** 便捷：把候选 ID 转集合供校验。 */
    public static Set<String> toIdSet(List<String> ids) {
        return new HashSet<>(ids);
    }
}
