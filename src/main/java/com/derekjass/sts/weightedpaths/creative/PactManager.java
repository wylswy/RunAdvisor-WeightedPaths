package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.card.GlobalRunPlan;

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
 */
public final class PactManager {

    private static PactEngine engine = new PactEngine();
    private static int lastAct = 0;
    /** 保守建议奖励生效标志：契约完成后置 true，由推荐链路消费（如提高跳过阈值）。 */
    private static boolean conservativeAdviceActive = false;

    private static final Set<String> ACCEPT_WORDS = new HashSet<>(Arrays.asList(
            "同意", "我同意", "接受", "我接受", "答应", "我答应", "成交", "就这么办", "说到做到"));
    private static final Set<String> DECLINE_WORDS = new HashSet<>(Arrays.asList(
            "拒绝", "我拒绝", "不要", "不干", "算了", "不了", "不答应", "没兴趣", "别闹"));

    private PactManager() {
    }

    /** 底层状态机（测试/扩展用）。 */
    public static PactEngine engine() {
        return engine;
    }

    /** 保守建议奖励是否生效（推荐链路消费）。 */
    public static boolean isConservativeAdviceActive() {
        return conservativeAdviceActive;
    }

    /** 新局/SL 重开：清空契约状态（契约暂不跨 SL 持久化，重开可重新提出）。 */
    public static void resetForRun() {
        engine = new PactEngine();
        lastAct = 0;
        conservativeAdviceActive = false;
    }

    /** 地图打开（每层一次）：检测幕切换 → 结算上一幕契约 + 提出新契约；返回需要显示的消息。 */
    public static List<String> onMapOpen(int act, boolean deckNeedsDiscipline, boolean seedAvailable) {
        List<String> out = new ArrayList<>();
        if (act == lastAct) {
            return out;
        }
        String endMsg = onActEnd();
        if (!endMsg.isEmpty()) {
            out.add(endMsg);
        }
        lastAct = act;
        String offer = onActStart(act, deckNeedsDiscipline, seedAvailable);
        if (!offer.isEmpty()) {
            out.add(offer);
        }
        return out;
    }

    /** 幕开始：提出契约；返回提案消息（无提案返回空串）。 */
    public static String onActStart(int act, boolean deckNeedsDiscipline, boolean seedAvailable) {
        PactEngine.Pact pact = engine.propose(act, deckNeedsDiscipline, seedAvailable);
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
        for (String w : ACCEPT_WORDS) {
            if (t.contains(w)) {
                engine.accept();
                return "好，说定了。别让我失望。";
            }
        }
        for (String w : DECLINE_WORDS) {
            if (t.contains(w)) {
                engine.decline();
                return "行，随你。";
            }
        }
        return "";
    }

    /** 玩家抓了一张卡（isAttack=攻击牌）。违背时返回需显示的消息。 */
    public static String onCardPicked(boolean isAttack) {
        int delta = engine.onCardPicked(isAttack);
        CardMoodEngine.adjustFavor(delta);
        if (delta < 0) {
            return "说好这幕不抓攻击牌的，你转头就抓……这笔我记下了。";
        }
        return "";
    }

    /** 进入精英房间。达标 → 完成+奖励；未达标 → 违背。返回需显示的消息。 */
    public static String onEliteReached(int hpPercent) {
        int delta = engine.onEliteReached(hpPercent);
        CardMoodEngine.adjustFavor(delta);
        if (delta < 0) {
            return "……不到五成血也敢冲精英？约定作废，我不高兴了。";
        }
        if (delta > 0) {
            return "说到做到，你挺讲信用。兑现奖励：" + rewardText();
        }
        return "";
    }

    /** 幕结束：完成/违背结算；返回需显示的消息。 */
    public static String onActEnd() {
        int delta = engine.onActEnd();
        CardMoodEngine.adjustFavor(delta);
        if (delta < 0) {
            return "这幕的约定你没做到……";
        }
        if (delta > 0) {
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
            try {
                GlobalRunPlan plan = GlobalRunPlan.fromCurrentRun();
                if (plan.roomsUntilElite >= 0) {
                    String ahead = String.join("", plan.upcomingThisAct);
                    return "下一个精英在 " + plan.roomsUntilElite + " 房后（前方：" + ahead + "），小心。";
                }
                if (plan.roomsUntilElite < 0 && !plan.nextRoom.isEmpty()) {
                    return "这一幕前方没有精英，稳着推就行。";
                }
            } catch (Throwable ignored) {
                // 拿不到路线信息时降级为通用提示
            }
            return "接下来我会帮你盯紧精英的。";
        }
        conservativeAdviceActive = true;
        return "接下来我建议你走保守路线：多歇火堆，少碰精英。";
    }

    private static String describe(PactEngine.Pact pact) {
        String condition;
        switch (pact.condition) {
            case NO_ATTACK_CARDS_THIS_ACT:
                condition = "这一幕别再抓攻击牌";
                break;
            case REACH_ELITE_HP_ABOVE_50:
            default:
                condition = "以至少五成血量到达下一个精英";
                break;
        }
        String reward;
        switch (pact.reward) {
            case REVEAL_NEXT_ELITE:
                reward = "我就提前告诉你精英在哪";
                break;
            case CONSERVATIVE_ADVICE:
            default:
                reward = "我帮你把路线调保守些";
                break;
        }
        return condition + "，做到的话，" + reward;
    }
}