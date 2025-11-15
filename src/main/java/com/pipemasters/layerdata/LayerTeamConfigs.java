package com.pipemasters.layerdata;

public record LayerTeamConfigs(TeamConfig team1, TeamConfig team2) {
    public boolean isEmpty() {
        return team1 == null && team2 == null;
    }

    public static LayerTeamConfigs empty() {
        return new LayerTeamConfigs(null, null);
    }
}
