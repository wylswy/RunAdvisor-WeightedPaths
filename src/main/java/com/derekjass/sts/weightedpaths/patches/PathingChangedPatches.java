package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.WeightedPaths;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.derekjass.sts.weightedpaths.creative.PactManager;
import com.derekjass.sts.weightedpaths.paths.RouteFormatUtil;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;

@SpirePatch(clz = AbstractDungeon.class, method = "setCurrMapNode")
@SpirePatch(clz = CardCrawlGame.class, method = "getDungeon",
        paramtypez = {String.class, AbstractPlayer.class})
@SpirePatch(clz = CardCrawlGame.class, method = "getDungeon",
        paramtypez = {String.class, AbstractPlayer.class, SaveFile.class})
public class PathingChangedPatches {

    @SpirePostfixPatch
    public static void onPathingChanged() {
        if (AbstractDungeon.player == null) {
            return;
        }
        MapNodeLogPatch.logIfEnabled();
        WeightedPaths.regeneratePaths();
        // 契约：进入精英房间时结算 REACH_ELITE 契约（血量以进房瞬间计）
        try {
            MapRoomNode node = AbstractDungeon.getCurrMapNode();
            if (node != null && "E".equals(RouteFormatUtil.symbolOrEmpty(node))) {
                int hp = AbstractDungeon.player.maxHealth > 0
                        ? (int) (AbstractDungeon.player.currentHealth * 100L / AbstractDungeon.player.maxHealth)
                        : 100;
                String msg = PactManager.onEliteReached(hp);
                if (!msg.isEmpty()) {
                    ChatBoxUi.get().core().addCardMessage(msg);
                }
            }
        } catch (Exception ignored) {
            // 检测失败不阻断游戏
        }
    }

}