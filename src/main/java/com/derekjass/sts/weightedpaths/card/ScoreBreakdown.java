package com.derekjass.sts.weightedpaths.card;

/** 四层评分明细，供决策日志与调参分析。 */
public final class ScoreBreakdown {

    public final double baseScore;
    public final double survivalBonus;
    public final double portMult;
    public final double synergyMult;
    public final double pollutionMult;
    public final double calibrationMult;
    public final double finalScore;

    public ScoreBreakdown(
            double baseScore,
            double survivalBonus,
            double portMult,
            double synergyMult,
            double pollutionMult,
            double calibrationMult,
            double finalScore) {
        this.baseScore = baseScore;
        this.survivalBonus = survivalBonus;
        this.portMult = portMult;
        this.synergyMult = synergyMult;
        this.pollutionMult = pollutionMult;
        this.calibrationMult = calibrationMult;
        this.finalScore = finalScore;
    }
}
