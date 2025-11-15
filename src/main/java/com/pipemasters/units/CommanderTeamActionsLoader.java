package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pipemasters.util.AssetResolver;
import com.pipemasters.util.MissingAssetLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class CommanderTeamActionsLoader {
    private static final Logger LOGGER = LogManager.getLogger(CommanderTeamActionsLoader.class);
    private static final String PROPERTY_TABLES = "units.commanderTeamActionTables";
    private static final String ENV_TABLES = "UNITS_COMMANDER_TEAM_ACTION_TABLES";
    private static final List<String> DEFAULT_TABLES = List.of(
//            "Settings/Actions/Commander/SD_DT_TeamCommandActions.json",
            "SquadGame/Plugins/Mods/Steel_Division/Content/Settings/Actions/Commander/SD_DT_TeamCommandActions.json"
    );

    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final MissingAssetLogger missingAssetLogger;
    private final Map<String, List<String>> teamToAssets = new HashMap<>();
    private final Map<String, CommanderTeamAction> actionCache = new HashMap<>();

    CommanderTeamActionsLoader(ObjectMapper mapper, Path rootDir, MissingAssetLogger missingAssetLogger) {
        this.mapper = mapper;
        this.resolver = new AssetResolver(rootDir);
        this.missingAssetLogger = missingAssetLogger;
        loadTeamTables(rootDir);
    }

    List<UnitCommanderAsset> load(List<String> teamCandidates) {
        if (teamCandidates == null || teamCandidates.isEmpty() || teamToAssets.isEmpty()) {
            return List.of();
        }
        Set<String> uniqueKeys = new LinkedHashSet<>();
        List<UnitCommanderAsset> assets = new ArrayList<>();
        for (String candidate : teamCandidates) {
            String normalized = normalizeTeam(candidate);
            if (normalized.isEmpty()) {
                continue;
            }
            List<String> assetPaths = teamToAssets.getOrDefault(normalized, List.of());
            for (String assetPath : assetPaths) {
                CommanderTeamAction action = loadAction(assetPath);
                if (action == null) {
                    continue;
                }
                String key = (action.displayName() + '|' + action.icon()).toLowerCase(Locale.ROOT);
                if (!uniqueKeys.add(key)) {
                    continue;
                }
                assets.add(new UnitCommanderAsset(action.delayMinutes(), action.displayName(), action.icon()));
            }
        }
        assets.sort((a, b) -> Integer.compare(a.delay(), b.delay()));
        return assets;
    }

    private void loadTeamTables(Path rootDir) {
        if (rootDir == null || !Files.exists(rootDir)) {
            LOGGER.debug("Commander team actions loading skipped because root directory '{}' does not exist.", rootDir);
            return;
        }

        List<Path> tables = resolveConfiguredTables(rootDir);
        if (tables.isEmpty()) {
            LOGGER.debug("No commander team action tables configured or discovered under '{}'.", rootDir);
            return;
        }

        for (Path table : tables) {
            parsePotentialTeamTable(table);
        }

        if (teamToAssets.isEmpty()) {
            LOGGER.debug("Configured commander team action tables did not yield any team mappings under '{}'.", rootDir);
        } else {
            LOGGER.debug("Loaded commander team actions for {} teams from configured tables.", teamToAssets.size());
        }
    }

    private List<Path> resolveConfiguredTables(Path rootDir) {
        List<String> configured = new ArrayList<>();
        configured.addAll(parseConfigurationValue(System.getProperty(PROPERTY_TABLES)));
        configured.addAll(parseConfigurationValue(System.getenv(ENV_TABLES)));

        if (configured.isEmpty()) {
            configured.addAll(DEFAULT_TABLES);
        }

        Set<Path> result = new LinkedHashSet<>();
        for (String entry : configured) {
            Path resolved = resolveTablePath(rootDir, entry);
            if (resolved == null) {
                continue;
            }
            if (!Files.exists(resolved)) {
                LOGGER.debug("Configured commander team action table '{}' does not exist (resolved to '{}').", entry, resolved);
                continue;
            }
            result.add(resolved);
        }
        return new ArrayList<>(result);
    }

    private List<String> parseConfigurationValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split("[;," + java.io.File.pathSeparator + "]+");
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Path resolveTablePath(Path rootDir, String configuredPath) {
        if (configuredPath == null) {
            return null;
        }
        String trimmed = configuredPath.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        final Path direct;
        try {
            direct = Path.of(trimmed);
        } catch (InvalidPathException e) {
            LOGGER.debug("Ignoring commander team action table '{}' because it is not a valid path.", trimmed, e);
            return null;
        }
        if (direct.isAbsolute()) {
            return direct;
        }

        String normalized = trimmed.replace('\\', '/');
        int contentIdx = normalized.toLowerCase(Locale.ROOT).indexOf("/content/");
        if (contentIdx >= 0) {
            normalized = normalized.substring(contentIdx + "/content/".length());
        } else if (normalized.toLowerCase(Locale.ROOT).startsWith("content/")) {
            normalized = normalized.substring("content/".length());
        }

        Path relative = Path.of(normalized);
        if (rootDir != null) {
            Path resolved = rootDir.resolve(relative).normalize();
            if (Files.exists(resolved)) {
                return resolved;
            }

            Path current = rootDir.getParent();
            while (current != null) {
                Path candidate = current.resolve(relative).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }

            return rootDir.resolve(relative).normalize();
        }

        return relative.toAbsolutePath().normalize();
    }

    private void parsePotentialTeamTable(Path file) {
        try {
            JsonNode root = mapper.readTree(file.toFile());
            if (!root.isArray() || root.isEmpty()) {
                return;
            }
            JsonNode header = root.get(0);
            if (!"DataTable".equals(header.path("Type").asText())) {
                return;
            }
            String rowStruct = header.path("Properties").path("RowStruct").path("ObjectName").asText("");
            if (!rowStruct.toLowerCase(Locale.ROOT).contains("sqteamcommands")) {
                return;
            }
            JsonNode rows = header.path("Rows");
            if (rows == null || !rows.isObject()) {
                return;
            }
            List<String> discoveredTeams = new ArrayList<>();
            rows.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || !value.isObject()) {
                    return;
                }
                String assetPath = readAssetPath(value.path("CommandData"));
                if (assetPath == null || assetPath.isBlank()) {
                    return;
                }
                List<String> teams = JsonUtils.readStringArray(value.path("Team"));
                if (teams.isEmpty()) {
                    return;
                }
                for (String team : teams) {
                    String normalized = normalizeTeam(team);
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    teamToAssets.computeIfAbsent(normalized, key -> new ArrayList<>()).add(assetPath);
                    discoveredTeams.add(normalized);
                }
            });
            if (!discoveredTeams.isEmpty()) {
                String summary = discoveredTeams.stream().distinct().sorted().collect(Collectors.joining(", "));
                LOGGER.debug("Loaded commander team actions from '{}': {}", file, summary);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to parse potential team command table '{}'.", file, e);
        }
    }

    private String readAssetPath(JsonNode commandData) {
        if (commandData == null || commandData.isMissingNode()) {
            return null;
        }
        String assetPath = commandData.path("AssetPathName").asText(null);
        if (assetPath != null && !assetPath.isBlank()) {
            return assetPath;
        }
        String objectPath = commandData.path("ObjectPath").asText(null);
        if (objectPath != null && !objectPath.isBlank()) {
            return objectPath;
        }
        return null;
    }

    private CommanderTeamAction loadAction(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }
        return actionCache.computeIfAbsent(assetPath, this::readActionFromAsset);
    }

    private CommanderTeamAction readActionFromAsset(String assetPath) {
        Path resolved = resolver.resolve(assetPath);
        if (resolved == null || !Files.exists(resolved)) {
            if (missingAssetLogger != null) {
                missingAssetLogger.missing(assetPath, "commander action");
            }
            LOGGER.debug("Unable to resolve commander action asset '{}'", assetPath);
            return null;
        }
        try {
            ActionInfo info = loadActionInfo(resolved, assetPath, new HashSet<>());
            if (info == null) {
                return null;
            }
            String displayName = info.displayName();
            if (displayName == null || displayName.isBlank()) {
                String fallbackName = deriveFallbackName(info, assetPath);
                displayName = BlueprintUtils.prettifyName(fallbackName);
            }
            String icon = info.icon();
            if (icon == null || icon.isBlank()) {
                icon = "questionmark";
            }
            int delayMinutes = info.delayMinutes() != null ? info.delayMinutes() : 0;
            return new CommanderTeamAction(displayName, icon, delayMinutes);
        } catch (IOException e) {
            LOGGER.warn("Failed to read commander action asset '{}'", resolved, e);
        }
        return null;
    }

    private ActionInfo loadActionInfo(Path blueprintPath, String context, Set<Path> visited) throws IOException {
        ActionInfo accumulated = null;
        Path currentPath = blueprintPath;

        while (currentPath != null && Files.exists(currentPath)) {
            Path normalized = currentPath.toAbsolutePath().normalize();
            if (!visited.add(normalized)) {
                break;
            }

            JsonNode root = mapper.readTree(currentPath.toFile());
            if (!root.isArray()) {
                LOGGER.debug("Commander action asset '{}' did not contain an array root.", currentPath);
                break;
            }

            ActionInfo localInfo = extractActionInfo(root);
            accumulated = mergeActionInfo(accumulated, localInfo);
            if (isComplete(accumulated)) {
                break;
            }

            currentPath = findSuperBlueprintPath(root, context);
        }

        return accumulated;
    }

    private ActionInfo extractActionInfo(JsonNode root) {
        for (JsonNode node : root) {
            JsonNode properties = node.path("Properties");
            if (!properties.isObject()) {
                continue;
            }
            String displayName = properties.path("DisplayName").asText("");
            if (displayName.isBlank()) {
                displayName = TextUtils.readText(JsonUtils.findFirstProperty(properties, "DisplayName"));
            }
            String icon = TextUtils.readAssetName(properties.path("Texture"));
            if (icon.isBlank()) {
                icon = TextUtils.readAssetName(JsonUtils.findFirstProperty(properties, "Icon"));
            }
            double cooldownSeconds = properties.path("CooldownDuration").asDouble(Double.NaN);
            Integer delayMinutes = Double.isNaN(cooldownSeconds) ? null : (int) Math.round(cooldownSeconds / 60.0);
            String fallbackName = node.path("Name").asText("");
            return new ActionInfo(displayName, icon, delayMinutes, fallbackName);
        }
        return null;
    }

    private boolean isComplete(ActionInfo info) {
        return info != null && info.hasDisplayName() && info.hasIcon() && info.hasDelay();
    }

    private ActionInfo mergeActionInfo(ActionInfo primary, ActionInfo fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        String displayName = primary.hasDisplayName() ? primary.displayName() : fallback.displayName();
        String icon = primary.hasIcon() ? primary.icon() : fallback.icon();
        Integer delayMinutes = primary.hasDelay() ? primary.delayMinutes() : fallback.delayMinutes();
        String fallbackName = !isBlank(primary.fallbackName()) ? primary.fallbackName() : fallback.fallbackName();
        return new ActionInfo(displayName, icon, delayMinutes, fallbackName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String deriveFallbackName(ActionInfo info, String assetPath) {
        String fallbackName = info != null ? info.fallbackName() : null;
        if (isBlank(fallbackName)) {
            fallbackName = BlueprintUtils.extractInnerName(assetPath);
        }
        if (isBlank(fallbackName)) {
            int slash = assetPath != null ? assetPath.lastIndexOf('/') : -1;
            if (slash >= 0 && slash + 1 < assetPath.length()) {
                fallbackName = assetPath.substring(slash + 1);
            } else {
                fallbackName = assetPath;
            }
        }
        return fallbackName;
    }

    private Path findSuperBlueprintPath(JsonNode root, String context) {
        if (root == null || root.isMissingNode() || !root.isArray()) {
            return null;
        }
        for (JsonNode node : root) {
            if (!node.isObject()) {
                continue;
            }
            JsonNode typeNode = node.path("Type");
            if (!typeNode.isTextual() || !"BlueprintGeneratedClass".equals(typeNode.asText())) {
                continue;
            }
            JsonNode superNode = node.path("Super");
            if (!superNode.isObject()) {
                continue;
            }
            String objectPath = superNode.path("ObjectPath").asText(null);
            if (objectPath == null || objectPath.isBlank()) {
                continue;
            }
            Path resolved = resolver.resolve(objectPath);
            if (resolved == null || !Files.exists(resolved)) {
                if (missingAssetLogger != null) {
                    missingAssetLogger.missing(objectPath, context);
                }
                continue;
            }
            return resolved;
        }
        return null;
    }

    private record ActionInfo(String displayName, String icon, Integer delayMinutes, String fallbackName) {
        boolean hasDisplayName() {
            return displayName != null && !displayName.isBlank();
        }

        boolean hasIcon() {
            return icon != null && !icon.isBlank();
        }

        boolean hasDelay() {
            return delayMinutes != null;
        }
    }

    private String normalizeTeam(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private record CommanderTeamAction(String displayName, String icon, int delayMinutes) {
    }
}