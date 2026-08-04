package com.derekjass.sts.weightedpaths.card;



public enum CardGrade {

    S,

    A,

    B,

    C;



    /** 无全局压分：好牌约 70–90，神抓 rare 约 85+。 */

    public static CardGrade fromScore(double score) {

        if (score >= 80.0) {

            return S;

        }

        if (score >= 65.0) {

            return A;

        }

        if (score >= 50.0) {

            return B;

        }

        return C;

    }

}


