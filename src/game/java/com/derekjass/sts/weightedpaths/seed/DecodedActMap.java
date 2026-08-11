package com.derekjass.sts.weightedpaths.seed;

import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.ArrayList;

public final class DecodedActMap {

    public final int actNumber;
    public final ArrayList<ArrayList<MapRoomNode>> map;
    public final ActMapSummary summary;

    DecodedActMap(int actNumber, ArrayList<ArrayList<MapRoomNode>> map, ActMapSummary summary) {
        this.actNumber = actNumber;
        this.map = map;
        this.summary = summary;
    }
}
