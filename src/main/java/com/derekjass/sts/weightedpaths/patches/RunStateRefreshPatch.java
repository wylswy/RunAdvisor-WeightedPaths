package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.actions.common.UpgradeRandomCardAction;
import com.megacrit.cardcrawl.actions.common.UpgradeSpecificCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import javassist.CannotCompileException;
import javassist.CtBehavior;

/**
 * 血量、卡组、升级变化后重新评估路线（不再沿用战前固定权重）。
 */
public class RunStateRefreshPatch {

    private RunStateRefreshPatch() {
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "damage")
    @SpirePatch(clz = AbstractPlayer.class, method = "heal")
    public static class HpChangedPatch {

        @SpirePostfixPatch
        public static void afterHpChanged(AbstractPlayer __instance) {
            refreshIfInRun();
        }
    }

    @SpirePatch(clz = CardGroup.class, method = "addToBottom")
    @SpirePatch(clz = CardGroup.class, method = "addToTop")
    public static class MasterDeckChangedPatch {

        @SpirePostfixPatch
        public static void afterMasterDeckCardAdded(CardGroup __instance, AbstractCard c) {
            if (__instance.type == CardGroup.CardGroupType.MASTER_DECK) {
                refreshIfInRun();
            }
        }
    }

    @SpirePatch(clz = CardGroup.class, method = "removeCard", paramtypez = {AbstractCard.class})
    public static class MasterDeckCardRemovedPatch {

        @SpirePostfixPatch
        public static void afterMasterDeckCardRemoved(CardGroup __instance, AbstractCard c) {
            if (__instance.type == CardGroup.CardGroupType.MASTER_DECK) {
                refreshIfInRun();
            }
        }
    }

    /**
     * 不能 patch {@link AbstractCard#upgrade()}：其为 abstract，MTS 3.30 会在解析参数时 NPE。
     * 改在篝火/事件等实际调用 upgrade 的 Action 上插入。
     */
    @SpirePatch(clz = UpgradeSpecificCardAction.class, method = "update")
    @SpirePatch(clz = UpgradeRandomCardAction.class, method = "update")
    public static class CardUpgradedPatch {

        @SpireInsertPatch(locator = AfterUpgradeLocator.class)
        public static void afterUpgrade() {
            refreshIfInRun();
        }

        private static class AfterUpgradeLocator extends SpireInsertLocator {

            @Override
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher finalMatcher = new Matcher.MethodCallMatcher(AbstractCard.class, "superFlash");
                return LineFinder.findInOrder(ctMethodToPatch, finalMatcher);
            }
        }
    }

    private static void refreshIfInRun() {
        if (AbstractDungeon.player == null || AbstractDungeon.currMapNode == null) {
            return;
        }
        WeightedPaths.refreshPathValues();
    }
}
