package com.derekjass.sts.weightedpaths.creative;

/**
 * 「卡的态度」引擎 —— 最小可行版（MVP）。
 *
 * 目标：打破「工具永远是沉默的」这条官方规矩。当玩家在卡奖中违背了 Mod 的推荐时，
 * 生成一句「记得你干了什么」的有态度的台词，让卡不再是死的工具，而是一个会跟你较劲、
 * 会记你一笔的「活物」。
 *
 * 台词生命周期：
 *  - 玩家在卡奖 N 结束时违背推荐 → {@link #evaluateReward} 生成 {@code pendingLine}（待提升）；
 *  - 下一次卡奖 N+1 出现时 → {@link #advanceForNewReward} 把 pending 提升为当前卡奖界面的
 *    {@code displayLine}，供渲染层持续显示（让玩家看到上一局「卡记得你」的态度）。
 *
 * 设计原则：
 *  - 台词从「玩家实际行为」生长（弃了哪张、抓了哪张、该防却抓攻），不是凭空乱说。
 *  - 态度 = 打破工具沉默 + 记得具体行为。宁可少，要有灵魂。
 *  - 一次只留一句，不刷屏。
 *
 * 本类纯逻辑，可单测；游戏内显示由 patches 层负责。
 */
public final class CardAttitudeEngine {

    /** 待提升到卡奖界面显示的台词（上一次卡奖违背生成）。 */
    private static String pendingLine = "";
    /** 当前卡奖界面持续显示的台词。 */
    private static String displayLine = "";
    /** AI 增强（可为 null，则纯本地模板）。 */
    private static AttitudeAi ai;
    private static String apiKey = "";

    private CardAttitudeEngine() {
    }

    /**
     * 评估一次卡奖，若玩家违背推荐则生成一句待显示台词。
     * 已有待显示台词时不再覆盖（一次只怼一句，避免刷屏）。
     */
    public static void evaluateReward(String recommendedId, boolean recommendedSkipAll,
                                      String chosenId, boolean skipped, String chosenGrade) {
        if (!pendingLine.isEmpty()) {
            return;
        }
        boolean hasRecommendation = recommendedId != null && !recommendedId.isEmpty();

        if (skipped) {
            if (!recommendedSkipAll && hasRecommendation) {
                pendingLine = pick(POOL_SKIPPED_RECOMMENDED, recommendedId);
            }
            return;
        }
        if (chosenId == null || chosenId.isEmpty()) {
            return;
        }
        if (recommendedSkipAll) {
            pendingLine = pick(POOL_PICKED_DESPITE_SKIP, chosenId);
            return;
        }
        if (hasRecommendation && !chosenId.equals(recommendedId)) {
            if (isLowGrade(chosenGrade)) {
                pendingLine = pick(POOL_PICKED_WEAK, recommendedId, chosenId);
            } else {
                pendingLine = pick(POOL_IGNORED_RECOMMENDED, recommendedId, chosenId);
            }
        }
    }

    /** 测试注入 mock AI 与 key。 */
    public static void setAiForTest(AttitudeAi ai, String key) {
        CardAttitudeEngine.ai = ai;
        CardAttitudeEngine.apiKey = key == null ? "" : key;
    }

    /**
     * 异步用 AI 增强当前待显示台词；失败/无 key/已被推进则保留本地模板。
     * 调用方在玩家卡奖结束后调用（此时本地模板已生成）。
     */
    public static void enrichWithAi(String recommendedId, boolean recommendedSkipAll,
                                    String chosenId, boolean skipped, String chosenGrade,
                                    String deckContext) {
        if (pendingLine.isEmpty()) {
            return;
        }
        if (ai == null) {
            ai = createDefaultAi();
        }
        if (ai == null) {
            return; // 无 key：纯本地模板
        }
        final String template = pendingLine;
        new Thread(() -> {
            try {
                String line = ai.generateFor(recommendedId, recommendedSkipAll,
                        chosenId, skipped, chosenGrade, deckContext);
                // 仅当模板尚未被 advance 消费时替换，避免覆盖更新的台词
                if (line != null && !line.isEmpty() && pendingLine.equals(template)) {
                    pendingLine = line;
                }
            } catch (Exception ignored) {
                // 网络/解析异常：保留模板
            }
        }).start();
    }

    /** 懒加载默认 AI：从环境变量/系统属性读 key；未配置返回 null（纯模板）。 */
    private static AttitudeAi createDefaultAi() {
        String key = System.getenv("RUN_ADVISOR_AI_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("runAdvisor.aiKey");
        }
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        return new DeepSeekAttitudeAi(key.trim());
    }

    /** 下一次卡奖出现时调用：把上次生成的台词提升为当前卡奖界面显示。 */
    public static void advanceForNewReward() {
        if (!pendingLine.isEmpty()) {
            displayLine = pendingLine;
            pendingLine = "";
        }
    }

    /** 当前卡奖界面应显示的内容（可能为空）。 */
    public static String displayLine() {
        return displayLine;
    }

    public static boolean hasDisplay() {
        return !displayLine.isEmpty();
    }

    /** 消费（取出并清空）当前待提升台词。仅供测试。 */
    public static String consumePendingLine() {
        String line = pendingLine;
        pendingLine = "";
        return line;
    }

    public static boolean hasPending() {
        return !pendingLine.isEmpty();
    }

    /** 仅供测试注入：直接设置一句待提升台词。 */
    public static void setPendingForTest(String line) {
        pendingLine = line == null ? "" : line;
    }

    private static boolean isLowGrade(String grade) {
        return grade != null && (grade.equals("C") || grade.equalsIgnoreCase("c"));
    }

    private static String pick(String[] pool, String... names) {
        int i = (int) (Math.random() * pool.length);
        String result = pool[i];
        if (names.length > 0 && names[0] != null) {
            result = result.replace("{A}", names[0]);
        }
        if (names.length > 1 && names[1] != null) {
            result = result.replace("{B}", names[1]);
        }
        return result;
    }

    /** 该抓却没抓，跳过了。 */
    private static final String[] POOL_SKIPPED_RECOMMENDED = {
            "我把 {A} 都递到你手边了，你绕开走了。行，你有你的想法。",
            "推荐里明明写着 {A}，你一眼都没看。我有点失望，真的。",
            "你宁可空手，也不拿 {A}。你这倔劲儿，我记住了。"
    };

    /** 推荐整次跳过，你却抓了一张。 */
    private static final String[] POOL_PICKED_DESPITE_SKIP = {
            "我都说了这轮别乱拿，你偏要抓 {A}。行，等着看它怎么坑你。",
            "该空手的时候你手痒，抓了张 {A}。我看你能后悔多久。"
    };

    /** 弃了推荐的强卡，抓了别的。 */
    private static final String[] POOL_IGNORED_RECOMMENDED = {
            "放着 {A} 不要，你偏挑 {B}。一个是好牌，一个是你喜欢的，你选了喜欢的。行，随你。",
            "{A} 就摆在你面前，你看都不看，去拿了 {B}。你这眼光……我不评价。",
            "我辛辛苦苦把 {A} 评到最前，你反手抓了 {B}。行吧，这局你自己扛。"
    };

    /** 弃了推荐，还抓了张 C 级弱卡。 */
    private static final String[] POOL_PICKED_WEAK = {
            "放着 {A} 不要，你居然拿 {B}？它连及格都够呛，你倒是宝贝。我服了。",
            "我把 {A} 排前面，你反手抓了张 C 级的 {B}。你不是缺牌，你是缺判断。",
            "你明明可以拿 {A}，却抱走 {B}。行，祝你们俩天长地久。"
    };
}
