package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.pipemasters.util.AssetResolver;
import com.pipemasters.util.MissingAssetLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class VehicleBlueprintLoader {
    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final MissingAssetLogger logger;
    private final Map<Path, VehicleBlueprintInfo> cache = new HashMap<>();

    VehicleBlueprintLoader(ObjectMapper mapper, Path rootDir, MissingAssetLogger logger) {
        this.mapper = mapper;
        this.resolver = new AssetResolver(rootDir);
        this.logger = logger;
    }

    VehicleBlueprintInfo load(JsonNode versionsNode, String settingsName) {
        if (versionsNode == null || versionsNode.isMissingNode() || !versionsNode.isArray()) {
            return VehicleBlueprintInfo.EMPTY;
        }
        VehicleBlueprintInfo fallback = VehicleBlueprintInfo.EMPTY;
        for (JsonNode version : versionsNode) {
            Iterator<JsonNode> iterator = version.elements();
            while (iterator.hasNext()) {
                JsonNode value = iterator.next();
                if (!value.isObject()) {
                    continue;
                }
                String assetPath = value.path("AssetPathName").asText(null);
                if (assetPath == null || assetPath.isBlank()) {
                    continue;
                }
                String className = extractClassName(assetPath);
                Path resolved = resolver.resolve(assetPath);
                if (resolved == null) {
                    if (logger != null) {
                        logger.missing(assetPath, "vehicle blueprint for " + settingsName);
                    }
                    if (fallback == VehicleBlueprintInfo.EMPTY) {
                        fallback = new VehicleBlueprintInfo(className, 1, 0, false, false);
                    }
                    continue;
                }
                if (!Files.exists(resolved)) {
                    if (logger != null) {
                        logger.missing(assetPath, "vehicle blueprint for " + settingsName);
                    }
                    if (fallback == VehicleBlueprintInfo.EMPTY) {
                        fallback = new VehicleBlueprintInfo(className, 1, 0, false, false);
                    }
                    continue;
                }
                VehicleBlueprintInfo info = cache.get(resolved);
                if (info == null) {
                    info = readBlueprint(resolved);
                    cache.put(resolved, info);
                }
                if (info.className().isBlank() && !className.isBlank()) {
                    info = new VehicleBlueprintInfo(className,
                            info.driverSeats(),
                            info.passengerSeats(),
                            info.amphibious(),
                            info.atgm());
                    cache.put(resolved, info);
                }
                return info;
            }
        }
        return fallback;
    }

    private VehicleBlueprintInfo readBlueprint(Path path) {
        return readBlueprint(path, new HashSet<>());
    }

    private VehicleBlueprintInfo readBlueprint(Path path, Set<Path> visited) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!visited.add(normalized)) {
            return VehicleBlueprintInfo.EMPTY;
        }

        String className = deriveClassName(path);
        int driverSeats = 0;
        int passengerSeats = 0;
        boolean amphibious = false;
        boolean atgm = false;
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
            for (JsonNode node : root) {
                JsonNode properties = node.path("Properties");
                if (!properties.isObject()) {
                    continue;
                }
                if (properties.has("DriverSeatConfig")) {
                    driverSeats = Math.max(driverSeats, 1);
                }
                JsonNode additionalSeats = properties.path("AdditionalSeatsConfig");
                if (additionalSeats.isArray()) {
                    passengerSeats = Math.max(passengerSeats, additionalSeats.size());
                }
                if (!amphibious) {
                    amphibious = hasComponent(properties, "SQVehicleBuoyancyComponent") || hasComponent(properties, "SQWaterSeatEjectionComponent") || hasComponent(properties, "SQWaterDamageComponent");
                }
                if (!atgm) {
                    atgm = detectAtgm(properties);
                }
                if (driverSeats > 0 || passengerSeats > 0) {
                    // The default object contains the info we need; stop after finding it.
                    // Keep scanning for additional hints like vehicle config references.
                }
            }
            VehicleConfigInfo configInfo = loadVehicleConfig(path, root);
            if (configInfo != null) {
                driverSeats = Math.max(driverSeats, configInfo.driverSeats());
                passengerSeats = Math.max(passengerSeats, configInfo.passengerSeats());
                amphibious = amphibious || configInfo.amphibious();
            }

            if ((driverSeats == 0 || passengerSeats == 0) && root != null) {
                Path superBlueprintPath = findSuperBlueprintPath(root, "vehicle blueprint superclass for " + className);
                if (superBlueprintPath != null) {
                    VehicleBlueprintInfo superInfo = readSuperBlueprint(superBlueprintPath, visited);
                    driverSeats = Math.max(driverSeats, superInfo.driverSeats());
                    passengerSeats = passengerSeats > 0 ? passengerSeats : Math.max(passengerSeats, superInfo.passengerSeats());
                    amphibious = amphibious || superInfo.amphibious();
                    atgm = atgm || superInfo.atgm();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle blueprint from " + path, e);
        }
        if (driverSeats == 0) {
            driverSeats = 1;
        }
        return new VehicleBlueprintInfo(className, driverSeats, passengerSeats, amphibious, atgm);
    }

    private VehicleBlueprintInfo readSuperBlueprint(Path superBlueprintPath, Set<Path> visited) {
        VehicleBlueprintInfo cached = cache.get(superBlueprintPath);
        if (cached != null) {
            return cached;
        }
        VehicleBlueprintInfo info = readBlueprint(superBlueprintPath, visited);
        cache.put(superBlueprintPath, info);
        return info;
    }

    private boolean detectAtgm(JsonNode node) {
        // TODO: Improve detection logic
        return false;
//        if (node == null || node.isMissingNode()) {
//            return false;
//        }
//        if (node.isTextual()) {
//            return containsAtgmKeyword(node.asText());
//        }
//        if (node.isArray()) {
//            for (JsonNode element : node) {
//                if (detectAtgm(element)) {
//                    return true;
//                }
//            }
//            return false;
//        }
//        if (node.isObject()) {
//            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
//            while (fields.hasNext()) {
//                Map.Entry<String, JsonNode> entry = fields.next();
//                String keyLower = entry.getKey().toLowerCase(Locale.ROOT);
//                if (keyLower.contains("atgm") || keyLower.contains("missile")) {
//                    return true;
//                }
//                if (detectAtgm(entry.getValue())) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    private boolean hasComponent(JsonNode properties, String classFragment) {
        JsonNode componentNode = JsonUtils.findFirstProperty(properties, classFragment);
        if (componentNode.isMissingNode()) {
            return false;
        }
        if (componentNode.isObject()) {
            JsonNode classNode = componentNode.path("Class");
            if (classNode.isTextual() && classNode.asText().contains(classFragment)) {
                return true;
            }
        }
        return JsonUtils.readBoolean(componentNode.path("bEnabled")) || componentNode.toString().toLowerCase(Locale.ROOT).contains(classFragment.toLowerCase(Locale.ROOT));
    }

    private VehicleConfigInfo loadVehicleConfig(Path blueprintPath, JsonNode root) {
        return loadVehicleConfig(blueprintPath, root, new HashSet<>());
    }

    private VehicleConfigInfo loadVehicleConfig(Path blueprintPath, JsonNode root, Set<Path> visited) {
        if (blueprintPath != null) {
            Path normalized = blueprintPath.toAbsolutePath().normalize();
            if (!visited.add(normalized)) {
                return null;
            }
        }

        VehicleConfigInfo localInfo = loadVehicleConfigFromReference(root);
        boolean hasSeatInfo = hasSeatInfo(localInfo);

        if (hasSeatInfo) {
            return localInfo;
        }

        String contextName = deriveClassName(blueprintPath);
        if ((contextName == null || contextName.isBlank()) && blueprintPath != null) {
            Path fileName = blueprintPath.getFileName();
            contextName = fileName != null ? fileName.toString() : blueprintPath.toString();
        }
        if (contextName == null || contextName.isBlank()) {
            contextName = "unknown blueprint";
        }
        Path superBlueprintPath = findSuperBlueprintPath(root, "vehicle config for " + contextName);
        if (superBlueprintPath == null) {
            return localInfo;
        }

        try {
            JsonNode superRoot = mapper.readTree(superBlueprintPath.toFile());
            VehicleConfigInfo superInfo = loadVehicleConfig(superBlueprintPath, superRoot, visited);
            if (superInfo == null) {
                return localInfo;
            }
            if (localInfo == null) {
                return superInfo;
            }
            return mergeConfigInfo(localInfo, superInfo);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle blueprint from " + superBlueprintPath, e);
        }
    }

    private VehicleConfigInfo loadVehicleConfigFromReference(JsonNode root) {
        JsonNode configReference = findVehicleConfigReference(root);
        if (configReference == null || configReference.isMissingNode()) {
            return null;
        }
        String objectPath = configReference.path("ObjectPath").asText(null);
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null || !Files.exists(resolved)) {
            return null;
        }
        try {
            JsonNode configRoot = mapper.readTree(resolved.toFile());
            SeatAccumulator accumulator = new SeatAccumulator();
            traverseConfig(configRoot, accumulator);
            return accumulator.toInfo();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle config from " + resolved, e);
        }
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
            if (resolved == null) {
                if (logger != null) {
                    logger.missing(objectPath, context);
                }
                continue;
            }
            if (!Files.exists(resolved)) {
                if (logger != null) {
                    logger.missing(objectPath, context);
                }
                continue;
            }
            return resolved;
        }
        return null;
    }

    private JsonNode findVehicleConfigReference(JsonNode root) {
        for (JsonNode node : root) {
            JsonNode properties = node.path("Properties");
            if (!properties.isObject()) {
                continue;
            }
            JsonNode configNode = JsonUtils.findFirstProperty(properties, "VehicleConfigAsset");
            if (!configNode.isMissingNode()) {
                return configNode;
            }
        }
        return null;
    }

    private void traverseConfig(JsonNode node, SeatAccumulator accumulator) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                traverseConfig(element, accumulator);
            }
            return;
        }
        if (!node.isObject()) {
            if (node.isTextual()) {
                String text = node.asText("").toLowerCase(Locale.ROOT);
                if (text.contains("amphib")) {
                    accumulator.amphibious = true;
                }
            }
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String keyLower = entry.getKey().toLowerCase(Locale.ROOT);
            JsonNode value = entry.getValue();

            if (keyLower.contains("amphib") || keyLower.contains("water")) {
                if (value.isBoolean() && value.asBoolean()) {
                    accumulator.amphibious = true;
                } else if (value.isTextual()) {
                    String text = value.asText("").toLowerCase(Locale.ROOT);
                    if (text.contains("true") || text.contains("on")) {
                        accumulator.amphibious = true;
                    }
                }
            }

            if (keyLower.contains("seat")) {
                if (value.isArray()) {
                    int recognized = 0;
                    int driverCount = 0;
                    for (JsonNode element : value) {
                        SeatDescriptor descriptor = classifySeat(element, true);
                        if (descriptor.isSeat) {
                            recognized++;
                            if (descriptor.isDriver) {
                                driverCount++;
                            }
                        }
                    }
                    if (recognized == 0) {
                        if (value.size() > 0) {
                            if (keyLower.contains("driver")) {
                                accumulator.addDriverSeats(value.size());
                            } else {
                                accumulator.addPassengerSeats(value.size());
                            }
                        }
                    } else {
                        accumulator.addDriverSeats(driverCount);
                        accumulator.addPassengerSeats(recognized - driverCount);
                    }
                } else if (value.isObject()) {
                    SeatDescriptor descriptor = classifySeat(value, true);
                    if (descriptor.isSeat) {
                        if (descriptor.isDriver) {
                            accumulator.addDriverSeats(1);
                        } else {
                            accumulator.addPassengerSeats(1);
                        }
                    }
                } else if (value.isInt()) {
                    if (keyLower.contains("driver")) {
                        accumulator.addDriverSeats(value.asInt());
                    } else {
                        accumulator.addPassengerSeats(value.asInt());
                    }
                }
                continue;
            }

            if (value.isContainerNode()) {
                traverseConfig(value, accumulator);
            }
        }
    }

    private SeatDescriptor classifySeat(JsonNode node, boolean defaultSeat) {
        SeatDescriptor descriptor = new SeatDescriptor();
        descriptor.isSeat = defaultSeat;
        analyzeSeat(node, descriptor);
        return descriptor;
    }

    private void analyzeSeat(JsonNode node, SeatDescriptor descriptor) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText("").toLowerCase(Locale.ROOT);
            if (text.contains("seat")) {
                descriptor.isSeat = true;
            }
            if (text.contains("driver")) {
                descriptor.isDriver = true;
            }
            return;
        }
        if (!node.isObject() && !node.isArray()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                analyzeSeat(element, descriptor);
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String keyLower = entry.getKey().toLowerCase(Locale.ROOT);
            JsonNode value = entry.getValue();
            if (keyLower.contains("seat")) {
                descriptor.isSeat = true;
            }
            if (keyLower.contains("driver")) {
                if ((value.isBoolean() && value.asBoolean()) || (value.isTextual() && value.asText("").toLowerCase(Locale.ROOT).contains("driver"))) {
                    descriptor.isDriver = true;
                }
            }
            analyzeSeat(value, descriptor);
        }
    }

    private static final class SeatAccumulator {
        private int driverSeats;
        private int passengerSeats;
        private boolean amphibious;

        void addDriverSeats(int value) {
            if (value <= 0) {
                return;
            }
            driverSeats += value;
        }

        void addPassengerSeats(int value) {
            if (value <= 0) {
                return;
            }
            passengerSeats += value;
        }

        VehicleConfigInfo toInfo() {
            if (driverSeats == 0 && passengerSeats > 0) {
                driverSeats = 1;
            }
            return new VehicleConfigInfo(driverSeats, passengerSeats, amphibious);
        }
    }

    private static final java.util.regex.Pattern ATGM_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(bgm-?7\\d|missile|konkurs|kornet|metis|malyutka|fagot|spike|hellfire|eryx|milan|spandrel|sagger|refleks|reflex|javelin|9m\\d+|at-?\\d+)"
    );

    private static final java.util.Set<String> ATGM_TOKENS = java.util.Set.of(
            "atgm",
            "tow",
            "tow2",
            "towb",
            "towc",
            "hj8",
            "hj73",
            "hj9",
            "hj10",
            "hj12",
            "hj73m"
    );

    private static final class SeatDescriptor {
        private boolean isSeat;
        private boolean isDriver;
    }

    private record VehicleConfigInfo(int driverSeats, int passengerSeats, boolean amphibious) {
    }

    private boolean hasSeatInfo(VehicleConfigInfo info) {
        return info != null && (info.driverSeats() > 0 || info.passengerSeats() > 0);
    }

    private VehicleConfigInfo mergeConfigInfo(VehicleConfigInfo primary, VehicleConfigInfo fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        return new VehicleConfigInfo(
                Math.max(primary.driverSeats(), fallback.driverSeats()),
                Math.max(primary.passengerSeats(), fallback.passengerSeats()),
                primary.amphibious() || fallback.amphibious()
        );
    }

    private String deriveClassName(Path path) {
        if (path == null) {
            return "";
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return "";
        }
        String raw = fileName.toString();
        int dot = raw.lastIndexOf('.');
        String base = dot >= 0 ? raw.substring(0, dot) : raw;
        return base.endsWith("_C") ? base : base + "_C";
    }

    private String extractClassName(String assetPath) {
        int dot = assetPath.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < assetPath.length()) {
            return assetPath.substring(dot + 1);
        }
        int slash = assetPath.lastIndexOf('/');
        String base = slash >= 0 ? assetPath.substring(slash + 1) : assetPath;
        return base.endsWith("_C") ? base : base + "_C";
    }
}