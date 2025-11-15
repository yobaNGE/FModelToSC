package com.pipemasters.layerdata;

import java.util.List;

public record TeamFactions(boolean separatedFactionsList,
                           List<FactionConfig> team1Units,
                           List<FactionConfig> team2Units) {

    public boolean isEmpty() {
        return (team1Units == null || team1Units.isEmpty())
                && (team2Units == null || team2Units.isEmpty());
    }

    public static TeamFactions empty() {
        return new TeamFactions(false, List.of(), List.of());
    }
}
