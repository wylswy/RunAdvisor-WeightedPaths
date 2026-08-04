package com.derekjass.sts.weightedpaths.card.data;

import com.derekjass.sts.weightedpaths.card.Port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CardStatEntry {

    public int baseScore = 50;
    public List<String> tags = new ArrayList<>();
    /** 抓这张牌前，牌组在该端口至少已有多少点。 */
    public String requiresPort = "";
    public int requiresMinPoints = 0;

    public boolean hasTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        for (String t : tags) {
            if (tag.equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getTags() {
        return tags == null ? Collections.<String>emptyList() : tags;
    }

    public Port requiredPort() {
        if (requiresPort == null || requiresPort.isEmpty()) {
            return null;
        }
        try {
            return Port.valueOf(requiresPort.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean servesPort(Port port) {
        if (port == null) {
            return false;
        }
        switch (port) {
            case DAMAGE:
                return hasTag("attack") || hasTag("aoe") || hasTag("dot");
            case BLOCK:
                return hasTag("block");
            case ENGINE:
                return hasTag("draw") || hasTag("energy") || hasTag("discard")
                        || hasTag("engine") || hasTag("retain");
            default:
                return false;
        }
    }
}
