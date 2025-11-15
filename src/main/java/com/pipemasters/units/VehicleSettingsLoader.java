package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pipemasters.util.AssetResolver;
import com.pipemasters.util.MissingAssetLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class VehicleSettingsLoader {
    private static final Logger LOGGER = LogManager.getLogger(VehicleSettingsLoader.class);
    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final Map<Path, Map<String, VehicleSettings>> cache = new HashMap<>();

    private final MissingAssetLogger logger;
    private final VehicleDataTableLoader dataTableLoader;
    private final VehicleBlueprintLoader blueprintLoader;

    VehicleSettingsLoader(ObjectMapper mapper, Path rootDir, MissingAssetLogger logger) {
        this.mapper = mapper;
        this.resolver = new AssetResolver(rootDir);
        this.logger = logger;
        this.dataTableLoader = new VehicleDataTableLoader(mapper, rootDir, logger);
        this.blueprintLoader = new VehicleBlueprintLoader(mapper, rootDir, logger);
    }

    VehicleSettings load(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            LOGGER.debug("Vehicle settings reference missing; returning UNKNOWN settings.");
            return VehicleSettings.UNKNOWN;
        }
        String settingsName = BlueprintUtils.extractReferenceName(reference);
        String objectPath = reference.path("ObjectPath").asText(null);
        LOGGER.debug("Loading vehicle settings '{}' from '{}'", settingsName, objectPath);
        if (settingsName == null || objectPath == null) {
            LOGGER.debug("Vehicle settings reference '{}' missing name or object path.", reference);
            return VehicleSettings.UNKNOWN;
        }
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null) {
            if (logger != null) {
                logger.missing(objectPath, "vehicle settings for " + settingsName);
            }
            LOGGER.debug("Unable to resolve vehicle settings path for '{}'", objectPath);
            return VehicleSettings.UNKNOWN;
        }
        if (!Files.exists(resolved)) {
            if (logger != null) {
                logger.missing(objectPath, "vehicle settings for " + settingsName);
            }
            LOGGER.debug("Vehicle settings file '{}' for '{}' does not exist.", resolved, settingsName);
            return VehicleSettings.UNKNOWN;
        }
        LOGGER.trace("Reading vehicle settings from '{}'", resolved);
        Map<String, VehicleSettings> map = cache.computeIfAbsent(resolved, this::readSettingsFile);
        VehicleSettings settings = map.get(settingsName);
        if (settings == null && logger != null) {
            logger.missing(settingsName + " in " + resolved, "vehicle settings entry");
            LOGGER.debug("Vehicle settings '{}' not found in '{}'.", settingsName, resolved);
            return VehicleSettings.UNKNOWN;
        }
        LOGGER.debug("Resolved vehicle settings '{}' from '{}'.", settingsName, resolved);
        return settings;
    }

    private Map<String, VehicleSettings> readSettingsFile(Path path) {
        Map<String, VehicleSettings> result = new HashMap<>();
        try {
            LOGGER.trace("Parsing vehicle settings file '{}'", path);
            JsonNode root = mapper.readTree(path.toFile());
            if (!root.isArray()) {
                LOGGER.warn("Vehicle settings file '{}' is not an array node.", path);
                return result;
            }
            for (JsonNode node : root) {
                if (!"BP_SQVehicleSettings_C".equals(node.path("Type").asText())) {
                    continue;
                }
                String name = node.path("Name").asText("");
                JsonNode properties = node.path("Properties");
                result.put(name, parseSettings(name, properties));
            }
            LOGGER.info("Loaded {} vehicle settings entries from '{}'", result.size(), path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle settings from " + path, e);
        }
        return result;
    }

    private VehicleSettings parseSettings(String name, JsonNode properties) {
        VehicleDataRow dataRow = dataTableLoader.load(properties.path("Data"), name);
        String displayName = firstNonBlank(dataRow.displayName(),
                TextUtils.readText(JsonUtils.findFirstProperty(properties, "DisplayName")),
                BlueprintUtils.prettifyName(name));

        String icon = firstNonBlank(dataRow.icon(),
                TextUtils.readAssetName(JsonUtils.findFirstProperty(properties, "Icon")),
                TextUtils.readAssetName(JsonUtils.findFirstProperty(properties, "HUDIcon")));

        String rawType = BlueprintUtils.extractReferenceName(JsonUtils.findFirstProperty(properties, "Vehicle"));
        if (rawType == null) {
            rawType = BlueprintUtils.extractReferenceName(JsonUtils.findFirstProperty(properties, "VehicleClass"));
        }
        VehicleBlueprintInfo blueprintInfo = blueprintLoader.load(properties.path("VehicleVersions"), name);
        if ((rawType == null || rawType.isBlank()) && !blueprintInfo.className().isBlank()) {
            rawType = blueprintInfo.className();
        }

        String vehicleType = resolveVehicleType(JsonUtils.readString(JsonUtils.findFirstProperty(properties, "VehicleType")), displayName);
        String spawnerSize = resolveSpawnerSize(JsonUtils.readString(JsonUtils.findFirstProperty(properties, "Spawner")));

        List<String> tags = normalizeTags(JsonUtils.readStringArray(JsonUtils.findFirstProperty(properties, "Tag")));
        if (tags.isEmpty()) {
            tags = normalizeTags(JsonUtils.readStringArray(properties.path("VehicleTags")));
        }

        boolean amphibious = JsonUtils.readBoolean(JsonUtils.findFirstProperty(properties, "Amphib"));
        int ticketValue = JsonUtils.readInt(JsonUtils.findFirstProperty(properties, "Ticket"));
        boolean atgm = JsonUtils.readBoolean(JsonUtils.findFirstProperty(properties, "ATGM"));

        int passengerSeats = JsonUtils.readInt(JsonUtils.findFirstProperty(properties, "PassengerSeat"));
        if (passengerSeats == 0) {
            passengerSeats = JsonUtils.readInt(JsonUtils.findFirstProperty(properties, "PassengerCapacity"));
        }
        if (passengerSeats == 0) {
            passengerSeats = blueprintInfo.passengerSeats();
        }

        int driverSeats = JsonUtils.readInt(JsonUtils.findFirstProperty(properties, "DriverSeat"));
        if (driverSeats == 0) {
            driverSeats = JsonUtils.readInt(JsonUtils.findFirstProperty(properties, "Driver"));
        }

        if (driverSeats == 0) {
            driverSeats = blueprintInfo.driverSeats();
        }
        if (driverSeats == 0) {
            driverSeats = 1;
        }
        if (!amphibious) {
            amphibious = tags.stream()
                    .map(tag -> tag.toLowerCase(Locale.ROOT))
                    .anyMatch(tag -> tag.contains("watercraft") || tag.contains("amphib"));
        }
        if (!amphibious) {
            amphibious = blueprintInfo.amphibious();
        }

        if (!atgm) {
            atgm = tags.stream()
                    .map(tag -> tag.toLowerCase(Locale.ROOT))
                    .anyMatch(tag -> tag.contains("atgm"));
        }
        if (!atgm) {
            atgm = blueprintInfo.atgm();
        }

        return new VehicleSettings(displayName,
                rawType != null ? rawType : "",
                icon == null || icon.isBlank() ? "questionmark" : icon,
                vehicleType,
                spawnerSize,
                passengerSeats,
                driverSeats,
                tags,
                amphibious,
                ticketValue,
                atgm);
    }

    private String resolveVehicleType(String rawType, String displayName) {
        String lowerName = displayName != null ? displayName.toLowerCase(Locale.ROOT) : "";
        if (!lowerName.isBlank()) {
            if (lowerName.contains("anti air") || lowerName.contains("vads")) {
                return "AA";
            }
            if (lowerName.contains("log")) {
                return "LOGI";
            }
            if (lowerName.contains("transport") || lowerName.contains("truck")) {
                return "TRAN";
            }
            if (lowerName.contains("mortar") || lowerName.contains("m121") || lowerName.contains("m109")) {
                return "SPA";
            }
            if (lowerName.contains("uh-") || lowerName.contains("ch-") || lowerName.contains("little bird")) {
                return "UH";
            }
            if (lowerName.contains("apache") || lowerName.contains("ah-")) {
                return "AH";
            }
            if (lowerName.contains("altay") || lowerName.contains("tank")) {
                return "MBT";
            }
        }
        String mapped = VEHICLE_TYPE_OVERRIDES.get(rawType);
        if (mapped != null) {
            return mapped;
        }
        if (!lowerName.isBlank()) {
            if (lowerName.contains("dragoon") || lowerName.contains("25mm") || lowerName.contains("ifv")) {
                return "IFV";
            }
            if (lowerName.contains("m1126") || lowerName.contains("m113") || lowerName.contains("pars")) {
                return "APC";
            }
        }
        if (rawType != null && !rawType.isBlank()) {
            return BlueprintUtils.normalizeEnum(rawType);
        }
        return "Unknown";
    }

    private String resolveSpawnerSize(String rawSpawner) {
        String mapped = SPAWNER_SIZE_OVERRIDES.get(rawSpawner);
        if (mapped != null) {
            return mapped;
        }
        if (rawSpawner != null && !rawSpawner.isBlank()) {
            return BlueprintUtils.normalizeEnum(rawSpawner);
        }
        return "Unknown";
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : rawTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String mapped = VEHICLE_TAG_OVERRIDES.get(tag);
            if (mapped != null) {
                normalized.add(mapped);
            } else {
                String value = BlueprintUtils.normalizeEnum(tag);
                if (!value.isBlank() && !"Unknown".equals(value)) {
                    normalized.add(value);
                }
            }
        }
        return List.copyOf(normalized);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static final Map<String, String> VEHICLE_TYPE_OVERRIDES = Map.ofEntries(
            Map.entry("ESQVehicle::NewEnumerator1", "IFV"),
            Map.entry("ESQVehicle::NewEnumerator2", "APC"),
            Map.entry("ESQVehicle::NewEnumerator3", "MBT"),
            Map.entry("ESQVehicle::NewEnumerator4", "LOGI"),
            Map.entry("ESQVehicle::NewEnumerator6", "UH"),
            Map.entry("ESQVehicle::NewEnumerator9", "AA"),
            Map.entry("ESQVehicle::NewEnumerator10", "MRAP"),
            Map.entry("ESQVehicle::NewEnumerator11", "TRAN"),
            Map.entry("ESQVehicle::NewEnumerator13", "SPA"),
            Map.entry("ESQVehicle::NewEnumerator16", "APC")
    );

    private static final Map<String, String> SPAWNER_SIZE_OVERRIDES = Map.ofEntries(
            Map.entry("ESQVehicleSpawnerSize::NewEnumerator3", "MRAP"),
            Map.entry("ESQVehicleSpawnerSize::NewEnumerator4", "APC"),
            Map.entry("ESQVehicleSpawnerSize::NewEnumerator5", "Tank"),
            Map.entry("ESQVehicleSpawnerSize::NewEnumerator6", "Helicopter")
    );

    private static final Map<String, String> VEHICLE_TAG_OVERRIDES = Map.ofEntries(
            Map.entry("ESQVehicleTag::NewEnumerator0", "Class_Light"),
            Map.entry("ESQVehicleTag::NewEnumerator1", "Class_Medium"),
            Map.entry("ESQVehicleTag::NewEnumerator2", "Class_Heavy"),
            Map.entry("ESQVehicleTag::NewEnumerator7", "AGL")
    );
}