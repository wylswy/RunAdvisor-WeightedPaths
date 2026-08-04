package com.derekjass.sts.weightedpaths.card;

import com.derekjass.sts.weightedpaths.card.data.CardStatEntry;
import com.derekjass.sts.weightedpaths.ui.CardUiStrings;

public final class CardRecommendation {

    public final CardGrade grade;
    public final double score;
    public final String reason;

    public CardRecommendation(CardGrade grade, double score, String reason) {
        this.grade = grade;
        this.score = score;
        this.reason = reason;
    }

    public String gradeLabel() {
        return CardUiStrings.gradeLabel(grade);
    }
}
