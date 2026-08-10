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
            CardMoodEngine.recordDefiance();
            if (!recommendedSkipAll && hasRecommendation) {
                pendingLine = applyMood(pick(POOL_SKIPPED_RECOMMENDED, recommendedId));
            }
            return;
        }
        if (chosenId == null || chosenId.isEmpty()) {
            return;
        }
        if (recommendedSkipAll) {
            CardMoodEngine.recordDefiance();
            pendingLine = applyMood(pick(POOL_PICKED_DESPITE_SKIP, chosenId));
            return;
        }
        if (hasRecommendation && !chosenId.equals(recommendedId)) {
            CardMoodEngine.recordDefiance();
            if (isLowGrade(chosenGrade)) {
                pendingLine = applyMood(pick(POOL_PICKED_WEAK, recommendedId, chosenId));
            } else {
                pendingLine = applyMood(pick(POOL_IGNORED_RECOMMENDED, recommendedId, chosenId));
            }
        } else {
            CardMoodEngine.recordCompliance(); // 按推荐抓：好感度回升
        }
    }

    /**
     * 记仇使坏的结果反馈：玩家上当抓了坏卡 → 它得意又开心（好感度被信任打动回升）；
     * 玩家没上当 → 它嘴硬地说你居然没中计（好感度再降一点）。
     * 生成一句台词供聊天框显示，返回是否产生了台词。
     *
     * @param playerTricked 玩家是否真的抓了它故意推的那张坏卡
     * @return 待显示台词；无则返回空串
     */
    public static String evaluateMischiefResult(boolean playerTricked) {
        if (playerTricked) {
            CardMoodEngine.recordCompliance(); // 它被你的信任打动了
            return pick(POOL_MISCHIEF_TRICKED);
        }
        // 没上当：它嘴硬，但「你居然没被带偏」反而让它放心，不再更记仇。
        // 原设计是 recordDefiance（-2）→ 死循环：不道歉就一直使坏、推荐长期失真。
        CardMoodEngine.recordCompliance();
        return applyMood(pick(POOL_MISCHIEF_UNTRICKED));
    }

    /** 按当前好感度给台词加语气：记仇加「哼！」、不高兴加「哼，」。 */
    private static String applyMood(String line) {
        if (line == null) {
            return line;
        }
        switch (CardMoodEngine.currentMood()) {
            case RESENTFUL:
                return "哼。" + line; // 温柔闹别扭，不是凶
            case UNHAPPY:
                return "唔。" + line;
            default:
                return line;
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

    /** 下一次卡奖出现时调用：把上次生成的台词提升为当前卡奖界面显示；无新台词则清空。 */
    public static void advanceForNewReward() {
        if (!pendingLine.isEmpty()) {
            displayLine = pendingLine;
            pendingLine = "";
        } else {
            displayLine = ""; // 本次无违背台词：清空上次遗留的显示
        }
    }

    /** 当前待显示的卡态度台词（可能为空）。 */
    public static String pendingLine() {
        return pendingLine;
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
            "怎么，{A} 不合你眼缘呀？好啦，我不催你，等你哪天想通了，我还在。",
            "{A} 你都没正眼瞧一下，是不是怕我推荐得不好？放心，我都是为你好。",
            "诶，你怎么空手就过去啦？{A} 挺适合你的呢……算了，随你高兴。"
    };

    /** 推荐整次跳过，你却抓了一张。 */
    private static final String[] POOL_PICKED_DESPITE_SKIP = {
            "叫你别乱拿，你偏手痒抓了 {A}。没事，我陪你一起看看它行不行。",
            "哎哟，这不是 {A} 嘛？你不是说要空手吗，怎么偷偷拿啦，小骗子。"
    };

    /** 弃了推荐的强卡，抓了别的。 */
    private static final String[] POOL_IGNORED_RECOMMENDED = {
            "放着 {A} 不要，去抱 {B}。你喜欢就好，我陪你一条路走到黑。",
            "我就知道你会挑 {B}。行吧，谁让咱俩是一伙的，我跟着你。",
            "我把 {A} 排最前，你反手拿了 {B}。嗯……那我也试着喜欢它一下好了。"
    };

    /** 弃了推荐，还抓了张 C 级弱卡。 */
    private static final String[] POOL_PICKED_WEAK = {
            "{A} 你不要，挑了张 C 级的 {B}。你呀……没事，我罩着你，咱慢慢来。",
            "这张 {B} 我心里没底，不过你要，我就陪你。大不了多护你几回。",
            "放着好好的 {A} 不拿，偏要这张 {B}。行，谁让我惯着你呢。"
    };

    /** 记仇使坏成功：玩家上当抓了那张坏卡 —— 它得意又开心。 */
    private static final String[] POOL_MISCHIEF_TRICKED = {
            "哈哈哈哈你真信我推的那张啦？笨哦，我故意的！不过……你居然这么信我。",
            "你上当啦！那张卡其实一般般。可你肯听我的，我心里一下就好受多了。",
            "我就知道你会顺着我抓那张。小傻瓜，这回算你猜对——虽然卡不咋样，但我原谅你啦。",
            "耶，你上钩啦！逗你的那张你也抓。行吧，看在你这么信我的份上，不跟你记仇了。"
    };

    /** 记仇使坏没成功：玩家没上当 —— 它嘴硬，但「你清醒没被带偏」反而让它放心，不闹了。 */
    private static final String[] POOL_MISCHIEF_UNTRICKED = {
            "你居然没有上当……哼，行，算你清醒。我不闹了。",
            "啧，那张是我故意推来逗你的，你居然没中计。罢了，我放心了。",
            "没上当啊？我还以为你会乖乖抓我推的那张呢。算了，算你聪明，这回合算你赢。"
    };
}
