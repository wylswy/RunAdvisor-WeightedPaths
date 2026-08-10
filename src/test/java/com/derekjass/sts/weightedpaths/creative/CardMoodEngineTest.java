package com.derekjass.sts.weightedpaths.creative;

import com.derekjass.sts.weightedpaths.creative.CardMoodEngine.Mood;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 好感度状态机 + 道歉识别测试。 */
public class CardMoodEngineTest {

    @Test
    public void startsFriendly() {
        CardMoodEngine.reset();
        assertEquals(Mood.FRIENDLY, CardMoodEngine.currentMood());
    }

    @Test
    public void repeatedDefianceMakesResentful() {
        CardMoodEngine.reset();
        for (int i = 0; i < 5; i++) {
            CardMoodEngine.recordDefiance();
        }
        assertEquals("连续违背应记仇", Mood.RESENTFUL, CardMoodEngine.currentMood());
    }

    @Test
    public void someDefianceIsUnhappyNotResentful() {
        CardMoodEngine.reset();
        CardMoodEngine.recordDefiance();
        CardMoodEngine.recordDefiance();
        assertEquals("轻度违背应不高兴", Mood.UNHAPPY, CardMoodEngine.currentMood());
    }

    @Test
    public void apologyRecoversFromResentful() {
        CardMoodEngine.reset();
        for (int i = 0; i < 5; i++) {
            CardMoodEngine.recordDefiance();
        }
        assertEquals(Mood.RESENTFUL, CardMoodEngine.currentMood());
        CardMoodEngine.recordApology();
        CardMoodEngine.recordApology();
        CardMoodEngine.recordApology(); // -10 + 12 = 2
        assertEquals("道歉后应原谅", Mood.FRIENDLY, CardMoodEngine.currentMood());
    }

    @Test
    public void complianceGraduallyRecovers() {
        CardMoodEngine.reset();
        for (int i = 0; i < 4; i++) {
            CardMoodEngine.recordDefiance();
        }
        assertEquals(Mood.RESENTFUL, CardMoodEngine.currentMood());
        for (int i = 0; i < 7; i++) { // -8 + 7 = -1
            CardMoodEngine.recordCompliance();
        }
        assertEquals("顺从应逐渐恢复", Mood.FRIENDLY, CardMoodEngine.currentMood());
    }

    @Test
    public void favorClampedToRange() {
        CardMoodEngine.reset();
        for (int i = 0; i < 30; i++) {
            CardMoodEngine.recordDefiance();
        }
        assertEquals(-10, CardMoodEngine.favor());
        for (int i = 0; i < 50; i++) {
            CardMoodEngine.recordApology();
        }
        assertEquals(10, CardMoodEngine.favor());
    }

    @Test
    public void apologyRecognition() {
        assertTrue(CardMoodEngine.isApology("对不起我错了"));
        assertTrue(CardMoodEngine.isApology("我不该不听你的，别生气"));
        assertTrue(CardMoodEngine.isApology("以后都听你的"));
        assertTrue(CardMoodEngine.isApology("我错了，原谅我"));
    }

    @Test
    public void nonApologyNotRecognized() {
        assertFalse(CardMoodEngine.isApology("这局怎么打"));
        assertFalse(CardMoodEngine.isApology("你推荐的这张行不行"));
        assertFalse(CardMoodEngine.isApology(null));
        assertFalse(CardMoodEngine.isApology(""));
        assertFalse(CardMoodEngine.isApology("   "));
    }

    @Test
    public void restoreFavor_setsWithinBounds() {
        CardMoodEngine.reset();
        CardMoodEngine.restoreFavor(-5);
        assertEquals(-5, CardMoodEngine.favor());
        CardMoodEngine.restoreFavor(7);
        assertEquals(7, CardMoodEngine.favor());
    }

    @Test
    public void restoreFavor_clampsToBounds() {
        CardMoodEngine.reset();
        CardMoodEngine.restoreFavor(100);   // 夹到 +10
        assertEquals(10, CardMoodEngine.favor());
        CardMoodEngine.restoreFavor(-100);  // 夹到 -10
        assertEquals(-10, CardMoodEngine.favor());
    }
}
