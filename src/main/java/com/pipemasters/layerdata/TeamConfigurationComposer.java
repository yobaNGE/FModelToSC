package com.pipemasters.layerdata;

import com.pipemasters.model.LayerTeamConfiguration;
import com.pipemasters.units.UnitFactionFactory;
import com.pipemasters.units.Units;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class TeamConfigurationComposer {
    private final LayerDataParser layerDataParser;
    private final UnitFactionFactory unitFactionFactory;

    public TeamConfigurationComposer(LayerDataParser layerDataParser, UnitFactionFactory unitFactionFactory) {
        this.layerDataParser = Objects.requireNonNull(layerDataParser, "layerDataParser");
        this.unitFactionFactory = Objects.requireNonNull(unitFactionFactory, "unitFactionFactory");
    }

    public LayerTeamConfiguration compose(Path layerDataPath, Units units) throws IOException {
        Objects.requireNonNull(layerDataPath, "layerDataPath");

        TeamFactions teamFactions = layerDataParser.parseTeamFactions(layerDataPath);
        if (teamFactions == null) {
            teamFactions = TeamFactions.empty();
        }
        LayerTeamConfigs teamConfigs = layerDataParser.parseTeamConfigs(layerDataPath);
        if (teamConfigs == null) {
            teamConfigs = LayerTeamConfigs.empty();
        }

        TeamConfig team1Config = TeamConfigDefaults.applyDefaultFactionFallback(
                teamConfigs.team1(),
                teamFactions.team1Units());
        TeamConfig team2Config = TeamConfigDefaults.applyDefaultFactionFallback(
                teamConfigs.team2(),
                teamFactions.team2Units());

        TeamFactions factionsForOutput = !teamFactions.isEmpty() ? teamFactions : null;

        if (factionsForOutput == null && units != null) {
            TeamFactions factionsFromUnits = unitFactionFactory.fromUnits(units);
            if (factionsFromUnits != null) {
                factionsForOutput = factionsFromUnits;
            }
        }

        if (team1Config == null && team2Config == null && factionsForOutput == null) {
            return null;
        }

        return new LayerTeamConfiguration(factionsForOutput, team1Config, team2Config);
    }
}
