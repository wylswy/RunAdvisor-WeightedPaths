package com.derekjass.sts.weightedpaths.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.card.CardRecommendation;
import com.derekjass.sts.weightedpaths.card.CardScorer;
import com.derekjass.sts.weightedpaths.card.GlobalRunPlan;
import com.derekjass.sts.weightedpaths.creative.CardAttitudeEngine;
import com.derekjass.sts.weightedpaths.logging.RunAdvisorLogger;
import com.derekjass.sts.weightedpaths.logging.RunLogModels;
import com.derekjass.sts.weightedpaths.card.data.CardStatsLoader;
import com.derekjass.sts.weightedpaths.ui.CardUiStrings;
import com.derekjass.sts.weightedpaths.ui.ModFonts;
import com.derekjass.sts.weightedpaths.ui.config.Config;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.TheSilent;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.screens.CardRewardScreen;

import java.util.List;

public class CardRewardRenderPatch {

    private static final Color GRADE_S = new Color(1.0f, 0.85f, 0.2f, 1.0f);
    private static final Color GRADE_A = new Color(0.45f, 0.95f, 0.45f, 1.0f);
    private static final Color GRADE_B = new Color(0.85f, 0.85f, 0.85f, 1.0f);
    private static final Color GRADE_C = new Color(0.95f, 0.45f, 0.45f, 1.0f);
    private static final Color RECOMMENDED_COLOR = new Color(0.35f, 1.0f, 0.55f, 1.0f);
    private static final Color SKIP_HINT_COLOR = new Color(1.0f, 0.72f, 0.35f, 1.0f);
    // 卡的态度台词颜色（暖黄，区别于评分文字）
    private static final Color ATTITUDE_COLOR = new Color(0.98f, 0.82f, 0.35f, 1.0f);
    // 抓牌历史"已有 N 张"的次要文字色
    private static final Color HAVING_COLOR = new Color(0.78f, 0.78f, 0.78f, 1.0f);
    private static String lastRewardLogKey = "";
    /** 本次卡奖的候选卡 ID 集合（供 onClose 回填玩家实际选择）。 */
    private static java.util.Set<String> lastRewardCardIds = new java.util.HashSet<>();
    /** 卡奖出现时玩家牌组各卡数量（供 onClose 对比新增）。 */
    private static java.util.Map<String, Integer> lastDeckCountBefore = new java.util.HashMap<>();
    /** 本次卡奖推荐抓的卡 ID（供 onClose 检测违背）。 */
    private static String lastRecommendedId = "";
    /** 本次卡奖是否推荐整次跳过。 */
    private static boolean lastSkipAll = false;
    /** 本次卡奖各候选卡的 grade（供 onClose 判断弃高抓低）。 */
    private static final java.util.Map<String, String> lastRewardGrades = new java.util.HashMap<>();

    public static void resetRewardLogCache() {
        lastRewardLogKey = "";
    }

    private CardRewardRenderPatch() {
    }

    @SpirePatch(clz = CardRewardScreen.class, method = "render")
    public static class PostRenderPatch {

        @SpirePostfixPatch
        public static void afterRender(CardRewardScreen __instance, SpriteBatch sb) {
            if (!Config.showCardScores()) {
                return;
            }
            if (!CardStatsLoader.isLoaded()) {
                return;
            }
            if (AbstractDungeon.player == null || !(AbstractDungeon.player instanceof TheSilent)) {
                return;
            }
            if (__instance.rewardGroup == null || __instance.rewardGroup.isEmpty()) {
                return;
            }

            List<AbstractCard> deck = AbstractDungeon.player.masterDeck.group;
            GlobalRunPlan plan = GlobalRunPlan.fromCurrentRun();
            java.util.HashMap<AbstractCard, CardRecommendation> scored = new java.util.HashMap<>();
            java.util.HashMap<AbstractCard, CardScorer.ScoredResult> detailed = new java.util.HashMap<>();
            double bestScore = 0.0;
            AbstractCard bestCard = null;

            for (AbstractCard card : __instance.rewardGroup) {
                if (card == null) {
                    continue;
                }
                CardScorer.ScoredResult result = CardScorer.scoreRewardOptionDetailed(card, deck, plan);
                detailed.put(card, result);
                scored.put(card, result.recommendation);
                if (result.recommendation.score > bestScore) {
                    bestScore = result.recommendation.score;
                    bestCard = card;
                }
            }

            boolean skipAll = bestScore < com.derekjass.sts.weightedpaths.card.FourLayerScorer.SKIP_THRESHOLD;
            // 三选一里最高分至少显示 B，避免「全 C 无指引」
            if (!skipAll && bestCard != null) {
                CardRecommendation bestRec = scored.get(bestCard);
                if (bestRec != null && bestRec.grade == com.derekjass.sts.weightedpaths.card.CardGrade.C
                        && bestScore >= 38.0) {
                    scored.put(bestCard, new CardRecommendation(
                            com.derekjass.sts.weightedpaths.card.CardGrade.B, bestRec.score, bestRec.reason));
                }
            }
            maybeLogCardReward(__instance, detailed, bestCard, skipAll);

            for (AbstractCard card : __instance.rewardGroup) {
                if (card == null) {
                    continue;
                }
                CardRecommendation rec = scored.get(card);
                if (rec == null) {
                    continue;
                }
                boolean isPick = !skipAll && card == bestCard;
                drawRecommendation(sb, card, rec, isPick);
            }

            if (skipAll) {
                drawSkipHint(sb);
            }

            // 卡的态度：显示上一次卡奖违背推荐时留下的台词
            if (CardAttitudeEngine.hasDisplay()) {
                drawAttitudeLine(sb, CardAttitudeEngine.displayLine());
            }
        }
    }

    private static void maybeLogCardReward(
            CardRewardScreen screen,
            java.util.HashMap<AbstractCard, CardScorer.ScoredResult> detailed,
            AbstractCard bestCard,
            boolean skipAll) {
        if (!RunAdvisorLogger.isEnabled()) {
            return;
        }
        RunAdvisorLogger.ensureSession();
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(AbstractDungeon.floorNum).append(':');
        for (AbstractCard card : screen.rewardGroup) {
            if (card != null) {
                keyBuilder.append(card.cardID).append('|');
            }
        }
        String key = keyBuilder.toString();
        if (key.equals(lastRewardLogKey)) {
            return;
        }
        lastRewardLogKey = key;
        // 记录本次卡奖上下文，供 onClose 时对比玩家实际抓卡
        lastRewardCardIds = new java.util.HashSet<>();
        lastDeckCountBefore = new java.util.HashMap<>();
        for (AbstractCard card : screen.rewardGroup) {
            if (card != null) {
                lastRewardCardIds.add(card.cardID);
            }
        }
        if (AbstractDungeon.player != null && AbstractDungeon.player.masterDeck != null) {
            for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
                if (c != null) {
                    lastDeckCountBefore.merge(c.cardID, 1, Integer::sum);
                }
            }
        }
        // 记录推荐信息（供 onClose 检测玩家是否违背）；并推进「卡的态度」台词显示
        lastRecommendedId = bestCard == null ? "" : bestCard.cardID;
        lastSkipAll = skipAll;
        lastRewardGrades.clear();
        for (AbstractCard card : screen.rewardGroup) {
            if (card == null) {
                continue;
            }
            CardScorer.ScoredResult r = detailed.get(card);
            if (r != null) {
                lastRewardGrades.put(card.cardID, r.recommendation.grade.name());
            }
        }
        CardAttitudeEngine.advanceForNewReward();

        java.util.ArrayList<RunLogModels.CardChoiceLog> choices = new java.util.ArrayList<>();
        int index = 0;
        for (AbstractCard card : screen.rewardGroup) {
            if (card == null) {
                continue;
            }
            CardScorer.ScoredResult result = detailed.get(card);
            if (result == null) {
                continue;
            }
            boolean recommended = !skipAll && card == bestCard;
            choices.add(RunAdvisorLogger.choiceLog(
                    index,
                    card.cardID,
                    result.recommendation.grade.name(),
                    result.recommendation.score,
                    recommended,
                    result.breakdown));
            index++;
        }
        RunAdvisorLogger.logCardReward(skipAll, choices);
    }

    private static void drawRecommendation(
            SpriteBatch sb, AbstractCard card, CardRecommendation rec, boolean recommended) {
        float cx = card.hb.cX;
        float cy = card.hb.cY + card.hb.height + (36.0f * Settings.scale);
        String label = recommended ? CardUiStrings.PICK_RECOMMENDED : rec.gradeLabel();
        Color color = recommended ? RECOMMENDED_COLOR : gradeColor(rec.grade);

        if (ModFonts.header != null) {
            FontHelper.renderFontCentered(sb, ModFonts.header, label, cx, cy, color);
        } else {
            FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, label, cx, cy, color);
        }

        // V2 蓝图·第一层抓牌历史：显示卡组已有同名牌数
        int existing = countInDeck(card.cardID);
        if (existing > 0) {
            String having = String.format(CardUiStrings.HAVING_COUNT, existing);
            float cy2 = cy - (22.0f * Settings.scale);
            if (ModFonts.body != null) {
                FontHelper.renderFontCentered(sb, ModFonts.body, having, cx, cy2, HAVING_COLOR);
            } else {
                FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, having, cx, cy2, HAVING_COLOR);
            }
        }
    }

    private static int countInDeck(String cardId) {
        if (cardId == null) {
            return 0;
        }
        int count = 0;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c != null && cardId.equals(c.cardID)) {
                count++;
            }
        }
        return count;
    }

    private static void drawSkipHint(SpriteBatch sb) {
        float cx = Settings.WIDTH / 2.0f;
        float cy = 80.0f * Settings.scale;
        if (ModFonts.body != null) {
            FontHelper.renderFontCentered(sb, ModFonts.body, CardUiStrings.SKIP_ALL_HINT, cx, cy, SKIP_HINT_COLOR);
            return;
        }
        FontHelper.renderFontCentered(
                sb, FontHelper.cardTitleFont, CardUiStrings.SKIP_ALL_HINT, cx, cy, SKIP_HINT_COLOR);
    }

    /** 在卡奖界面顶部绘制卡的态度台词（暖黄色，醒目）。 */
    private static void drawAttitudeLine(SpriteBatch sb, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float cx = Settings.WIDTH / 2.0f;
        float cy = Settings.HEIGHT - (120.0f * Settings.scale);
        if (ModFonts.header != null) {
            FontHelper.renderFontCentered(sb, ModFonts.header, text, cx, cy, ATTITUDE_COLOR);
        } else {
            FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, text, cx, cy, ATTITUDE_COLOR);
        }
    }

    private static Color gradeColor(com.derekjass.sts.weightedpaths.card.CardGrade grade) {
        switch (grade) {
            case S:
                return GRADE_S;
            case A:
                return GRADE_A;
            case B:
                return GRADE_B;
            case C:
            default:
                return GRADE_C;
        }
    }

    @SpirePatch(clz = CardRewardScreen.class, method = "onClose")
    public static class PostClosePatch {
        @SpirePostfixPatch
        public static void afterClose(CardRewardScreen __instance) {
            if (AbstractDungeon.player == null) {
                return;
            }
            String chosen = resolveChosenFromDeck();
            if (RunAdvisorLogger.isEnabled()) {
                RunAdvisorLogger.logPlayerCardChoice(chosen, chosen.isEmpty());
            }
            // 卡的态度：检测本次卡奖是否违背推荐，生成待显示台词
            String chosenGrade = (chosen == null || chosen.isEmpty())
                    ? "" : lastRewardGrades.getOrDefault(chosen, "");
            CardAttitudeEngine.evaluateReward(
                    lastRecommendedId, lastSkipAll, chosen, chosen.isEmpty(), chosenGrade);
        }
    }

    /** 对比牌组，推断玩家在最近一次卡奖实际抓的卡；无新增则返回空串（跳过）。 */
    private static String resolveChosenFromDeck() {
        if (AbstractDungeon.player == null || AbstractDungeon.player.masterDeck == null) {
            return "";
        }
        java.util.Map<String, Integer> now = new java.util.HashMap<>();
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c != null) {
                now.merge(c.cardID, 1, Integer::sum);
            }
        }
        for (java.util.Map.Entry<String, Integer> e : now.entrySet()) {
            String id = e.getKey();
            if (!lastRewardCardIds.contains(id)) {
                continue;
            }
            int before = lastDeckCountBefore.getOrDefault(id, 0);
            if (e.getValue() > before) {
                return id;
            }
        }
        return "";
    }
}
