package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipemasters.util.AssetResolver;
import com.pipemasters.util.MissingAssetLogger;
import com.pipemasters.vehicles.VehicleWeapon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class VehicleWeaponsLoader {
    private static final Logger LOGGER = LogManager.getLogger(VehicleWeaponsLoader.class);

    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final MissingAssetLogger logger;
    private final Map<Path, Map<String, JsonNode>> settingsCache = new ConcurrentHashMap<>();
    private final Map<Path, List<VehicleWeapon>> blueprintWeaponCache = new ConcurrentHashMap<>();
    private final Map<Path, WeaponInfo> weaponInfoCache = new ConcurrentHashMap<>();

    VehicleWeaponsLoader(ObjectMapper mapper, Path rootDir, MissingAssetLogger logger) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.resolver = new AssetResolver(rootDir);
        this.logger = logger;
    }

    List<VehicleWeapon> load(JsonNode settingsReference, String fallbackName) {
        if (settingsReference == null || settingsReference.isMissingNode()) {
            return List.of();
        }
        String settingsName = BlueprintUtils.extractReferenceName(settingsReference);
        String objectPath = settingsReference.path("ObjectPath").asText(null);
        if (settingsName == null || settingsName.isBlank() || objectPath == null || objectPath.isBlank()) {
            LOGGER.debug("Vehicle settings reference '{}' missing name or object path.", settingsReference);
            return List.of();
        }
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null) {
            if (logger != null) {
                logger.missing(objectPath, "vehicle settings for " + settingsName);
            }
            return List.of();
        }
        if (!Files.exists(resolved)) {
            if (logger != null) {
                logger.missing(objectPath, "vehicle settings for " + settingsName);
            }
            return List.of();
        }
        Map<String, JsonNode> settings = settingsCache.computeIfAbsent(resolved, this::readSettingsFile);
        JsonNode properties = settings.get(settingsName);
        if (properties == null || properties.isMissingNode()) {
            if (logger != null) {
                logger.missing(settingsName + " in " + resolved, "vehicle settings entry");
            }
            return List.of();
        }
        JsonNode versionsNode = JsonUtils.findFirstProperty(properties, "VehicleVersions");
        List<String> versionAssets = extractVehicleVersions(versionsNode);
        if (versionAssets.isEmpty()) {
            return List.of();
        }
        List<VehicleWeapon> weapons = new ArrayList<>();
        for (String assetPath : versionAssets) {
            Path blueprintPath = resolver.resolve(assetPath);
            if (blueprintPath == null) {
                if (logger != null) {
                    logger.missing(assetPath, "vehicle blueprint for " + settingsName);
                }
                continue;
            }
            if (!Files.exists(blueprintPath)) {
                if (logger != null) {
                    logger.missing(assetPath, "vehicle blueprint for " + settingsName);
                }
                continue;
            }
            weapons.addAll(readWeaponsFromBlueprint(blueprintPath, new HashSet<>()));
        }
        return mergeWeapons(weapons);
    }

    private Map<String, JsonNode> readSettingsFile(Path path) {
        Map<String, JsonNode> result = new HashMap<>();
        try {
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
                result.put(name, properties);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle settings from " + path, e);
        }
        return result;
    }

    private List<String> extractVehicleVersions(JsonNode versionsNode) {
        if (versionsNode == null || versionsNode.isMissingNode() || !versionsNode.isArray()) {
            return List.of();
        }
        List<String> assets = new ArrayList<>();
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
                assets.add(assetPath);
            }
        }
        return assets;
    }

    private List<VehicleWeapon> readWeaponsFromBlueprint(Path path, Set<Path> visited) {
        Path normalized = path.toAbsolutePath().normalize();
        List<VehicleWeapon> cached = blueprintWeaponCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        if (!visited.add(normalized)) {
            return List.of();
        }

        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle blueprint from " + path, e);
        }

        List<VehicleWeapon> weapons = new ArrayList<>();
        List<JsonNode> weaponRefs = new ArrayList<>();
        collectWeaponReferences(root, weaponRefs);
        for (JsonNode reference : weaponRefs) {
            VehicleWeapon weapon = resolveWeapon(reference);
            if (weapon != null) {
                weapons.add(weapon);
            }
        }

        List<JsonNode> seatPawns = new ArrayList<>();
        collectSeatPawnReferences(root, seatPawns);
        for (JsonNode reference : seatPawns) {
            String objectPath = reference.path("ObjectPath").asText(null);
            if (objectPath == null || objectPath.isBlank()) {
                continue;
            }
            Path resolved = resolver.resolve(objectPath);
            if (resolved == null || !Files.exists(resolved)) {
                if (logger != null) {
                    logger.missing(objectPath, "seat pawn blueprint");
                }
                continue;
            }
            weapons.addAll(readWeaponsFromBlueprint(resolved, visited));
        }

        if (weapons.isEmpty() && weaponRefs.isEmpty() && seatPawns.isEmpty()) {
            for (String inheritancePath : findInheritancePaths(root)) {
                Path resolved = resolver.resolve(inheritancePath);
                if (resolved == null || !Files.exists(resolved)) {
                    if (logger != null) {
                        logger.missing(inheritancePath, "weapon blueprint inheritance");
                    }
                    continue;
                }
                weapons.addAll(readWeaponsFromBlueprint(resolved, visited));
            }
        }

        List<VehicleWeapon> merged = mergeWeapons(weapons);
        blueprintWeaponCache.put(normalized, merged);
        return merged;
    }

    private void collectWeaponReferences(JsonNode node, List<JsonNode> references) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                collectWeaponReferences(element, references);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode weaponsNode = node.get("Weapons");
        if (weaponsNode != null && weaponsNode.isArray()) {
            for (JsonNode weaponEntry : weaponsNode) {
                JsonNode weaponClass = weaponEntry.path("WeaponClass");
                if (!weaponClass.isMissingNode()) {
                    references.add(weaponClass);
                }
            }
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("WeaponClass".equalsIgnoreCase(entry.getKey())) {
                references.add(entry.getValue());
            }
            collectWeaponReferences(entry.getValue(), references);
        }
    }

    private VehicleWeapon resolveWeapon(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            return null;
        }
        String objectPath = reference.path("ObjectPath").asText(null);
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        String rawWeaponName = BlueprintUtils.extractReferenceName(reference);
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null || !Files.exists(resolved)) {
            if (logger != null) {
                logger.missing(objectPath, "weapon blueprint");
            }
            return null;
        }
        WeaponInfo info = readWeaponInfoCached(resolved, new HashSet<>());
        if (info == null) {
            return null;
        }
        if (rawWeaponName == null || rawWeaponName.isBlank()) {
            rawWeaponName = deriveBaseName(resolved);
        }
        return new VehicleWeapon(info.weaponName(), rawWeaponName, info.rawProjectileName());
    }

    private void collectSeatPawnReferences(JsonNode node, List<JsonNode> references) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                collectSeatPawnReferences(element, references);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if (key != null && key.toLowerCase(Locale.ROOT).contains("seatpawn")) {
                JsonNode seatPawn = entry.getValue();
                if (seatPawn != null && seatPawn.isObject() && seatPawn.has("ObjectPath")) {
                    references.add(seatPawn);
                }
            }
            collectSeatPawnReferences(entry.getValue(), references);
        }
    }

    private WeaponInfo readWeaponInfoCached(Path path, Set<Path> visited) {
        Path normalized = path.toAbsolutePath().normalize();
        WeaponInfo cached = weaponInfoCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        WeaponInfo info = readWeaponInfo(normalized, visited);
        if (info != null) {
            weaponInfoCache.put(normalized, info);
        }
        return info;
    }

    private WeaponInfo readWeaponInfo(Path path, Set<Path> visited) {
        if (!visited.add(path.toAbsolutePath().normalize())) {
            return null;
        }
        String weaponName = "";
        String projectileName = "";
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read weapon blueprint from " + path, e);
        }
        if (root != null && root.isArray()) {
            for (JsonNode node : root) {
                JsonNode properties = node.path("Properties");
                if (!properties.isObject()) {
                    continue;
                }
                if (weaponName.isBlank()) {
                    weaponName = readWeaponDisplayName(properties);
                }
                if (projectileName.isBlank()) {
                    projectileName = extractProjectileName(properties);
                }
                if (!weaponName.isBlank() && !projectileName.isBlank()) {
                    break;
                }
            }
        }
        if (weaponName.isBlank()) {
            weaponName = readStaticInfoDisplayName(root, visited);
        }
        if (weaponName.isBlank() || projectileName.isBlank()) {
            WeaponInfo inherited = readInheritedWeaponInfo(root, visited);
            if (inherited != null) {
                if (weaponName.isBlank()) {
                    weaponName = inherited.weaponName();
                }
                if (projectileName.isBlank()) {
                    projectileName = inherited.rawProjectileName();
                }
            }
        }
        if (weaponName.isBlank()) {
            weaponName = BlueprintUtils.prettifyName(deriveBaseName(path));
        }
        return new WeaponInfo(weaponName, projectileName);
    }

    private String readWeaponDisplayName(JsonNode properties) {
        String candidate = TextUtils.readText(JsonUtils.findFirstProperty(properties, "DisplayName"));
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return readTextRecursive(properties, "DisplayName");
    }

    private String readStaticInfoDisplayName(JsonNode root, Set<Path> visited) {
        if (root == null || root.isMissingNode()) {
            return "";
        }
        JsonNode staticInfoRef = findReferenceRecursive(root, "ItemStaticInfoClass");
        if (staticInfoRef == null || staticInfoRef.isMissingNode()) {
            return "";
        }
        String objectPath = staticInfoRef.path("ObjectPath").asText(null);
        if (objectPath == null || objectPath.isBlank()) {
            return "";
        }
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null || !Files.exists(resolved)) {
            if (logger != null) {
                logger.missing(objectPath, "weapon static info");
            }
            return "";
        }
        return readDisplayNameFromAsset(resolved, visited);
    }

    private String readDisplayNameFromAsset(Path path, Set<Path> visited) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!visited.add(normalized)) {
            return "";
        }
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read weapon static info from " + path, e);
        }
        if (root != null && root.isArray()) {
            for (JsonNode node : root) {
                JsonNode properties = node.path("Properties");
                if (!properties.isObject()) {
                    continue;
                }
                String candidate = readWeaponDisplayName(properties);
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        return "";
    }

    private WeaponInfo readInheritedWeaponInfo(JsonNode root, Set<Path> visited) {
        List<String> inheritancePaths = findInheritancePaths(root);
        for (String inheritancePath : inheritancePaths) {
            Path resolved = resolver.resolve(inheritancePath);
            if (resolved == null || !Files.exists(resolved)) {
                if (logger != null) {
                    logger.missing(inheritancePath, "weapon inheritance");
                }
                continue;
            }
            WeaponInfo info = readWeaponInfoCached(resolved, visited);
            if (info != null && (!info.weaponName().isBlank() || !info.rawProjectileName().isBlank())) {
                return info;
            }
        }
        return null;
    }

    private List<String> findInheritancePaths(JsonNode root) {
        if (root == null || root.isMissingNode() || !root.isArray()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (JsonNode node : root) {
            String superPath = node.path("Super").path("ObjectPath").asText(null);
            addPath(unique, superPath);
            String templatePath = node.path("Template").path("ObjectPath").asText(null);
            addPath(unique, templatePath);
            String classDefaultPath = node.path("ClassDefaultObject").path("ObjectPath").asText(null);
            addPath(unique, classDefaultPath);
        }
        return List.copyOf(unique.values());
    }

    private void addPath(Map<String, String> unique, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String key = path.toLowerCase(Locale.ROOT);
        unique.putIfAbsent(key, path);
    }

    private String extractProjectileName(JsonNode properties) {
        if (properties == null || properties.isMissingNode()) {
            return "";
        }
        JsonNode weaponConfig = JsonUtils.findFirstProperty(properties, "WeaponConfig");
        String projectile = extractProjectileFromNode(weaponConfig);
        if (projectile != null && !projectile.isBlank()) {
            return projectile;
        }
        projectile = extractProjectileFromNode(properties);
        if (projectile != null && !projectile.isBlank()) {
            return projectile;
        }
        return readProjectileRecursive(properties);
    }

    private String extractProjectileFromNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        JsonNode projectileNode = JsonUtils.findFirstProperty(node, "ProjectileClass");
        String projectile = BlueprintUtils.extractReferenceName(projectileNode);
        if (projectile != null && !projectile.isBlank()) {
            return projectile;
        }
        JsonNode tracerNode = JsonUtils.findFirstProperty(node, "TracerProjectileClass");
        return safeReferenceName(tracerNode);
    }

    private String readProjectileRecursive(JsonNode node) {
        String projectile = readReferenceNameRecursive(node, "ProjectileClass");
        if (projectile != null && !projectile.isBlank()) {
            return projectile;
        }
        return readReferenceNameRecursive(node, "TracerProjectileClass");
    }

    private String safeReferenceName(JsonNode reference) {
        String name = BlueprintUtils.extractReferenceName(reference);
        return name != null ? name : "";
    }

    private String deriveBaseName(Path path) {
        if (path == null) {
            return "Unknown";
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return "Unknown";
        }
        String raw = fileName.toString();
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(0, dot) : raw;
    }

    private String readTextRecursive(JsonNode node, String fragment) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                String candidate = readTextRecursive(element, fragment);
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return "";
        }
        if (!node.isObject()) {
            return "";
        }
        for (Map.Entry<String, JsonNode> entry : iterable(node.fields())) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key != null && key.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))) {
                String candidate = TextUtils.readText(value);
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            String candidate = readTextRecursive(value, fragment);
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private JsonNode findReferenceRecursive(JsonNode node, String fragment) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                JsonNode candidate = findReferenceRecursive(element, fragment);
                if (candidate != null && !candidate.isMissingNode()) {
                    return candidate;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> entry : iterable(node.fields())) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key != null && key.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))) {
                return value;
            }
            JsonNode candidate = findReferenceRecursive(value, fragment);
            if (candidate != null && !candidate.isMissingNode()) {
                return candidate;
            }
        }
        return null;
    }

    private String readReferenceNameRecursive(JsonNode node, String fragment) {
        JsonNode reference = findReferenceRecursive(node, fragment);
        if (reference == null || reference.isMissingNode()) {
            return "";
        }
        return safeReferenceName(reference);
    }

    private Iterable<Map.Entry<String, JsonNode>> iterable(Iterator<Map.Entry<String, JsonNode>> iterator) {
        return () -> iterator;
    }

    private List<VehicleWeapon> mergeWeapons(List<VehicleWeapon> weapons) {
        if (weapons == null || weapons.isEmpty()) {
            return List.of();
        }
        Map<String, VehicleWeapon> merged = new LinkedHashMap<>();
        for (VehicleWeapon weapon : weapons) {
            if (weapon == null) {
                continue;
            }
            String key = buildWeaponKey(weapon);
            if (!key.isBlank()) {
                merged.putIfAbsent(key, weapon);
            }
        }
        return List.copyOf(merged.values());
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

    private record WeaponInfo(String weaponName, String rawProjectileName) {
    }
}
