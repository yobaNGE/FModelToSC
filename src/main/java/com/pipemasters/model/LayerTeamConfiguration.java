package com.pipemasters.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pipemasters.layerdata.TeamConfig;
import com.pipemasters.layerdata.TeamFactions;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayerTeamConfiguration(TeamFactions factions, TeamConfig team1, TeamConfig team2) {
}
