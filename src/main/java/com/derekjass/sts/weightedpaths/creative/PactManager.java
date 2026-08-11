package com.derekjass.sts.weightedpaths.creative;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 契约/赌约与游戏流程的接线层：把 {@link PactEngine} 状态机接到游戏事件上，
 * 负责提案消息、接受/拒绝识别、好感度应用与奖励发放。
 *
 * <p>设计：所有判定逻辑在 {@link PactEngine}（可单测）；本类只是薄胶水，
 * 只做「事件 → 引擎 → 消息/好感度/奖励」的翻译，不持有业务规则。
 * 奖励文案需要的路线信息由游戏层通过 {@link #setEliteIntelProvider} 注入，
 * 本类不依赖游戏类，可在无游戏 jar 的门禁里单测。
 */
public final class PactManager {

    /** 精英情报（奖励文案用；由游戏层基于 GlobalRunPlan 注入）。 */
    public interface EliteIntel {
        /** 距下一精英还有几房；-1 表示前方无精英。 */
        int roomsUntilElite();

        /** 当前幕前方房间符号序列（不含当前格）。 */
        List<String> upcomingThisAct();

        /** 下一房符号；空串表示未知或已无后续。 */
        String nextRoom();

        /** 距指定符号（如火堆 R）还有几房；-1 表示前方没有。 */
        int roomsUntilSymbol(String target);

        /** 未来各幕（不含当前幕）剩余精英总数。 */
        int futureEliteCount();

        /** 未来各幕（不含当前幕）剩余火堆总数。 */
        int futureRestCount();

        /** 最优路线估值领先次优的差值；无可对比时返回 NaN。 */
        double routeValueLead();
    }

    /** 精英情报提供器：返回 null 表示当前拿不到（奖励文案降级为通用提示）。 */
    public interface EliteIntelProvider {
        EliteIntel get();
    }

    private static PactEngine engine = new PactEngine();
    private static int lastAct = 0;
    /** 保守建议奖励生效标志：契约完成后置 true，由推荐链路消费（如提高跳过阈值）。 */
    private static boolean conservativeAdviceActive = false;
    /** 默认无情报（测试环境与未接线时奖励文案走通用提示，不崩）。 */
    private static EliteIntelProvider eliteIntelProvider = () -> null;
    /** 违约记仇标志：违背契约后置 true（推荐链路消费：推荐偏保守）；完成下一份契约后原谅清空。 */
    private static boolean grudgeActive = false;

    private static final Set<String> ACCEPT_WORDS = new HashSet<>(Arrays.asList(
            "同意", "我同意", "接受", "我接受", "答应", "我答应", "成交", "就这么办", "说到做到"));
    private static final Set<String> DECLINE_WORDS = new HashSet<>(Arrays.asList(
            "拒绝", "我拒绝", "不要", "不同意", "不答应", "不想", "不干", "算了", "不了", "没兴趣", "别闹"));

    private PactManager() {
    }

    /** 注入精英情报提供器（游戏层初始化时调用；传入 null 则忽略）。 */
    public static void setEliteIntelProvider(EliteIntelProvider provider) {
        if (provider != null) {
            eliteIntelProvider = provider;
        }
    }

    /** 底层状态机（测试/扩展用）。 */
    public static PactEngine engine() {
        return engine;
    }

    /** 保守建议奖励是否生效（推荐链路消费）。 */
    public static boolean isConservativeAdviceActive() {
        return conservativeAdviceActive;
    }

    /** 违约记仇是否生效（推荐链路消费：推荐偏保守）。 */
    public static boolean isGrudgeActive() {
        return grudgeActive;
    }



    /** 导出契约状态（跨 SL 持久化用）。 */
    public static JsonObject exportState() {
        JsonObject o = new JsonObject();
        o.addProperty("lastAct", lastAct);
        o.addProperty("conservative", conservativeAdviceActive);
        o.addProperty("grudge", grudgeActive);
        JsonObject pact = engine.exportState();
        if (pact != null) {
            o.add("pact", pact);
        }
        return o;
    }

    /** 从持久化状态恢复契约（SL 重进同一局）；顶层字段非法则整体重置，pact 子对象非法则仅清空契约（不崩游戏）。 */
    public static void restoreState(JsonObject o) {
        if (o == null) {
            resetForRun();
            return;
        }
        try {
            lastAct = o.has("lastAct") ? o.get("lastAct").getAsInt() : 0;
            conservativeAdviceActive = o.has("conservative") && o.get("conservative").getAsBoolean();
            grudgeActive = o.has("grudge") && o.get("grudge").getAsBoolean();
            JsonObject pact = o.has("pact") && o.get("pact").isJsonObject()
                    ? o.getAsJsonObject("pact") : null;
            engine = new PactEngine();
            engine.restoreState(pact);
        } catch (Exception ignored) {
            resetForRun();
        }
    }

    /** 新局：清空契约状态（跨 SL 由 {@link #restoreState} 恢复）。 */
    public static void resetForRun() {
        engine = new PactEngine();
        lastAct = 0;
        conservativeAdviceActive = false;
        grudgeActive = false;
    }

    /** 地图打开（每层一次）：检测幕切换 → 结算上一幕契约 + 提出新契约；返回需要显示的消息。 */
    public static List<String> onMapOpen(int act, boolean seedAvailable) {
        List<String> out = new ArrayList<>();
        if (act == lastAct) {
            return out;
        }
        String endMsg = onActEnd();
        if (!endMsg.isEmpty()) {
            out.add(endMsg);
        }
        lastAct = act;
        String offer = onActStart(act, seedAvailable);
        if (!offer.isEmpty()) {
            out.add(offer);
        }
        return out;
    }

    /** 幕开始：提出契约；返回提案消息（无提案返回空串）。 */
    public static String onActStart(int act, boolean seedAvailable) {
        PactEngine.Pact pact = engine.propose(act, seedAvailable);
        if (pact == null) {
            return "";
        }
        return "我有个提议：" + describe(pact)
                + "（回「同意」我就当你答应了，回「拒绝」就算了。）";
    }

    /** 玩家聊天输入：识别接受/拒绝；返回卡应回复的消息（未处理返回空串）。 */
    public static String onChatInput(String text) {
        if (text == null || engine.current() == null
                || engine.current().status != PactEngine.Status.OFFERED) {
            return "";
        }
        String t = text.trim();
        // 先判拒绝（含否定变体如「不同意/不要同意」），再判接受，避免「不+同意」歧义
        for (String w : DECLINE_WORDS) {
            if (t.contains(w)) {
                engine.decline();
                return "行，随你。";
            }
        }
        for (String w : ACCEPT_WORDS) {
            if (t.contains(w)) {
                engine.accept();
                return "好，说定了。别让我失望。";
            }
        }
        return "";
    }



    /** 进入精英房间。达标 → 完成+奖励；未达标 → 违背。返回需显示的消息。 */
    public static String onEliteReached(int hpPercent) {
        int delta = engine.onEliteReached(hpPercent);
        CardMoodEngine.adjustFavor(delta);
        if (delta < 0) {
            grudgeActive = true;
            return "……不到五成血也敢冲精英？约定作废，我不高兴了。";
        }
        if (delta > 0) {
            grudgeActive = false; // 说到做到 → 原谅
            return "说到做到，你挺讲信用。兑现奖励：" + rewardText();
        }
        return "";
    }

    /** 幕结束：完成/违背结算；返回需显示的消息。 */
    public static String onActEnd() {
        int delta = engine.onActEnd();
        CardMoodEngine.adjustFavor(delta);
        if (delta < 0) {
            grudgeActive = true;
            return "这幕的约定你没做到……";
        }
        if (delta > 0) {
            grudgeActive = false; // 说到做到 → 原谅
            return "这幕你守住了约定，给你：" + rewardText();
        }
        return "";
    }

    private static String rewardText() {
        PactEngine.Reward reward = engine.consumeReward();
        if (reward == null) {
            return "";
        }
        if (reward == PactEngine.Reward.REVEAL_NEXT_ELITE) {
            // 玩家有眼睛：地图上能看见精英/火堆的位置，重复念一遍等于没奖励。
            // 只给「眼睛看不见」的情报：未来幕精英/火堆总数 + 当前路线估值领先次优的幅度。
            try {
                EliteIntel plan = eliteIntelProvider.get();
                if (plan != null) {
                    StringBuilder sb = new StringBuilder();
                    boolean any = false;
                    if (plan.futureEliteCount() > 0 || plan.futureRestCount() > 0) {
                        sb.append("后面还有 ").append(plan.futureEliteCount())
                                .append(" 个精英、").append(plan.futureRestCount()).append(" 个火堆");
                        any = true;
                    }
                    double lead = plan.routeValueLead();
                    if (!Double.isNaN(lead)) {
                        long rounded = Math.round(lead);
                        if (rounded != 0) {
                            if (any) {
                                sb.append("；");
                            }
                            sb.append("当前路线估值领先次优约 ").append(rounded).append(" 分");
                            any = true;
                        }
                    }
                    if (any) {
                        sb.append("。这幕留点 AoE，别把血拼光。");
                        return sb.toString();
                    }
                }
            } catch (Throwable ignored) {
                // 拿不到情报时降级为通用提示
            }
            return "接下来我会帮你盯紧精英的。";
        }
        conservativeAdviceActive = true;
        return "接下来我建议你走保守路线：多歇火堆，少碰精英。";
    }

    /** 当前契约的人类可读摘要（供聊天框状态栏显示）；无活动契约返回空串。 */
    public static String describeCurrentPact() {
        PactEngine.Pact pact = engine.current();
        return pact == null ? "" : describe(pact);
    }

    private static String describe(PactEngine.Pact pact) {
        String condition = "以至少五成血量到达下一个精英";
        String reward;
        switch (pact.reward) {
            case REVEAL_NEXT_ELITE:
                reward = "我就提前告诉你后面几幕的精英和火堆";
                break;
            case CONSERVATIVE_ADVICE:
            default:
                reward = "我帮你把路线调保守些";
                break;
        }
        return condition + "，做到的话，" + reward;
    }
}