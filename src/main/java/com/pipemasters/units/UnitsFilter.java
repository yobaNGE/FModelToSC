package com.pipemasters.units;

import com.pipemasters.layerdata.FactionConfig;
import com.pipemasters.layerdata.FactionType;
import com.pipemasters.model.LayerTeamConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class UnitsFilter {
    public Units filter(Units units, LayerTeamConfiguration teamConfiguration) {
        if (units == null) {
            return null;
        }
        if (teamConfiguration == null || teamConfiguration.factions() == null) {
            return units;
        }

        Map<String, Unit> unitsByName = indexUnits(units);

        List<Unit> team1Units = filterTeamUnits(units.team1Units(),
                teamConfiguration.factions().team1Units(),
                unitsByName);
        List<Unit> team2Units = filterTeamUnits(units.team2Units(),
                teamConfiguration.factions().team2Units(),
                unitsByName);

        return new Units(team1Units, team2Units);
    }

    private Map<String, Unit> indexUnits(Units units) {
        Map<String, Unit> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        addToIndex(byName, units.team1Units());
        addToIndex(byName, units.team2Units());
        return byName;
    }

    private void addToIndex(Map<String, Unit> index, List<Unit> units) {
        if (units == null) {
            return;
        }
        for (Unit unit : units) {
            if (unit == null || unit.unitObjectName() == null || unit.unitObjectName().isBlank()) {
                continue;
            }
            index.putIfAbsent(unit.unitObjectName(), unit);
        }
    }

    private List<Unit> filterTeamUnits(List<Unit> units,
                                       List<FactionConfig> factionConfigs,
                                       Map<String, Unit> unitsByName) {
        if (units == null || units.isEmpty() || factionConfigs == null || factionConfigs.isEmpty()) {
            return units;
        }

        Map<String, Set<String>> factionAllowedTypes = new HashMap<>();
        Set<String> allowedUnits = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (FactionConfig factionConfig : factionConfigs) {
            if (factionConfig == null) {
                continue;
            }

            String factionKey = resolveFactionKey(factionConfig.factionID(), factionConfig.defaultUnit());
            if (factionKey.isEmpty()) {
                continue;
            }

            Set<String> allowedTypes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            if (factionConfig.types() != null) {
                for (FactionType type : factionConfig.types()) {
                    if (type == null) {
                        continue;
                    }
                    if (type.unit() != null && !type.unit().isBlank()) {
                        allowedUnits.add(type.unit());
                    }
                    String normalizedType = normalizeType(type.unitType());
                    if (!normalizedType.isEmpty()) {
                        allowedTypes.add(normalizedType);
                    }
                }
            }

            if (factionConfig.defaultUnit() != null && !factionConfig.defaultUnit().isBlank()) {
                allowedUnits.add(factionConfig.defaultUnit());
                String defaultUnitType = determineUnitType(factionConfig.defaultUnit(), unitsByName);
                if (!defaultUnitType.isEmpty()) {
                    allowedTypes.add(defaultUnitType);
                }
            }

            factionAllowedTypes.put(factionKey, Collections.unmodifiableSet(allowedTypes));
        }

        if (factionAllowedTypes.isEmpty() && allowedUnits.isEmpty()) {
            return units;
        }

        List<Unit> filtered = new ArrayList<>();
        for (Unit unit : units) {
            if (unit == null) {
                continue;
            }

            String unitName = unit.unitObjectName();
            if (unitName != null && allowedUnits.contains(unitName)) {
                filtered.add(unit);
                continue;
            }

            String factionKey = resolveFactionKey(unit.factionID(), unit.unitObjectName());
            if (factionKey.isEmpty()) {
                continue;
            }

            Set<String> allowedTypes = factionAllowedTypes.get(factionKey);
            if (allowedTypes == null) {
                continue;
            }

            if (allowedTypes.isEmpty()) {
                filtered.add(unit);
                continue;
            }

            String unitType = normalizeType(unit.type());
            if (!unitType.isEmpty() && allowedTypes.contains(unitType)) {
                filtered.add(unit);
            }
        }

        return List.copyOf(filtered);
    }

    private String resolveFactionKey(String factionId, String unitName) {
        String normalized = normalizeFactionId(factionId);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return normalizeFactionId(extractFactionFromUnitName(unitName));
    }

    private String normalizeFactionId(String factionId) {
        if (factionId == null) {
            return "";
        }
        String cleaned = factionId.trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        int dashIndex = cleaned.indexOf('-');
        if (dashIndex > 0) {
            cleaned = cleaned.substring(0, dashIndex);
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    private String extractFactionFromUnitName(String unitName) {
        if (unitName == null || unitName.isBlank()) {
            return "";
        }
        int underscoreIndex = unitName.indexOf('_');
        if (underscoreIndex > 0) {
            return unitName.substring(0, underscoreIndex);
        }
        return unitName;
    }

    private String determineUnitType(String unitName, Map<String, Unit> unitsByName) {
        if (unitName == null || unitName.isBlank()) {
            return "";
        }

        Unit matching = unitsByName.get(unitName);
        if (matching != null && matching.type() != null && !matching.type().isBlank()) {
            return normalizeType(matching.type());
        }

        String[] segments = unitName.split("_");
        if (segments.length < 3) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < segments.length; i++) {
            if (!segments[i].isEmpty()) {
                builder.append(segments[i]);
            }
        }

        return normalizeType(builder.toString());
    }

    private String normalizeType(String rawType) {
        if (rawType == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : rawType.toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString().toUpperCase(Locale.ROOT);
    }
}