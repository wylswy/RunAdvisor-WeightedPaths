package com.derekjass.sts.weightedpaths.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.derekjass.sts.weightedpaths.card.CardRecommendation;
import com.derekjass.sts.weightedpaths.card.CardScorer;
import com.derekjass.sts.weightedpaths.card.GlobalRunPlan;
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
    private static String lastRewardLogKey = "";

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
            return;
        }
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, label, cx, cy, color);
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
}
