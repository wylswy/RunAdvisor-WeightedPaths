package com.derekjass.sts.weightedpaths.creative;

/**
 * 「长期陪伴」逻辑层 —— 卡跨局记得玩家、越玩越熟（温暖向）。
 *
 * <p>与局内好感度 {@link CardMoodEngine} 不同：好感度记录「这一局」闹没闹别扭（SL 恢复）；
 * 这里的羁绊记录「你俩长期的关系」，跨局累积、新局不清空。它只影响卡"怎么说话"（开场白、
 * 亲昵程度），绝不进入推荐/评分逻辑——推荐永远诚实。
 *
 * <p>关系阶段由「羁绊值 bonding」决定：每打完一局 +1（胜利再多 +1），每跟卡聊一句 +1。
 * 阶段越高，卡开场越熟络、越念旧。本类纯逻辑 + 少量模板台词，可单测。
 */
public final class PlayerRelation {

    /** 关系阶段（从陌生到好友）。 */
    public enum Tier {
        STRANGER("陌生人"),
        ACQUAINTANCE("熟识"),
        FRIEND("默契"),
        CLOSE("好友");

        public final String label;

        Tier(String label) {
            this.label = label;
        }
    }

    /** 羁绊值 → 阶段阈值（bonding 达到即升一级）。 */
    private static final int[] TIER_THRESHOLDS = {3, 8, 15};

    private static PlayerRelation instance;

    private RelationPersistence.RelationData data;

    private PlayerRelation() {
    }

    /** 懒加载单例：读取跨局档案；首次运行/无档案则从零开始。 */
    public static PlayerRelation get() {
        if (instance == null) {
            instance = new PlayerRelation();
            RelationPersistence.RelationData d = RelationPersistence.load();
            instance.data = d == null ? new RelationPersistence.RelationData() : d;
        }
        return instance;
    }

    /** 仅供测试重置单例（新测试各自重建，避免状态串扰）。 */
    public static void resetForTest() {
        instance = null;
    }

    /** 当前关系阶段。 */
    public Tier tier() {
        return tierFor(data.bonding);
    }

    /** 上次结算时的阶段索引（用于识别"刚升级"）。 */
    public int lastTierIndex() {
        return data.lastTierIndex;
    }

    public int bonding() {
        return data.bonding;
    }

    public int totalRuns() {
        return data.totalRuns;
    }

    public int totalVictories() {
        return data.totalVictories;
    }

    public int maxFloorReached() {
        return data.maxFloorReached;
    }

    /** 仅供测试：当前是否挂起"刚升级"标志。 */
    public boolean pendingUpgradeForTest() {
        return data.pendingUpgrade;
    }

    /** 仅供测试：上把记忆里的抓牌列表。 */
    public java.util.List<String> pickedCardIdsForTest() {
        if (data.lastRunSummary == null) {
            return java.util.Collections.emptyList();
        }
        return data.lastRunSummary.pickedCardIds;
    }

    /**
     * 一局结束结算：记录卡真正记得的"上把发生的事"，累加羁绊与统计，落盘。
     * 若本局让关系升级，返回 true（新局开场可给惊喜台词）。
     *
     * @param victory        本局是否胜利
     * @param floorReached   本局到达的最高层
     * @param chatCount      本局玩家发的聊天条数（计入羁绊）
     * @param pickedCardIds  本局玩家抓过的卡（中文名，供卡真实引用）
     * @param chatHighlights 本局玩家说过的话（供卡真实引用）
     */
    public boolean settleRun(boolean victory, int floorReached, int chatCount,
                             java.util.List<String> pickedCardIds,
                             java.util.List<String> chatHighlights) {
        int oldTierIndex = tier().ordinal();
        data.totalRuns++;
        if (victory) {
            data.totalVictories++;
        }
        data.maxFloorReached = Math.max(data.maxFloorReached, floorReached);
        data.bonding += 1 + (victory ? 1 : 0) + Math.max(0, chatCount);
        data.lastTierIndex = tierFor(data.bonding).ordinal();
        // 真记得：存下"上把发生的事"，卡后续开场/聊天可真实引用
        RelationPersistence.LastRunSummary summary = new RelationPersistence.LastRunSummary();
        summary.floorReached = floorReached;
        summary.victory = victory;
        if (pickedCardIds != null) {
            for (String c : pickedCardIds) {
                if (c != null && !c.trim().isEmpty()) {
                    summary.pickedCardIds.add(c.trim());
                }
            }
        }
        if (chatHighlights != null) {
            for (String c : chatHighlights) {
                if (c != null && !c.trim().isEmpty()) {
                    summary.chatHighlights.add(c.trim());
                }
            }
        }
        data.lastRunSummary = summary;
        // 关系升级：挂起"刚升级"标志，供下一局开场显摆一次
        if (tierFor(data.bonding).ordinal() > oldTierIndex) {
            data.pendingUpgrade = true;
        }
        RelationPersistence.save(data);
        return data.pendingUpgrade;
    }

    /**
     * 新局开场白：卡根据记忆真实引用"上把发生的事"（真记得），否则按关系阶段客套。
     * 若刚升过级，优先给"我们认识这么久了"的惊喜台词（消费后清除标志）。
     *
     * @return 一句中文开场白
     */
    public String greeting() {
        Tier t = tier();
        if (data.pendingUpgrade && t.ordinal() > 0) {
            data.pendingUpgrade = false;
            RelationPersistence.save(data);
            return pick(UPGRADE_LINES[t.ordinal() - 1]);
        }
        String memory = memoryGreeting();
        if (memory != null) {
            return memory;
        }
        return pick(GREETING_LINES[t.ordinal()]);
    }

    /** 基于"上把记忆"生成开场白；没有可引用的真实记忆返回 null。 */
    private String memoryGreeting() {
        RelationPersistence.LastRunSummary s = data.lastRunSummary;
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (s.victory) {
            sb.append("上把你赢了，我可记着呢。这局咱接着冲。");
        } else if (s.floorReached > 0) {
            sb.append("上把你栽在第").append(s.floorReached).append("层，我记得。这局我陪你翻盘。");
        }
        if (!s.pickedCardIds.isEmpty()) {
            if (sb.length() == 0) {
                sb.append("你上把抓的").append(s.pickedCardIds.get(0)).append("，我可都记着呢。");
            } else {
                sb.append("还有你上把抓的").append(s.pickedCardIds.get(0)).append("，我也都记着。");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 卡的"真记得"记忆文本，注入聊天提示词让 AI 能真实引用（并守诚实护栏）。
     * 没有记忆则返回空串。
     */
    public String memoryText() {
        RelationPersistence.LastRunSummary s = data.lastRunSummary;
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("这是你真正记得的玩家上把发生的事：");
        sb.append(s.victory ? "他上把赢了" : "他上把在第" + s.floorReached + "层栽了");
        if (!s.pickedCardIds.isEmpty()) {
            sb.append("；他上把抓了：").append(String.join("、", s.pickedCardIds));
        }
        if (!s.chatHighlights.isEmpty()) {
            sb.append("；他上把对你说过：").append(String.join("；", s.chatHighlights));
        }
        sb.append("。你只准引用这些真事；玩家问到你不记得的，老实说'这个我真记不清了'，绝不编造。");
        return sb.toString();
    }

    private static Tier tierFor(int bonding) {
        int idx = 0;
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (bonding >= TIER_THRESHOLDS[i]) {
                idx = i + 1;
            }
        }
        return Tier.values()[idx];
    }

    private static String pick(String[] pool) {
        return pool[(int) (Math.random() * pool.length)];
    }

    /** 各阶段的开场白（温暖向，卡认得出你）。 */
    private static final String[][] GREETING_LINES = {
            // STRANGER：刚认识，客气
            {"嗨，我们是……刚认识对吧？我是这张会陪你的卡，这局多多关照呀。",
             "你好呀。虽然咱俩还没怎么打过交道，但我已经决定要护着你啦。"},
            // ACQUAINTANCE：认得出你，提上局
            {"你又来啦。上回咱俩配合得还行，这次继续？",
             "我记得你——上局没白陪我聊那么久。这局我也在。"},
            // FRIEND：熟络，会开玩笑
            {"哟，回来啦。我都记着你呢——你老爱不听劝，不过我也乐意惯着你。",
             "又见面了。你什么脾气我还不知道？反正我认了，谁让咱熟呢。"},
            // CLOSE：特别亲，念旧
            {"你来了。认识你这么久，哪一局不是陪你走到底的？这局也交给我。",
             "老朋友，好久不见。你打的每一局我都记在心里，这局咱们接着走。"}
    };

    /** 刚升级时的惊喜台词（index 对齐 ACQUAINTANCE/FRIEND/CLOSE）。 */
    private static final String[][] UPGRADE_LINES = {
            {"诶，这才认识你几局，怎么就觉得离不开了呢？",
             "不知不觉，我们好像开始熟起来了。以后的路，我陪你慢慢走。"},
            {"原来咱们已经这么默契了。你不说，我也懂你要什么。",
             "默契感来了——你一个眼神，我都知道你想抓哪张。"},
            {"认识你这么久，早就超过「朋友」两个字了。这局，我豁出去护你。",
             "这么多年……不对，这么多局走过来，咱俩早就是一伙的了。你来，我就在。"}
    };
}
