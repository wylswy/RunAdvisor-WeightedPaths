package com.derekjass.sts.weightedpaths.creative;

/**
 * 「卡的好感度」状态机 —— 让卡对玩家有记忆、有态度、会记仇也会原谅。
 *
 * 情感循环：你违背推荐 → 好感度↓ → 它闹脾气；你在聊天框道歉/按它推荐抓 → 好感度↑ → 它原谅、恢复给你正确推荐。
 *
 * 状态由好感度数值决定：
 *  - {@link Mood#RESENTFUL}：好感度很低，记仇/闹脾气（推荐会带「哼」、台词更毒、可能给反向提示但明示）
 *  - {@link Mood#UNHAPPY}：有点不高兴（台词带刺）
 *  - {@link Mood#FRIENDLY}：友好（正常推荐）
 *
 * 本类纯逻辑、可单测；游戏交互由 patches 层负责。
 */
public final class CardMoodEngine {

    public enum Mood {
        FRIENDLY, UNHAPPY, RESENTFUL
    }

    private static final int FAVOR_MIN = -10;
    private static final int FAVOR_MAX = 10;

    private static int favor = 0;

    private CardMoodEngine() {
    }

    /** 玩家违背了推荐（好感度下降）。 */
    public static void recordDefiance() {
        favor = Math.max(FAVOR_MIN, favor - 2);
    }

    /** 玩家顺从了推荐/按推荐抓（好感度回升）。 */
    public static void recordCompliance() {
        favor = Math.min(FAVOR_MAX, favor + 1);
    }

    /** 玩家道歉（好感度大幅回升，原谅）。 */
    public static void recordApology() {
        favor = Math.min(FAVOR_MAX, favor + 4);
    }

    /** 当前好感度状态。 */
    public static Mood currentMood() {
        if (favor <= -6) {
            return Mood.RESENTFUL;
        }
        if (favor <= -2) {
            return Mood.UNHAPPY;
        }
        return Mood.FRIENDLY;
    }

    /** 当前好感度数值（-10..10）。 */
    public static int favor() {
        return favor;
    }

    /** 重置好感度（新的一局）。 */
    public static void reset() {
        favor = 0;
    }

    /** 恢复好感度（SL 重进同一局）。夹紧到 -10..10。 */
    public static void restoreFavor(int value) {
        favor = Math.max(FAVOR_MIN, Math.min(FAVOR_MAX, value));
    }

    /**
     * 道歉识别：判断玩家的聊天输入是否包含道歉/服软意图。
     * 命中则返回 true，调用方应 {@link #recordApology()}。
     */
    /** 按增量调整好感度（契约完成/违背、探针等），夹紧到 -10..10。 */
    public static void adjustFavor(int delta) {
        favor = Math.max(FAVOR_MIN, Math.min(FAVOR_MAX, favor + delta));
    }
    public static boolean isApology(String input) {
        if (input == null) {
            return false;
        }
        String t = input.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (String w : APOLOGY_KEYWORDS) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] APOLOGY_KEYWORDS = {
            "对不起", "我错了", "知道错了", "我的错", "是我的错", "抱歉", "不好意思",
            "原谅", "不该不听", "不该没听", "听你的", "以后都听", "以后听你的",
            "我悔过", "别生气", "消消气", "我改", "以后不这样", "我认错"
    };
}
