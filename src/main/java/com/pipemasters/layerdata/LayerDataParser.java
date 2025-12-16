package com.pipemasters.layerdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class LayerDataParser {
    private static final Map<String, String> ALLIANCE_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("NewEnumerator21", "BLUFOR"),
            Map.entry("NewEnumerator22", "REDFOR"),
            Map.entry("NewEnumerator23", "INDEPENDENT"),
            Map.entry("NewEnumerator24", "PAC")
    );

    private static final Map<String, String> FACTION_SETUP_TYPE_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("NewEnumerator1", "Light Infantry"),
            Map.entry("NewEnumerator2", "Motorized"),
            Map.entry("NewEnumerator3", "Mechanized"),
            Map.entry("NewEnumerator4", "Armored"),
            Map.entry("NewEnumerator5", "Infantry (Air Mobile)"),
            Map.entry("NewEnumerator6", "Combined Arms"),
            Map.entry("NewEnumerator9", "Support"),
            Map.entry("NewEnumerator11", "Mechanized (Amphibious)"),
            Map.entry("NewEnumerator12", "Special Forces"),
            Map.entry("NewEnumerator13", "Mechanized (Wheeled)"),
            Map.entry("NewEnumerator14", "Mountain Infantry"),
            Map.entry("NewEnumerator15", "Support (MLRS)"),
            Map.entry("NewEnumerator16", "Armored Recon (Wheeled)"),
            Map.entry("NewEnumerator17", "Mechanized (Wheeled MGS)"),
            Map.entry("NewEnumerator18", "Mechanized (Wheeled Amphibious)"),
            Map.entry("NewEnumerator19", "Mechanized (Amphibious + MGS)"),
            Map.entry("NewEnumerator20", "Mechanized (MGS)"),
            Map.entry("NewEnumerator21", "Recon Infantry")
    );
    private final ObjectMapper mapper;

    public LayerDataParser(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public TeamFactions parseTeamFactions(Path layerDataPath) throws IOException {
        Objects.requireNonNull(layerDataPath, "layerDataPath");
        if (!Files.exists(layerDataPath)) {
            return TeamFactions.empty();
        }

        JsonNode root = mapper.readTree(layerDataPath.toFile());
        if (root == null || !root.isArray()) {
            return TeamFactions.empty();
        }

        JsonNode layerNode = findLayerNode(root);

        if (layerNode == null) {
            return TeamFactions.empty();
        }

        JsonNode properties = layerNode.path("Properties");
        boolean separated = properties.path("bSeparatedFactionsList").asBoolean(false);
        GameMode gameMode = parseGameMode(properties);
        boolean mirroredTeams = gameMode.hasMirroredTeams();

        List<FactionConfig> team1 = new ArrayList<>();
        List<FactionConfig> team2 = new ArrayList<>();

        JsonNode teamOneListNode = properties.get("FactionsListTeamOne");
        JsonNode teamTwoListNode = properties.get("FactionsListTeamTwo");
        JsonNode factionsListNode = properties.path("FactionsList");

        boolean hasExplicitTeamOne = teamOneListNode != null && teamOneListNode.isArray();
        boolean hasExplicitTeamTwo = teamTwoListNode != null && teamTwoListNode.isArray();

        if (hasExplicitTeamOne) {
            parseFactionEntries(teamOneListNode, TeamSide.TEAM1, team1, team2);
        }
        if (hasExplicitTeamTwo) {
            parseFactionEntries(teamTwoListNode, TeamSide.TEAM2, team1, team2);
        }

        if (!hasExplicitTeamOne) {
            TeamSide defaultTeam = hasExplicitTeamTwo ? TeamSide.TEAM1 : null;
            if (mirroredTeams && !hasExplicitTeamTwo) {
                parseFactionEntries(factionsListNode, TeamSide.TEAM1, team1, team2);
                team2.addAll(team1);
            } else {
                parseFactionEntries(factionsListNode, defaultTeam, team1, team2);
            }
        }

        if ((team1.isEmpty() || team2.isEmpty()) && root.isArray()) {
            addFallbackFactionsFromTeamConfigs(root, team1, team2);
        }

        return new TeamFactions(separated, List.copyOf(team1), List.copyOf(team2));
    }

    public GameMode parseGameMode(Path layerDataPath) throws IOException {
        Objects.requireNonNull(layerDataPath, "layerDataPath");
        if (!Files.exists(layerDataPath)) {
            return GameMode.UNKNOWN;
        }

        JsonNode root = mapper.readTree(layerDataPath.toFile());
        if (root == null || !root.isArray()) {
            return GameMode.UNKNOWN;
        }

        JsonNode layerNode = findLayerNode(root);
        if (layerNode == null) {
            return GameMode.UNKNOWN;
        }

        return parseGameMode(layerNode.path("Properties"));
    }

    public LayerTeamConfigs parseTeamConfigs(Path layerDataPath) throws IOException {
        Objects.requireNonNull(layerDataPath, "layerDataPath");
        if (!Files.exists(layerDataPath)) {
            return LayerTeamConfigs.empty();
        }

        JsonNode root = mapper.readTree(layerDataPath.toFile());
        if (root == null || !root.isArray()) {
            return LayerTeamConfigs.empty();
        }

        JsonNode layerNode = findLayerNode(root);
        GameMode gameMode = parseGameMode(layerNode == null ? null : layerNode.path("Properties"));
        boolean mirroredTeams = gameMode.hasMirroredTeams();

        TeamConfig team1 = null;
        TeamConfig team2 = null;
        List<TeamConfig> fallback = new ArrayList<>();

        for (JsonNode node : root) {
            if (!"BP_SQLayerTeamConfig_C".equals(node.path("Type").asText())) {
                continue;
            }

            TeamConfig config = parseTeamConfig(node);
            if (config == null) {
                continue;
            }

            if (config.index() == 1 && team1 == null) {
                team1 = config;
            } else if (config.index() == 2 && team2 == null) {
                team2 = config;
            } else {
                fallback.add(config);
            }
        }

        if (team1 == null && !fallback.isEmpty()) {
            team1 = fallback.remove(0);
        }
        if (team2 == null && !fallback.isEmpty()) {
            team2 = fallback.remove(0);
        }

        if (mirroredTeams) {
            team1 = duplicateIfMissing(team1, team2, 1);
            team2 = duplicateIfMissing(team2, team1, 2);
        }

        team1 = ensureTeamIndex(team1, 1);
        team2 = ensureTeamIndex(team2, 2);

        if (team1 == null && team2 == null) {
            return LayerTeamConfigs.empty();
        }

        return new LayerTeamConfigs(team1, team2);
    }

    private void parseFactionEntries(JsonNode factionsListNode,
                                     TeamSide forcedTeam,
                                     List<FactionConfig> team1,
                                     List<FactionConfig> team2) {
        if (factionsListNode == null || !factionsListNode.isArray()) {
            return;
        }

        for (JsonNode factionEntry : factionsListNode) {
            JsonNode valueNode = factionEntry.path("Value");
            if (valueNode == null || valueNode.isMissingNode()) {
                continue;
            }

            String defaultUnit = extractUnitName(valueNode.path("Faction"));
            if (defaultUnit == null || defaultUnit.isBlank()) {
                continue;
            }

            String key = factionEntry.path("Key").asText(null);
            TeamSide team = forcedTeam != null
                    ? forcedTeam
                    : determineTeam(key, defaultUnit, valueNode.path("Faction"));

            String factionId = extractFactionId(defaultUnit, key);
            List<FactionType> types = parseFactionTypes(valueNode.path("Types"));
            List<FactionType> filteredTypes = removeDefaultUnitFromTypes(defaultUnit, types);
            FactionConfig config = new FactionConfig(factionId, defaultUnit, List.copyOf(filteredTypes));

            if (team == TeamSide.TEAM2) {
                team2.add(config);
            } else {
                team1.add(config);
            }
        }
    }

    private void addFallbackFactionsFromTeamConfigs(JsonNode root,
                                                    List<FactionConfig> team1,
                                                    List<FactionConfig> team2) {
        TeamConfig team1Config = null;
        TeamConfig team2Config = null;
        List<TeamConfig> fallback = new ArrayList<>();

        for (JsonNode node : root) {
            if (!"BP_SQLayerTeamConfig_C".equals(node.path("Type").asText())) {
                continue;
            }

            TeamConfig config = parseTeamConfig(node);
            if (config == null || config.defaultFactionUnit() == null || config.defaultFactionUnit().isBlank()) {
                continue;
            }

            if (config.index() == 1 && team1Config == null) {
                team1Config = config;
            } else if (config.index() == 2 && team2Config == null) {
                team2Config = config;
            } else {
                fallback.add(config);
            }
        }

        if (team1Config == null && !fallback.isEmpty()) {
            team1Config = fallback.remove(0);
        }
        if (team2Config == null && !fallback.isEmpty()) {
            team2Config = fallback.remove(0);
        }

        team1Config = ensureTeamIndex(team1Config, 1);
        team2Config = ensureTeamIndex(team2Config, 2);

        if (team1.isEmpty()) {
            addFallbackFaction(team1, team1Config);
        }
        if (team2.isEmpty()) {
            addFallbackFaction(team2, team2Config);
        }
    }

    private JsonNode findLayerNode(JsonNode root) {
        if (root == null || !root.isArray()) {
            return null;
        }
        for (JsonNode node : root) {
            if ("BP_SQLayer_C".equals(node.path("Type").asText())) {
                return node;
            }
        }
        return null;
    }

    private GameMode parseGameMode(JsonNode properties) {
        if (properties == null || properties.isMissingNode()) {
            return GameMode.UNKNOWN;
        }
        return GameMode.fromRowName(properties.path("GameMode").path("RowName").asText(null));
    }

    private void addFallbackFaction(List<FactionConfig> target, TeamConfig config) {
        if (target == null || config == null) {
            return;
        }

        String defaultUnit = config.defaultFactionUnit();
        if (defaultUnit == null || defaultUnit.isBlank()) {
            return;
        }

        String factionId = extractFactionId(defaultUnit, null);
        target.add(new FactionConfig(factionId, defaultUnit, List.of()));
    }

    private List<FactionType> parseFactionTypes(JsonNode typesNode) {
        if (typesNode == null || !typesNode.isArray()) {
            return List.of();
        }
        List<FactionType> types = new ArrayList<>();
        for (JsonNode typeNode : typesNode) {
            String unit = extractUnitName(typeNode.path("Value"));
            if (unit == null || unit.isBlank()) {
                continue;
            }
            String unitType = determineUnitType(unit, typeNode.path("Key").asText(null));
            types.add(new FactionType(unitType, unit));
        }
        return types;
    }

    private List<FactionType> removeDefaultUnitFromTypes(String defaultUnit, List<FactionType> types) {
        if (defaultUnit == null || defaultUnit.isBlank() || types.isEmpty()) {
            return types;
        }

        List<FactionType> filtered = new ArrayList<>(types.size());
        boolean removed = false;
        for (FactionType type : types) {
            if (defaultUnit.equalsIgnoreCase(type.unit())) {
                removed = true;
                continue;
            }
            filtered.add(type);
        }

        return removed ? filtered : types;
    }

    private TeamSide determineTeam(String key, String defaultUnit, JsonNode factionNode) {
        int teamNumber = parseTeamNumber(key);
        if (teamNumber == 1) {
            return TeamSide.TEAM1;
        }
        if (teamNumber == 2) {
            return TeamSide.TEAM2;
        }

        String reference = defaultUnit;
        if (reference == null || reference.isBlank()) {
            reference = extractAssetPath(factionNode);
        }

        if (reference != null) {
            String lower = reference.toLowerCase(Locale.ROOT);
            if (lower.contains("_lo_") || lower.contains("offense") || lower.contains("offence")) {
                return TeamSide.TEAM1;
            }
            if (lower.contains("_ld_") || lower.contains("defense") || lower.contains("defence")) {
                return TeamSide.TEAM2;
            }
        }

        return TeamSide.TEAM1;
    }

    private int parseTeamNumber(String key) {
        if (key == null) {
            return -1;
        }
        int lastHyphen = key.lastIndexOf('-');
        if (lastHyphen >= 0 && lastHyphen < key.length() - 1) {
            String suffix = key.substring(lastHyphen + 1);
            try {
                return Integer.parseInt(suffix);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1;
    }

    private String extractFactionId(String defaultUnit, String key) {
        if (defaultUnit != null) {
            int underscore = defaultUnit.indexOf('_');
            if (underscore > 0) {
                return defaultUnit.substring(0, underscore);
            }
        }
        if (key != null) {
            int hyphen = key.indexOf('-');
            if (hyphen > 0) {
                return key.substring(0, hyphen);
            }
            return key;
        }
        return "";
    }

    private String determineUnitType(String unitName, String fallbackKey) {
        if (unitName != null) {
            String[] segments = unitName.split("_");
            if (segments.length >= 3) {
                StringBuilder builder = new StringBuilder();
                for (int i = 2; i < segments.length; i++) {
                    if (!segments[i].isEmpty()) {
                        builder.append(segments[i]);
                    }
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        }
        return formatTypeKey(fallbackKey);
    }

    private String formatTypeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String cleaned = rawKey.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] parts = cleaned.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        return builder.toString();
    }

    private String extractUnitName(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String assetPath = extractAssetPath(node);
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }
        int dot = assetPath.lastIndexOf('.');
        if (dot >= 0 && dot < assetPath.length() - 1) {
            return assetPath.substring(dot + 1);
        }
        int slash = assetPath.lastIndexOf('/');
        if (slash >= 0 && slash < assetPath.length() - 1) {
            return assetPath.substring(slash + 1);
        }
        return assetPath;
    }

    private String extractAssetPath(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        return node.path("AssetPathName").asText(null);
    }

    private TeamConfig parseTeamConfig(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode properties = node.path("Properties");
        if (properties == null || properties.isMissingNode()) {
            return null;
        }

        int index = parseTeamIndex(properties.path("Index").asText(null));
        String defaultFactionUnit = extractUnitName(properties.path("SpecificFactionSetup"));
        int tickets = properties.path("Tickets").asInt(0);
        boolean vehiclesDisabled = properties.path("VehiclesDisabled").asBoolean(false)
                || properties.path("bVehiclesDisabled").asBoolean(false)
                || properties.path("DisableVehicles").asBoolean(false);
        int playerPercent = parsePlayerPercent(properties);
        List<String> allowedAlliances = parseAllowedAlliances(properties.get("Allowed Alliances"));
        List<String> allowedFactionTypes = parseAllowedFactionTypes(properties.get("AllowedFactionSetupTypes"));
        List<String> requiredTags = parseTagArray(properties.get("RequiredTags"));

        return new TeamConfig(index, defaultFactionUnit, tickets, vehiclesDisabled, playerPercent,
                List.copyOf(allowedAlliances), List.copyOf(allowedFactionTypes), List.copyOf(requiredTags));
    }

    private TeamConfig duplicateIfMissing(TeamConfig primary, TeamConfig secondary, int targetIndex) {
        if (primary != null) {
            return primary;
        }
        if (secondary == null) {
            return null;
        }
        return new TeamConfig(targetIndex,
                secondary.defaultFactionUnit(),
                secondary.tickets(),
                secondary.vehiclesDisabled(),
                secondary.playerPercent(),
                secondary.allowedAlliances(),
                secondary.allowedFactionUnitTypes(),
                secondary.requiredTags());
    }

    private int parseTeamIndex(String rawIndex) {
        if (rawIndex == null || rawIndex.isBlank()) {
            return -1;
        }
        String normalized = rawIndex.trim();
        int separator = normalized.lastIndexOf("::");
        if (separator >= 0 && separator < normalized.length() - 1) {
            normalized = normalized.substring(separator + 2);
        }
        normalized = normalized.trim();
        if (normalized.equalsIgnoreCase("Team_One") || normalized.equalsIgnoreCase("TeamOne")) {
            return 1;
        }
        if (normalized.equalsIgnoreCase("Team_Two") || normalized.equalsIgnoreCase("TeamTwo")) {
            return 2;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int parsePlayerPercent(JsonNode properties) {
        if (properties == null || properties.isMissingNode()) {
            return 50;
        }
        if (properties.has("PlayerPercent")) {
            return properties.path("PlayerPercent").asInt(50);
        }
        if (properties.has("PlayerPercentOverride")) {
            return properties.path("PlayerPercentOverride").asInt(50);
        }
        if (properties.has("PlayerCountPercent")) {
            return properties.path("PlayerCountPercent").asInt(50);
        }
        return 50;
    }

    private List<String> parseAllowedAlliances(JsonNode arrayNode) {
        return parseEnumArray(arrayNode, ALLIANCE_DISPLAY_NAMES);
    }

    private List<String> parseAllowedFactionTypes(JsonNode arrayNode) {
        return parseEnumArray(arrayNode, FACTION_SETUP_TYPE_DISPLAY_NAMES);
    }

    private List<String> parseEnumArray(JsonNode arrayNode, Map<String, String> displayNames) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String raw = extractEnumToken(item);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = normalizeEnumKey(raw);
            String displayName = displayNames.get(normalized);
            if (displayName != null && !displayName.isBlank()) {
                values.add(displayName);
                continue;
            }
            String fallback = cleanEnumValue(raw);
            if (!fallback.isEmpty()) {
                values.add(fallback);
            }
        }
        return values;
    }

    private String extractEnumToken(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            if (node.has("EnumName")) {
                return node.path("EnumName").asText(null);
            }
            if (node.has("Value")) {
                JsonNode valueNode = node.get("Value");
                if (valueNode != null) {
                    if (valueNode.isTextual()) {
                        return valueNode.asText();
                    }
                    if (valueNode.has("EnumName")) {
                        return valueNode.path("EnumName").asText(null);
                    }
                }
            }
            if (node.has("Name")) {
                return node.path("Name").asText(null);
            }
        }
        return node.asText(null);
    }

    private String normalizeEnumKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        int separator = value.lastIndexOf("::");
        if (separator >= 0 && separator < value.length() - 1) {
            value = value.substring(separator + 2);
        }
        return value.trim();
    }

    private List<String> parseTagArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String value = null;
            if (item.isTextual()) {
                value = item.asText();
            } else {
                value = item.path("TagName").asText(null);
            }
            String formatted = cleanEnumValue(value);
            if (!formatted.isEmpty()) {
                tags.add(formatted);
            }
        }
        return tags;
    }

    private String cleanEnumValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        int separator = value.lastIndexOf("::");
        if (separator >= 0 && separator < value.length() - 1) {
            value = value.substring(separator + 2);
        }
        value = value.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (value.isEmpty()) {
            return "";
        }
        String[] parts = value.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private TeamConfig ensureTeamIndex(TeamConfig config, int desiredIndex) {
        if (config == null) {
            return null;
        }
        if (config.index() > 0) {
            return config;
        }
        return new TeamConfig(desiredIndex,
                config.defaultFactionUnit(),
                config.tickets(),
                config.vehiclesDisabled(),
                config.playerPercent(),
                config.allowedAlliances(),
                config.allowedFactionUnitTypes(),
                config.requiredTags());
    }

    private enum TeamSide {
        TEAM1,
        TEAM2
    }
}
