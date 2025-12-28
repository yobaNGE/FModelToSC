package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.pipemasters.util.MissingAssetLogger;
import com.pipemasters.vehicles.VehicleExport;
import com.pipemasters.vehicles.VehicleWeapon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class VehiclesParser {
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)$");
    private static final Logger LOGGER = LogManager.getLogger(VehiclesParser.class);

    private final ObjectMapper mapper;
    private final Path baseDir;
    private final VehicleSettingsLoader vehicleSettingsLoader;
    private final DelayLoader delayLoader;
    private final VehicleWeaponsLoader weaponsLoader;

    public VehiclesParser(ObjectMapper mapper, Path baseDir) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        Path rootDir = resolveRootDirectory(baseDir);
        MissingAssetLogger missingAssetLogger = new MissingAssetLogger(rootDir);
        this.vehicleSettingsLoader = new VehicleSettingsLoader(mapper, rootDir, missingAssetLogger);
        this.delayLoader = new DelayLoader(mapper, rootDir, missingAssetLogger);
        this.weaponsLoader = new VehicleWeaponsLoader(mapper, rootDir, missingAssetLogger);
    }

    public List<VehicleExport> parse() throws IOException {
        return parse(1);
    }

    public List<VehicleExport> parse(int threads) throws IOException {
        Map<String, VehicleExport> vehicles = new LinkedHashMap<>();

        if (!Files.isDirectory(baseDir)) {
            LOGGER.warn("Base directory '{}' is not a directory. Returning empty vehicle list.", baseDir);
            return List.of();
        }

        LOGGER.info("Scanning factions under '{}'", baseDir);
        List<Path> unitFiles = new ArrayList<>();
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
                unitFiles.addAll(collectFactionUnitFiles(factionDir));
            }
        }

        if (unitFiles.isEmpty()) {
            return List.of();
        }

        int threadCount = Math.max(1, threads);
        if (threadCount == 1 || unitFiles.size() == 1) {
            for (Path file : unitFiles) {
                parseUnitFile(file, vehicles);
            }
            return List.copyOf(vehicles.values());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<List<VehicleExport>>> futures = new ArrayList<>(unitFiles.size());
            for (Path file : unitFiles) {
                futures.add(executor.submit(() -> parseUnitFileVehicles(file)));
            }
            for (Future<List<VehicleExport>> future : futures) {
                List<VehicleExport> parsed = future.get();
                for (VehicleExport vehicle : parsed) {
                    mergeVehicle(vehicles, vehicle);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while parsing vehicle files.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UncheckedIOException io) {
                throw io.getCause();
            }
            throw new RuntimeException("Failed to parse vehicle files.", cause);
        } finally {
            executor.shutdown();
        }

        return List.copyOf(vehicles.values());
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

    private List<Path> collectFactionUnitFiles(Path factionDir) throws IOException {
        List<Path> filesList = new ArrayList<>();
        try (Stream<Path> files = Files.walk(factionDir)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                if (file.getFileName().toString().startsWith("FactionSetupTable_")) {
                    continue;
                }
                filesList.add(file);
            }
        }
        return filesList;
    }

    private void parseUnitFile(Path file, Map<String, VehicleExport> vehicles) throws IOException {
        List<VehicleExport> parsed = parseUnitFileVehicles(file);
        for (VehicleExport vehicle : parsed) {
            mergeVehicle(vehicles, vehicle);
        }
    }

    private List<VehicleExport> parseUnitFileVehicles(Path file) throws IOException {
        LOGGER.debug("Parsing unit file '{}' for vehicles.", file);
        JsonNode root = mapper.readTree(file.toFile());
        if (root == null || !root.isArray()) {
            LOGGER.warn("Unit file '{}' is not an array; skipping.", file);
            return List.of();
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
            return List.of();
        }

        JsonNode properties = factionSetupNode.path("Properties");
        JsonNode vehiclesNode = properties.path("Vehicles");
        if (vehiclesNode == null || !vehiclesNode.isArray()) {
            return List.of();
        }

        List<VehicleExport> results = new ArrayList<>();
        for (JsonNode entry : vehiclesNode) {
            String referenceName = BlueprintUtils.extractInnerName(entry.path("ObjectName").asText(null));
            if (referenceName == null) {
                continue;
            }
            JsonNode vehicleNode = nodesByName.get(referenceName);
            if (vehicleNode == null) {
                continue;
            }
            VehicleExport vehicle = parseVehicle(vehicleNode);
            if (vehicle != null) {
                results.add(vehicle);
            }
        }
        return results;
    }

    private VehicleExport parseVehicle(JsonNode node) {
        JsonNode properties = node.path("Properties");
        VehicleSettings settings = vehicleSettingsLoader.load(properties.path("Setting"));
        DelaySettings delay = delayLoader.load(properties.path("Delay"));
        int count = parseCount(properties.path("LimitedCount"));
        boolean singleUse = properties.path("bSingleUse").asBoolean(false)
                || properties.path("SingleUse").asBoolean(false);
        List<VehicleWeapon> weapons = weaponsLoader.load(properties.path("Setting"), settings.rawType());

        return new VehicleExport(settings.displayName(),
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
                settings.atgm(),
                weapons);
    }

    private void mergeVehicle(Map<String, VehicleExport> vehicles, VehicleExport incoming) {
        String key = buildVehicleKey(incoming);
        VehicleExport existing = vehicles.get(key);
        if (existing == null) {
            vehicles.put(key, incoming);
            return;
        }
        List<VehicleWeapon> mergedWeapons = mergeWeapons(existing.weapons(), incoming.weapons());
        if (mergedWeapons == existing.weapons()) {
            return;
        }
        vehicles.put(key, new VehicleExport(existing.type(),
                existing.rawType(),
                existing.icon(),
                existing.count(),
                existing.delay(),
                existing.respawnTime(),
                existing.singleUse(),
                existing.vehType(),
                existing.spawnerSize(),
                existing.passengerSeats(),
                existing.driverSeats(),
                existing.vehTags(),
                existing.isAmphibious(),
                existing.ticketValue(),
                existing.ATGM(),
                mergedWeapons));
    }

    private String buildVehicleKey(VehicleExport vehicle) {
        String candidate = firstNonBlank(vehicle.rawType(), vehicle.type(), vehicle.icon());
        if (candidate == null || candidate.isBlank()) {
            return vehicle.toString().toLowerCase(Locale.ROOT);
        }
        return candidate.toLowerCase(Locale.ROOT);
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

    private List<VehicleWeapon> mergeWeapons(List<VehicleWeapon> base, List<VehicleWeapon> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return base == null ? List.of() : base;
        }
        if (base == null || base.isEmpty()) {
            return List.copyOf(incoming);
        }
        Map<String, VehicleWeapon> merged = new LinkedHashMap<>();
        addWeapons(merged, base);
        addWeapons(merged, incoming);
        return List.copyOf(merged.values());
    }

    private void addWeapons(Map<String, VehicleWeapon> target, List<VehicleWeapon> weapons) {
        for (VehicleWeapon weapon : weapons) {
            if (weapon == null) {
                continue;
            }
            String key = buildWeaponKey(weapon);
            if (key.isBlank()) {
                continue;
            }
            target.putIfAbsent(key, weapon);
        }
    }

    private String buildWeaponKey(VehicleWeapon weapon) {
        String name = weapon.weaponName() != null ? weapon.weaponName() : "";
        String projectile = weapon.rawProjectileName() != null ? weapon.rawProjectileName() : "";
        String key = (name + "|" + projectile).trim();
        if (key.isBlank()) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT);
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
}
