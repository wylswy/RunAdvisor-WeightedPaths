package com.derekjass.sts.weightedpaths.patches;

import com.derekjass.sts.weightedpaths.creative.ChatBoxCore;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.derekjass.sts.weightedpaths.creative.PlayerRelation;
import com.derekjass.sts.weightedpaths.logging.RunAdvisorLogger;
import com.derekjass.sts.weightedpaths.patches.CardRewardRenderPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;

/** 对局结束写入 run_summary.json + 结算长期陪伴关系档案。 */
public class RunLifecyclePatch {

    private RunLifecyclePatch() {
    }

    /** 采集本局玩家说过的话（最近最多 3 条非空），供卡真实引用。 */
    private static java.util.List<String> chatHighlights() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            for (ChatBoxCore.ChatMessage m : ChatBoxUi.get().core().messages()) {
                if (m.sender == ChatBoxCore.Sender.PLAYER && m.text != null && !m.text.trim().isEmpty()) {
                    out.add(m.text.trim());
                    if (out.size() >= 3) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * 结算跨局关系（须先于 RunAdvisorLogger.onRunEnd，否则 current 被清空拿不到抓牌）。
     * 抓牌转中文名存入档案；温暖向，只影响表达，不碰推荐。
     */
    private static void settleRelation(boolean victory) {
        try {
            java.util.List<String> picked = new java.util.ArrayList<>();
            for (String id : RunAdvisorLogger.currentPickedCardIds()) {
                String cn = CardRewardRenderPatch.chineseNameOf(id);
                if (cn != null && !cn.isEmpty()) {
                    picked.add(cn);
                }
            }
            PlayerRelation.get().settleRun(
                    victory, AbstractDungeon.floorNum,
                    ChatBoxUi.get().core().playerMessageCount(),
                    picked, chatHighlights());
        } catch (Exception ignored) {
            // 结算失败不致命
        }
    }

    @SpirePatch(
            clz = DeathScreen.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {MonsterGroup.class})
    public static class OnDeath {

        @SpirePostfixPatch
        public static void after(DeathScreen __instance, MonsterGroup mg) {
            settleRelation(false);
            RunAdvisorLogger.onRunEnd(false, "death");
        }
    }

    @SpirePatch(
            clz = VictoryScreen.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {MonsterGroup.class})
    public static class OnVictory {

        @SpirePostfixPatch
        public static void after(VictoryScreen __instance, MonsterGroup mg) {
            settleRelation(true);
            RunAdvisorLogger.onRunEnd(true, "victory");
        }
    }
}
