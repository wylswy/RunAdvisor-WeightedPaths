package com.derekjass.sts.weightedpaths.card;



public enum CardGrade {

    S,

    A,

    B,

    C;



    /** 好牌约 75–95；神抓 rare 约 90+。 */

    public static CardGrade fromScore(double score) {

        if (score >= 75.0) {

            return S;

        }

        if (score >= 60.0) {

            return A;

        }

        if (score >= 45.0) {

            return B;

        }

        return C;

    }

}


