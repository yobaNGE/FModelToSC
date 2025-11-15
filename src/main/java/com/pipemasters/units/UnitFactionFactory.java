package com.pipemasters.units;

import com.pipemasters.layerdata.FactionConfig;
import com.pipemasters.layerdata.FactionType;
import com.pipemasters.layerdata.TeamFactions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class UnitFactionFactory {
    public TeamFactions fromUnits(Units units) {
        if (units == null) {
            return null;
        }

        List<FactionConfig> team1Factions = buildFactionConfigs(units.team1Units());
        List<FactionConfig> team2Factions = buildFactionConfigs(units.team2Units());

        return new TeamFactions(true, List.copyOf(team1Factions), List.copyOf(team2Factions));
    }

    private List<FactionConfig> buildFactionConfigs(List<Unit> units) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }

        Map<String, FactionAccumulator> factions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Unit unit : units) {
            if (unit == null) {
                continue;
            }
            String unitObjectName = unit.unitObjectName();
            if (unitObjectName == null || unitObjectName.isBlank()) {
                continue;
            }
            String[] segments = unitObjectName.split("_");
            if (segments.length < 3) {
                continue;
            }
            String factionId = segments[0];
            String typeSegment = String.join("_", Arrays.copyOfRange(segments, 2, segments.length));
            String sanitizedType = sanitizeType(typeSegment);
            if (sanitizedType.isEmpty()) {
                continue;
            }

            FactionAccumulator accumulator = factions.computeIfAbsent(factionId, key -> new FactionAccumulator());
            if ("CombinedArms".equalsIgnoreCase(sanitizedType)) {
                accumulator.defaultUnit = unitObjectName;
            } else {
                accumulator.types.putIfAbsent(sanitizedType, unitObjectName);
            }
        }

        List<FactionConfig> factionConfigs = new ArrayList<>();
        for (Map.Entry<String, FactionAccumulator> entry : factions.entrySet()) {
            FactionAccumulator accumulator = entry.getValue();
            String defaultUnit = accumulator.defaultUnit;
            if (defaultUnit == null && !accumulator.types.isEmpty()) {
                defaultUnit = accumulator.types.values().iterator().next();
            }
            List<FactionType> types = new ArrayList<>();
            for (Map.Entry<String, String> typeEntry : accumulator.types.entrySet()) {
                types.add(new FactionType(typeEntry.getKey(), typeEntry.getValue()));
            }
            factionConfigs.add(new FactionConfig(entry.getKey(), defaultUnit, List.copyOf(types)));
        }

        return factionConfigs;
    }

    private String sanitizeType(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        if (builder.length() == 0) {
            return "";
        }
        String sanitized = builder.toString();
        if (sanitized.length() == 1) {
            return sanitized.toUpperCase(Locale.ROOT);
        }
        return sanitized.substring(0, 1).toUpperCase(Locale.ROOT) + sanitized.substring(1);
    }

    private static final class FactionAccumulator {
        private String defaultUnit;
        private final Map<String, String> types = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
}
