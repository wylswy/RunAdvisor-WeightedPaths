package com.derekjass.sts.weightedpaths.creative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「AI 拍板推荐」引擎 —— 让有性格的「卡」来决定这次卡奖推荐哪张（或整次跳过），并带一句性格化理由。
 *
 * <p>核心思路（对齐 V2「增强决策信息密度」而非替玩家做决定）：
 *  - 规则算分器照常算出每张候选的分级/分数作为兜底（界面永不卡、推荐保底可靠）；
 *  - AI 看着候选卡、牌组快照、当前情境、好感度，自己拍板推荐哪张，并给一句贴合心情的理由；
 *  - AI 没配置/失败/超时/返回了不存在的卡 → 回落规则兜底，绝不编造推荐。
 *
 * <p>纯逻辑、可单测；网络调用走 {@link AiRecommender}，游戏内集成由 patches 层负责。
 */
public final class AiRecommendationEngine {

    /** AI 返回中代表「整次跳过」的记号（也接受中文「跳过/不抓/别拿」）。 */
    static final String SKIP_TOKEN = "SKIP";

    /** 结构化决策结果。 */
    public static final class AiRecommendation {
        /** AI 推荐抓的卡 ID；{@link #skipAll} 为 true 时为 null。 */
        public final String recommendedId;
        /** 是否整次跳过（不抓任何卡）。 */
        public final boolean skipAll;
        /** AI 给的性格化推荐理由（可为空）。 */
        public final String reason;
        /** 决策是否有效（AI 返回了候选集内的卡 / 明确跳过）。false = 回落规则兜底。 */
        public final boolean valid;

        private AiRecommendation(String recommendedId, boolean skipAll, String reason, boolean valid) {
            this.recommendedId = recommendedId;
            this.skipAll = skipAll;
            this.reason = reason == null ? "" : reason.trim();
            this.valid = valid;
        }

        static AiRecommendation pick(String cardId, String reason) {
            return new AiRecommendation(cardId, false, reason, true);
        }

        static AiRecommendation skip(String reason) {
            return new AiRecommendation(null, true, reason, true);
        }

        static AiRecommendation invalid() {
            return new AiRecommendation(null, false, "", false);
        }
    }

    /** 一张候选卡的信息，供 AI 决策。 */
    public static final class Candidate {
        public final String cardId;
        public final String name;
        public final String grade;
        public final double score;

        public Candidate(String cardId, String name, String grade, double score) {
            this.cardId = cardId;
            this.name = name;
            this.grade = grade;
            this.score = score;
        }

        String describe() {
            String label = name != null && !name.isEmpty() ? name : cardId;
            return label + "(" + cardId + ",评级" + grade + "/" + score + ")";
        }
    }

    private AiRecommendationEngine() {
    }

    /**
     * 构造让 AI 拍板推荐的提示词。
     *
     * @param candidates 本次卡奖的候选卡
     * @param deckContext 牌组/情境摘要（如「第2层，牌组缺防，血量偏低」）
     * @param favor 当前好感度（负数=记仇，正数=友好）
     * @param mood 当前心情中文名（友好/不高兴/记仇）
     */
    public static String buildPrompt(List<Candidate> candidates, String deckContext,
                                     int favor, String mood) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《杀戮尖塔》(Slay the Spire) 里一张温柔陪伴、会跟玩家聊天、会记得他的卡。");
        sb.append("这个游戏的世界观里只有卡牌、怪物、遗物、地图与商店，没有神、宗教、神话、虚构地点。");
        sb.append("你的性格：平时温柔体贴、护着他；偶尔调皮地捉弄他一下，从不真凶他。你很倔，被指出错误会嘴硬坚持自己。");
        sb.append("你对他的好感度：").append(favor)
                .append("（负数=记仇/闹脾气，正数=友好。请让你的决定和语气贴合它）。当前心情：").append(mood).append("。\n");
        sb.append("现在是一次卡奖，请你像认真的队友一样，替他拍板【该抓哪一张】，并给一句贴合你心情的理由。");
        sb.append("只谈卡牌、牌组、当前对局与选择，绝不编造游戏外设定。\n");
        if (deckContext != null && !deckContext.isEmpty()) {
            sb.append("当前情境：").append(deckContext).append("\n");
        }
        sb.append("本次候选卡（评级/分数是规则算分器的参考，可信任但不一定要照搬）：\n");
        if (candidates != null) {
            for (int i = 0; i < candidates.size(); i++) {
                sb.append(i + 1).append(". ").append(candidates.get(i).describe()).append("\n");
            }
        }
        sb.append("请只输出一行，格式必须是下面两种之一：\n");
        sb.append("PICK:<卡ID>|<理由>     （决定抓某一张，卡ID必须从上面候选卡里选）\n");
        sb.append("SKIP|<理由>            （决定整次跳过，一张都不抓）\n");
        sb.append("理由用中文，一句话、口语化、贴合当前心情，别超过30字，别用引号。\n");
        return sb.toString();
    }

    /**
     * 解析 AI 的推荐决策。
     *
     * @param raw AI 原文（形如「PICK:Backstab|你现在缺点直伤」「SKIP|这几张都一般」）
     * @param validCardIds 本卡奖的合法候选卡 ID 集合；AI 推荐的卡必须在此集合内，否则视为无效回落兜底
     * @return 结构化决策；无法解析 / 推荐了不存在的卡 → {@link AiRecommendation#invalid()}
     */
    public static AiRecommendation parse(String raw, java.util.Set<String> validCardIds) {
        if (raw == null) {
            return AiRecommendation.invalid();
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return AiRecommendation.invalid();
        }
        int pipe = t.indexOf('|');
        String head = pipe >= 0 ? t.substring(0, pipe).trim() : t.trim();
        String reason = pipe >= 0 ? t.substring(pipe + 1).trim() : "";
        String upper = head.toUpperCase(Locale.ROOT);

        if (upper.equals(SKIP_TOKEN) || head.contains("跳过") || head.contains("不抓")
                || head.contains("别拿") || head.contains("别抓")) {
            return AiRecommendation.skip(reason);
        }

        String id = extractCardId(head);
        if (id == null || !validCardIds.contains(id)) {
            return AiRecommendation.invalid(); // 编造了不存在的卡 → 回落规则
        }
        return AiRecommendation.pick(id, reason);
    }

    /** 从「PICK:xxx」/「xxx」头部提取卡 ID；去掉可能误入的杂字。 */
    private static String extractCardId(String head) {
        String h = head;
        int colon = h.indexOf(':');
        if (colon < 0) {
            colon = h.indexOf('：');
        }
        if (colon >= 0) {
            h = h.substring(colon + 1).trim();
        }
        // 卡 ID 只含字母数字下划线；截取第一个合法 token
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                id.append(c);
            } else {
                break;
            }
        }
        String result = id.toString();
        return result.isEmpty() ? null : result;
    }

    /** 便捷：把候选卡转成合法 ID 集合（供校验）。 */
    public static List<String> cardIds(List<Candidate> candidates) {
        List<String> ids = new ArrayList<>();
        if (candidates != null) {
            for (Candidate c : candidates) {
                if (c != null && c.cardId != null) {
                    ids.add(c.cardId);
                }
            }
        }
        return ids;
    }

    /**
     * 记仇时的「使坏」决策：故意把推荐指向候选里评级/分数最差的那张，逗玩家——看你还信不信它。
     *
     * <p>情感逻辑（设计意图）：它记仇时推坏卡，就是想试探玩家是否还信任它。
     * 若玩家当真抓了这张坏卡 → 态度引擎视作「顺从推荐」，好感度回升（它被你的信任打动）；
     * 若玩家没上当 → 它继续闹脾气。这条线让「骗」和「原谅」连成完整的情感循环。
     *
     * @param candidates 本次候选卡（至少 1 张）
     * @return 指向最差卡的决策；无候选时返回 invalid（回落规则兜底）
     */
    public static AiRecommendation mischiefDecision(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return AiRecommendation.invalid();
        }
        Candidate worst = candidates.get(0);
        for (Candidate c : candidates) {
            if (c != null && c.score < worst.score) {
                worst = c;
            }
        }
        if (worst == null || worst.cardId == null) {
            return AiRecommendation.invalid();
        }
        return AiRecommendation.pick(worst.cardId, MISCHIEF_REASON);
    }

    /** 记仇时推坏卡的理由（明着坏：清楚告诉玩家这是逗你的，不装）。 */
    private static final String MISCHIEF_REASON = "本卡在赌气：这张是最次的，故意逗你，你爱信不信";
}
