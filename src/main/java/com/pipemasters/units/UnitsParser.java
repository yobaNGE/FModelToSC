package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pipemasters.util.MissingAssetLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class UnitsParser {
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)$");
    private static final Logger LOGGER = LogManager.getLogger(UnitsParser.class);

    private final ObjectMapper mapper;
    private final Path baseDir;
    private final Path rootDir;
    private final VehicleSettingsLoader vehicleSettingsLoader;
    private final CommanderActionSettingsLoader actionSettingsLoader;
    private final DelayLoader delayLoader;
    private final FactionSetupTableParser tableParser;
    private final MissingAssetLogger missingAssetLogger;
    private final CommanderTeamActionsLoader teamActionsLoader;

    UnitsParser(ObjectMapper mapper, Path baseDir) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        this.rootDir = resolveRootDirectory(baseDir);
        this.missingAssetLogger = new MissingAssetLogger(rootDir);
        this.vehicleSettingsLoader = new VehicleSettingsLoader(mapper, rootDir, missingAssetLogger);
        this.actionSettingsLoader = new CommanderActionSettingsLoader(mapper, rootDir);
        this.delayLoader = new DelayLoader(mapper, rootDir, missingAssetLogger);
        this.tableParser = new FactionSetupTableParser(mapper);
        this.teamActionsLoader = new CommanderTeamActionsLoader(mapper, rootDir, missingAssetLogger);
    }

    private Path resolveRootDirectory(Path start) {
        Path current = start.toAbsolutePath().normalize();
        Path candidate = current;
        Path settingsCandidate = null;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "Content".equalsIgnoreCase(name.toString()) && Files.isDirectory(current)) {
                return current;
            }

            boolean hasSettings = Files.isDirectory(current.resolve("Settings"));
            boolean hasVehicles = Files.isDirectory(current.resolve("Vehicles"));
            if ((hasSettings || hasVehicles) && settingsCandidate == null) {
                settingsCandidate = current;
            }

            current = current.getParent();
        }

        if (settingsCandidate != null) {
            return settingsCandidate;
        }
        return candidate;
    }

    Units parse() throws IOException {
        List<Unit> team1 = new ArrayList<>();
        List<Unit> team2 = new ArrayList<>();

        if (!Files.isDirectory(baseDir)) {
            LOGGER.warn("Base directory '{}' is not a directory. Returning empty unit lists.", baseDir);
            return new Units(List.of(), List.of());
        }

        LOGGER.info("Scanning factions under '{}'", baseDir);
        try (Stream<Path> factionDirs = Files.list(baseDir)) {
            for (Path factionDir : factionDirs.collect(Collectors.toList())) {
                if (!Files.isDirectory(factionDir)) {
                    continue;
                }
                String name = factionDir.getFileName().toString();
                if (name.equalsIgnoreCase("Template")) {
                    LOGGER.debug("Skipping template faction directory '{}'", factionDir);
                    continue;
                }
                LOGGER.info("Processing faction directory '{}'", factionDir);
                parseFactionDirectory(factionDir, team1, team2);
            }
        }

        return new Units(List.copyOf(team1), List.copyOf(team2));
    }

    private void parseFactionDirectory(Path factionDir, List<Unit> team1, List<Unit> team2) throws IOException {
        String factionName = factionDir.getFileName().toString();
        Path setupTablePath = baseDir.resolve("FactionSetupTable_" + factionName + ".json");
        Map<String, FactionSetupRow> rows = tableParser.parse(setupTablePath);
        FactionSetupRow coreRow = rows.getOrDefault(factionName + "_Core", null);
        LOGGER.debug("Faction '{}' resolved {} setup rows (core present: {}).", factionName, rows.size(), coreRow != null);

        try (Stream<Path> files = Files.walk(factionDir)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                if (file.getFileName().toString().startsWith("FactionSetupTable_")) {
                    continue;
                }
                LOGGER.debug("Parsing unit file '{}' for faction '{}'", file, factionName);
                parseUnitFile(file, rows, coreRow, team1, team2);
            }
        }
    }

    private void parseUnitFile(Path file,
                               Map<String, FactionSetupRow> rows,
                               FactionSetupRow coreRow,
                               List<Unit> team1,
                               List<Unit> team2) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        if (root == null || !root.isArray()) {
            LOGGER.warn("Unit file '{}' is not an array; skipping.", file);
            return;
        }

        Map<String, JsonNode> nodesByName = new HashMap<>();
        JsonNode factionSetupNode = MissingNode.getInstance();
        for (JsonNode node : root) {
            String name = node.path("Name").asText(null);
            if (name != null && !name.isBlank()) {
                nodesByName.put(name, node);
            }
            if ("BP_SQFactionSetup_C".equals(node.path("Type").asText())) {
                factionSetupNode = node;
            }
        }

        if (factionSetupNode.isMissingNode()) {
            LOGGER.warn("Unit file '{}' does not contain BP_SQFactionSetup_C entry; skipping.", file);
            return;
        }

        String unitObjectName = factionSetupNode.path("Name").asText(null);
        if (unitObjectName == null || unitObjectName.isBlank()) {
            LOGGER.warn("Unit file '{}' has faction setup entry without a Name property; skipping.", file);
            return;
        }
        if (unitObjectName.endsWith("_Core")) {
            LOGGER.debug("Skipping core unit '{}' in file '{}'", unitObjectName, file);
            return;
        }

        JsonNode properties = factionSetupNode.path("Properties");
        String rowName = properties.path("Data").path("RowName").asText(null);
        FactionSetupRow row = rowName != null ? rows.get(rowName) : null;

        String shortName = row != null ? row.shortName() : coreRow != null ? coreRow.shortName() : "";
        String displayName = row != null && !row.displayName().isBlank()
                ? row.displayName()
                : BlueprintUtils.prettifyName(unitObjectName);
        String description = row != null ? row.description() : "";
        String unitBadge = row != null ? row.unitBadge() : coreRow != null ? coreRow.unitBadge() : "";
        String factionID = properties.path("FactionID").asText("");
        if (factionID.isBlank() && row != null) {
            factionID = row.factionId();
        }
        String factionDisplayName = coreRow != null ? coreRow.displayName() : displayName;

        boolean hasBuddyRally = properties.path("HasBuddyRally").asBoolean(false);
        boolean useCommanderActionNearVehicle = properties.path("UseCommanderActionNearVehicle").asBoolean(false)
                || properties.path("bUseCommanderActionNearVehicle").asBoolean(false);

        String type = determineType(unitObjectName, properties.path("Type").asText(null));
        String unitIcon = buildUnitIcon(type);

        List<UnitVehicle> vehicles = parseVehicles(properties.path("Vehicles"), nodesByName);
        List<UnitCommanderAsset> commanderAssets = parseCommanderAssets(unitObjectName, shortName, factionID, nodesByName);

        Unit unit = new Unit(unitObjectName,
                unitIcon,
                factionID,
                shortName,
                factionDisplayName,
                displayName,
                description,
                unitBadge,
                type,
                useCommanderActionNearVehicle,
                hasBuddyRally,
                vehicles,
                commanderAssets);

        TeamAssignment assignment = determineTeamAssignment(file, unitObjectName, rowName, shortName);
        switch (assignment) {
            case TEAM2 -> {
                team2.add(unit);
                LOGGER.info("Added Team 2 unit '{}' ({} vehicles, {} commander assets).", unitObjectName,
                        unit.vehicles().size(), unit.commanderAssets().size());
            }
            case BOTH -> {
                team1.add(unit);
                team2.add(unit);
                LOGGER.info("Added unit '{}' to both teams ({} vehicles, {} commander assets).", unitObjectName,
                        unit.vehicles().size(), unit.commanderAssets().size());
            }
            case TEAM1 -> {
                team1.add(unit);
                LOGGER.info("Added Team 1 unit '{}' ({} vehicles, {} commander assets).", unitObjectName,
                        unit.vehicles().size(), unit.commanderAssets().size());
            }
        }
    }

    private List<UnitVehicle> parseVehicles(JsonNode vehiclesNode, Map<String, JsonNode> nodesByName) {
        if (vehiclesNode == null || !vehiclesNode.isArray()) {
            return List.of();
        }
        List<UnitVehicle> vehicles = new ArrayList<>();
        for (JsonNode entry : vehiclesNode) {
            String referenceName = BlueprintUtils.extractInnerName(entry.path("ObjectName").asText(null));
            if (referenceName == null) {
                continue;
            }
            JsonNode vehicleNode = nodesByName.get(referenceName);
            if (vehicleNode == null) {
                continue;
            }
            UnitVehicle vehicle = parseVehicle(vehicleNode);
            if (vehicle != null) {
                vehicles.add(vehicle);
            }
        }
        return vehicles;
    }

    private UnitVehicle parseVehicle(JsonNode node) {
        JsonNode properties = node.path("Properties");
        VehicleSettings settings = vehicleSettingsLoader.load(properties.path("Setting"));
        DelaySettings delay = delayLoader.load(properties.path("Delay"));
        int count = parseCount(properties.path("LimitedCount"));
        boolean singleUse = properties.path("bSingleUse").asBoolean(false)
                || properties.path("SingleUse").asBoolean(false);

        return new UnitVehicle(settings.displayName(),
                settings.rawType(),
                settings.icon(),
                count,
                delay.initialDelayMinutes(),
                delay.respawnTimeMinutes(),
                singleUse,
                settings.vehicleType(),
                settings.spawnerSize(),
                settings.passengerSeats(),
                settings.driverSeats(),
                settings.tags(),
                settings.amphibious(),
                settings.ticketValue(),
                settings.atgm());
    }

    private List<UnitCommanderAsset> parseCommanderAssets(String unitObjectName,
                                                          String shortName,
                                                          String factionId,
                                                          Map<String, JsonNode> nodesByName) {
        List<UnitCommanderAsset> assets = new ArrayList<>();
        List<String> teamCandidates = new ArrayList<>();
        if (unitObjectName != null && !unitObjectName.isBlank()) {
            String[] segments = unitObjectName.split("_");
            if (segments.length > 0) {
                teamCandidates.add(segments[0]);
            }
        }
        if (shortName != null && !shortName.isBlank()) {
            teamCandidates.add(shortName);
        }
        if (factionId != null && !factionId.isBlank()) {
            teamCandidates.add(factionId);
            int hyphen = factionId.indexOf('-');
            if (hyphen > 0) {
                teamCandidates.add(factionId.substring(0, hyphen));
            }
        }
        for (JsonNode node : nodesByName.values()) {
            if (!"BP_SQAvailability_CommanderAction_C".equals(node.path("Type").asText())) {
                continue;
            }
            if (!unitObjectName.equals(node.path("Outer").asText())) {
                continue;
            }
            JsonNode properties = node.path("Properties");
            CommanderActionSettings settings = actionSettingsLoader.load(properties.path("Setting"));
            DelaySettings delay = delayLoader.load(properties.path("Delay"));
            assets.add(new UnitCommanderAsset(delay.initialDelayMinutes(), settings.displayName(), settings.icon()));
        }
        List<UnitCommanderAsset> teamAssets = teamActionsLoader.load(teamCandidates);
        if (!teamAssets.isEmpty()) {
            List<String> existing = assets.stream()
                    .map(asset -> asset.displayName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(ArrayList::new));
            for (UnitCommanderAsset asset : teamAssets) {
                if (!existing.contains(asset.displayName().toLowerCase(Locale.ROOT))) {
                    assets.add(asset);
                    existing.add(asset.displayName().toLowerCase(Locale.ROOT));
                }
            }
        }
        assets.sort((a, b) -> Integer.compare(a.delay(), b.delay()));
        return assets;
    }

    private int parseCount(JsonNode limitedCountNode) {
        String name = BlueprintUtils.extractReferenceName(limitedCountNode);
        if (name == null) {
            return 0;
        }
        Matcher matcher = COUNT_PATTERN.matcher(name);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return JsonUtils.readInt(limitedCountNode);
    }

    private String determineType(String unitObjectName, String typeValue) {
        if (unitObjectName != null) {
            String[] segments = unitObjectName.split("_");
            if (segments.length >= 3) {
                return formatTypeSegment(segments[2]);
            }
            if (segments.length >= 2) {
                return formatTypeSegment(segments[segments.length - 1]);
            }
        }
        if (typeValue != null && !typeValue.isBlank()) {
            return BlueprintUtils.normalizeEnum(typeValue);
        }
        return "Unknown";
    }

    private String formatTypeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        StringBuilder builder = new StringBuilder();
        char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (i > 0 && Character.isUpperCase(ch) && Character.isLowerCase(chars[i - 1])) {
                builder.append(' ');
            }
            builder.append(ch);
        }
        String formatted = builder.toString().replace('_', ' ').trim();
        if (formatted.isEmpty()) {
            return "Unknown";
        }
        return formatted.substring(0, 1).toUpperCase(Locale.ROOT) + formatted.substring(1);
    }

    private String buildUnitIcon(String type) {
        if (type == null || type.isBlank() || "Unknown".equals(type)) {
            return "T_UnitType_Generic";
        }
        String sanitized = type.replaceAll("\\s+", "");
        return "T_UnitType_" + sanitized;
    }

    private TeamAssignment determineTeamAssignment(Path file,
                                                   String unitObjectName,
                                                   String rowName,
                                                   String shortName) {
        TeamAssignment fromPath = determineTeamAssignmentFromPath(file);
        if (fromPath != null) {
            return fromPath;
        }

        TeamAssignment fromObjectName = determineTeamAssignmentFromText(unitObjectName);
        if (fromObjectName != null) {
            return fromObjectName;
        }

        TeamAssignment fromRowName = determineTeamAssignmentFromText(rowName);
        if (fromRowName != null) {
            return fromRowName;
        }

        TeamAssignment fromShortName = determineTeamAssignmentFromText(shortName);
        if (fromShortName != null) {
            return fromShortName;
        }

        return TeamAssignment.TEAM1;
    }

    private TeamAssignment determineTeamAssignmentFromPath(Path file) {
        if (file == null) {
            return null;
        }
        String normalized = file.toString().toLowerCase(Locale.ROOT);
        if (normalized.contains("smallmap")) {
            return TeamAssignment.BOTH;
        }
        if (normalized.contains("offense") || normalized.contains("offence") || normalized.contains("attack")) {
            return TeamAssignment.TEAM1;
        }
        if (normalized.contains("defense") || normalized.contains("defence") || normalized.contains("defend")) {
            return TeamAssignment.TEAM2;
        }
        return null;
    }

    private TeamAssignment determineTeamAssignmentFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains("_SMALL_") || upper.endsWith("_SMALL")) {
            return TeamAssignment.BOTH;
        }
        if (upper.contains("_LO_") || upper.contains("_AO_") || upper.contains("OFFENSE")
                || upper.contains("OFFENCE") || upper.contains("ATTACK")) {
            return TeamAssignment.TEAM1;
        }
        if (upper.contains("_LD_") || upper.contains("_AD_") || upper.contains("_MD_")
                || upper.contains("DEFENSE") || upper.contains("DEFENCE")) {
            return TeamAssignment.TEAM2;
        }
        return null;
    }

    private enum TeamAssignment {
        TEAM1,
        TEAM2,
        BOTH
    }
}