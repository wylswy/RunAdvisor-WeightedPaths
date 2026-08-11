package com.derekjass.sts.weightedpaths.logging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Gson-friendly run log schema (one file per run). */
public final class RunLogModels {

    public static final class RunSummary {
        public String runId;
        public String seed;
        public long seedSourceTimestamp;
        public long startedAtMs;
        public Long endedAtMs;
        public int ascension;
        public String character;
        public boolean victory;
        public String endReason;
        public int floorReached;
        public int endHp;
        public int maxHp;
        public int actReached;
        public List<NodeDecision> nodeDecisions = new ArrayList<>();
        public List<CardRewardLog> cardRewards = new ArrayList<>();
        public Map<String, Object> meta = new HashMap<>();
    }

    public static final class NodeDecision {
        public int floor;
        public int act;
        public String nodeType;
        public String recommendedNext;
        public double pathValue;
        public boolean act1EliteReady;
        public double hpRatio;
        public int estimatedRestAhead;
        public Map<String, Double> roomWeights = new HashMap<>();
        public Map<String, String> weightNotes = new HashMap<>();
    }

    public static final class CardRewardLog {
        public int floor;
        public int act;
        public double hpRatio;
        public boolean recommendedSkipAll;
        /** 本次推荐是否被信任调整（好感度低导致推荐失真；离线分析用）。 */
        public boolean trustAdjusted = false;
        /** 本次生效的信任系数（1.0=友好无失真）。 */
        public double trustFactor = 1.0;
        public List<CardChoiceLog> choices = new ArrayList<>();
        /** 玩家实际抓的卡 cardID；"" 表示跳过或尚未记录。 */
        public String playerChosen = "";
        /** 玩家是否跳过了本次卡奖。 */
        public boolean playerSkipped = false;
        /** 本次卡奖是否为「记仇使坏」（推荐指向最差卡逗玩家）。 */
        public boolean mischief = false;
    }

    public static final class CardChoiceLog {
        public int rewardIndex;
        public String cardId;
        public String grade;
        public double finalScore;
        public boolean recommended;
        public ScoreBreakdownLog breakdown;
    }

    public static final class ScoreBreakdownLog {
        public double baseScore;
        public double survivalBonus;
        public double portMult;
        public double synergyMult;
        public double pollutionMult;
        public double calibrationMult;
    }
}
