package com.derekjass.sts.weightedpaths.card;

/**
 * 评分所需的运行时卡信息（从游戏卡 AbstractCard 抽取，仅含评分逻辑用到的字段）。
 *
 * <p>解耦目的：{@link FourLayerScorer} 不再直接依赖 {@code com.megacrit.cardcrawl.cards.AbstractCard}
 * （该游戏类在纯 JUnit 环境静态初始化会 NPE，导致评分器无法单元测试）。
 * 游戏运行时由 {@code CardScorer} 从真实卡抽取本对象；测试可直接 new 一个轻量实例。
 *
 * <p>行为零变化：字段值与真实卡逐一对齐。
 */
public final class CardInfo {

    /** 卡牌 ID，如 "Catalyst"。 */
    public final String cardId;
    /** 本回合费用（layer4 污染判断用）。 */
    public final int costForTurn;

    public CardInfo(String cardId, int costForTurn) {
        this.cardId = cardId;
        this.costForTurn = costForTurn;
    }
}
