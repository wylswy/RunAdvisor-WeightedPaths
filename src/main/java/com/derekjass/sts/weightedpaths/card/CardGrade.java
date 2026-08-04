package com.derekjass.sts.weightedpaths.card;



public enum CardGrade {

    S,

    A,

    B,

    C;



    /** 校准后分数：好牌约 65–78，神抓 rare 约 80+。 */

    public static CardGrade fromScore(double score) {

        if (score >= 76.0) {

            return S;

        }

        if (score >= 62.0) {

            return A;

        }

        if (score >= 48.0) {

            return B;

        }

        return C;

    }

}


