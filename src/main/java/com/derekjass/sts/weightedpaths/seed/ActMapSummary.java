package com.derekjass.sts.weightedpaths.seed;

import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActMapSummary {

    public final int actNumber;
    public final List<List<String>> floorSymbols;

    private String routeStatSummary = "";
    private PathSymbolCounts plannedCounts = PathSymbolCounts.EMPTY;
    private List<String> plannedRouteSymbols = Collections.emptyList();

    public ActMapSummary(int actNumber, List<List<String>> floorSymbols) {
        this.actNumber = actNumber;
        this.floorSymbols = Collections.unmodifiableList(floorSymbols);
    }

    public void setRecommendedPlan(PathSymbolCounts routeCounts, List<String> routeSymbols) {
        if (routeCounts == null) {
            this.routeStatSummary = "";
            this.plannedCounts = PathSymbolCounts.EMPTY;
            this.plannedRouteSymbols = Collections.emptyList();
        } else {
            this.routeStatSummary = routeCounts.formatRouteSummary();
            this.plannedCounts = routeCounts;
            this.plannedRouteSymbols = routeSymbols == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(routeSymbols);
        }
    }

    public void setRecommendedPlan(PathSymbolCounts routeCounts) {
        setRecommendedPlan(routeCounts, Collections.<String>emptyList());
    }

    public PathSymbolCounts getPlannedCounts() {
        return plannedCounts;
    }

    public List<String> getPlannedRouteSymbols() {
        return plannedRouteSymbols;
    }

    public void clearRecommendedPlan() {
        setRecommendedPlan(null, null);
    }

    public String getRouteStatSummary() {
        return routeStatSummary;
    }

    public boolean hasPlan() {
        return !routeStatSummary.isEmpty();
    }

    public static ActMapSummary empty(int actNumber) {
        return new ActMapSummary(actNumber, new ArrayList<>());
    }
}
