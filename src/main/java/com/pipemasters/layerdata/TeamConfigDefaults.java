package com.pipemasters.layerdata;

import java.util.List;
import java.util.Locale;

public final class TeamConfigDefaults {
    private TeamConfigDefaults() {
    }

    public static TeamConfig applyDefaultFactionFallback(TeamConfig config, List<FactionConfig> factionConfigs) {
        if (config == null) {
            return null;
        }
        if (hasUsableDefault(config.defaultFactionUnit(), factionConfigs)) {
            return config;
        }
        String fallbackUnit = selectFallbackFactionUnit(factionConfigs);
        if (fallbackUnit == null || fallbackUnit.isBlank()) {
            return config;
        }
        return new TeamConfig(
                config.index(),
                fallbackUnit,
                config.tickets(),
                config.vehiclesDisabled(),
                config.playerPercent(),
                config.allowedAlliances(),
                config.allowedFactionUnitTypes(),
                config.requiredTags());
    }

    private static boolean hasUsableDefault(String defaultUnit, List<FactionConfig> factionConfigs) {
        if (defaultUnit == null || defaultUnit.isBlank()) {
            return false;
        }
        if (looksLikeFactionUnit(defaultUnit)) {
            return true;
        }
        if (factionConfigs == null || factionConfigs.isEmpty()) {
            return false;
        }
        for (FactionConfig faction : factionConfigs) {
            if (faction == null) {
                continue;
            }
            if (defaultUnit.equalsIgnoreCase(faction.defaultUnit())) {
                return true;
            }
            List<FactionType> types = faction.types();
            if (types == null) {
                continue;
            }
            for (FactionType type : types) {
                if (type != null && defaultUnit.equalsIgnoreCase(type.unit())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean looksLikeFactionUnit(String candidate) {
        String normalized = candidate.toUpperCase(Locale.ROOT);
        return normalized.contains("_LO_") || normalized.contains("_LD_") || normalized.contains("_MD_")
                || normalized.contains("_ME_");
    }

    private static String selectFallbackFactionUnit(List<FactionConfig> factionConfigs) {
        if (factionConfigs == null) {
            return null;
        }
        for (FactionConfig faction : factionConfigs) {
            if (faction == null) {
                continue;
            }
            String defaultUnit = faction.defaultUnit();
            if (defaultUnit != null && !defaultUnit.isBlank()) {
                return defaultUnit;
            }
            List<FactionType> types = faction.types();
            if (types == null) {
                continue;
            }
            for (FactionType type : types) {
                if (type != null && type.unit() != null && !type.unit().isBlank()) {
                    return type.unit();
                }
            }
        }
        return null;
    }
}
