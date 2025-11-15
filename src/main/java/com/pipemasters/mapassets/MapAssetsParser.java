package com.pipemasters.mapassets;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public class MapAssetsParser {
    private static final double DEFAULT_DEPLOYABLE_LOCK_DISTANCE = 15000.0;
    private static final int TEMP_SPAWN_LIFESPAN = 240;

    public MapAssets parse(JsonNode root) {
        Objects.requireNonNull(root, "root");
        if (!root.isArray()) {
            throw new IllegalArgumentException("FModel export is expected to be a JSON array of objects");
        }

        Map<ComponentKey, ComponentDefinition> components = new LinkedHashMap<>();
        Map<String, List<ComponentDefinition>> componentsByOwner = new HashMap<>();

        List<ProtectionZoneDefinition> protectionZoneDefinitions = new ArrayList<>();
        List<SpawnGroupDefinition> spawnGroupDefinitions = new ArrayList<>();
        List<SpawnPointDefinition> spawnPointDefinitions = new ArrayList<>();

        for (JsonNode node : root) {
            String type = node.path("Type").asText();
            switch (type) {
                case "SceneComponent" -> registerComponent(parseSceneComponent(node), components, componentsByOwner);
                case "BoxComponent" -> registerComponent(parseBoxComponent(node), components, componentsByOwner);
                case "SphereComponent" -> registerComponent(parseSphereComponent(node), components, componentsByOwner);
                case "CapsuleComponent" ->
                        registerComponent(parseCapsuleComponent(node), components, componentsByOwner);
                case "Gameplay_TeamZone_C" -> protectionZoneDefinitions.add(parseProtectionZone(node));
                case "SQTeamSpawnGroup" -> spawnGroupDefinitions.add(parseSpawnGroup(node));
                case "SQTeamSpawnPoint" -> spawnPointDefinitions.add(parseSpawnPoint(node));
                default -> {
                }
            }
        }

        TransformResolver resolver = new TransformResolver(components);

        List<ProtectionZone> protectionZones = buildProtectionZones(protectionZoneDefinitions, componentsByOwner, resolver);
        Map<String, SpawnGroup> spawnGroupsByName = buildSpawnGroups(spawnGroupDefinitions, componentsByOwner, resolver);
        List<SpawnGroup> spawnGroups = new ArrayList<>(spawnGroupDefinitions.size());
        for (SpawnGroupDefinition definition : spawnGroupDefinitions) {
            SpawnGroup group = spawnGroupsByName.get(definition.name());
            if (group != null) {
                spawnGroups.add(group);
            }
        }
        List<SpawnPoint> spawnPoints = buildSpawnPoints(spawnPointDefinitions, componentsByOwner, resolver, spawnGroupsByName);

        return new MapAssets(protectionZones, spawnGroups, spawnPoints);
    }

    private ComponentDefinition parseSceneComponent(JsonNode node) {
        return parseComponent(node, ComponentType.SCENE, Vector3D.ZERO, 0.0, 0.0, 0.0);
    }

    private ComponentDefinition parseBoxComponent(JsonNode node) {
        JsonNode extentNode = node.path("Properties").path("BoxExtent");
        Vector3D extent = readVector(extentNode, "X", "Y", "Z", Vector3D.ZERO);
        return parseComponent(node, ComponentType.BOX, extent, 0.0, 0.0, 0.0);
    }

    private ComponentDefinition parseSphereComponent(JsonNode node) {
        double radius = node.path("Properties").path("SphereRadius").asDouble(0.0);
        return parseComponent(node, ComponentType.SPHERE, Vector3D.ZERO, radius, 0.0, 0.0);
    }

    private ComponentDefinition parseCapsuleComponent(JsonNode node) {
        JsonNode properties = node.path("Properties");
        double radius = properties.path("CapsuleRadius").asDouble(0.0);
        double halfHeight = properties.path("CapsuleHalfHeight").asDouble(0.0);
        return parseComponent(node, ComponentType.CAPSULE, Vector3D.ZERO, 0.0, radius, halfHeight);
    }

    private ComponentDefinition parseComponent(JsonNode node,
                                               ComponentType type,
                                               Vector3D extent,
                                               double sphereRadius,
                                               double capsuleRadius,
                                               double capsuleHalfHeight) {
        ComponentKey key = createComponentKey(node);
        if (key == null) {
            return null;
        }
        JsonNode properties = node.path("Properties");
        Vector3D location = readVector(properties.path("RelativeLocation"), "X", "Y", "Z", Vector3D.ZERO);
        Rotation rotation = readRotation(properties.path("RelativeRotation"));
        Vector3D scale = readVector(properties.path("RelativeScale3D"), "X", "Y", "Z", Vector3D.ONES);
        ComponentKey parentKey = parseAttachParent(properties.path("AttachParent"));
        return new ComponentDefinition(key, type, parentKey, new Transform(location, rotation), scale, extent, sphereRadius, capsuleRadius, capsuleHalfHeight);
    }

    private ComponentKey createComponentKey(JsonNode node) {
        String owner = node.path("Outer").asText(null);
        String name = node.path("Name").asText(null);
        if (owner == null || name == null || owner.isBlank() || name.isBlank()) {
            return null;
        }
        return new ComponentKey(owner, name);
    }

    private void registerComponent(ComponentDefinition definition,
                                   Map<ComponentKey, ComponentDefinition> components,
                                   Map<String, List<ComponentDefinition>> componentsByOwner) {
        if (definition == null || definition.key() == null) {
            return;
        }
        components.put(definition.key(), definition);
        componentsByOwner.computeIfAbsent(definition.key().owner(), key -> new ArrayList<>()).add(definition);
    }

    private ProtectionZoneDefinition parseProtectionZone(JsonNode node) {
        String name = node.path("Name").asText();
        JsonNode properties = node.path("Properties");
        int teamId = properties.path("TeamId").asInt(0);
        double deployableLockDistance = properties.path("DeployableLockDistance").asDouble(DEFAULT_DEPLOYABLE_LOCK_DISTANCE);
        return new ProtectionZoneDefinition(name, teamId, deployableLockDistance);
    }

    private SpawnGroupDefinition parseSpawnGroup(JsonNode node) {
        String name = node.path("Name").asText();
        boolean temporary = name != null && name.toLowerCase(Locale.ROOT).contains("temp");
        return new SpawnGroupDefinition(name, temporary);
    }

    private SpawnPointDefinition parseSpawnPoint(JsonNode node) {
        String name = node.path("Name").asText();
        String groupReference = extractObjectOuter(node.path("Properties").path("Group"));
        return new SpawnPointDefinition(name, groupReference);
    }

    private List<ProtectionZone> buildProtectionZones(List<ProtectionZoneDefinition> definitions,
                                                      Map<String, List<ComponentDefinition>> componentsByOwner,
                                                      TransformResolver resolver) {
        List<ProtectionZone> protectionZones = new ArrayList<>();
        for (ProtectionZoneDefinition definition : definitions) {
            List<MapAssetObject> objects = buildObjects(definition.name(), componentsByOwner, resolver);
            String displayName = prettifyName(definition.name());
            protectionZones.add(new ProtectionZone(
                    displayName,
                    definition.deployableLockDistance(),
                    Integer.toString(definition.teamId()),
                    objects
            ));
        }
        protectionZones.sort(Comparator.comparing(ProtectionZone::displayName));
        return protectionZones;
    }

    private Map<String, SpawnGroup> buildSpawnGroups(List<SpawnGroupDefinition> definitions,
                                                     Map<String, List<ComponentDefinition>> componentsByOwner,
                                                     TransformResolver resolver) {
        Map<String, SpawnGroup> spawnGroups = new LinkedHashMap<>();
        for (SpawnGroupDefinition definition : definitions) {
            Vector3D location = resolveActorLocation(definition.name(), componentsByOwner, resolver).orElse(Vector3D.ZERO);
            String team = deriveTeamName(definition.name());
            int lifeSpan = definition.temporary() ? TEMP_SPAWN_LIFESPAN : 0;
            String displayName = adjustSpawnTokens(prettifyName(definition.name()));
            SpawnGroup group = new SpawnGroup(
                    location.x(),
                    location.y(),
                    location.z(),
                    team,
                    lifeSpan,
                    true,
                    displayName
            );
            spawnGroups.put(definition.name(), group);
        }
        return spawnGroups;
    }

    private List<SpawnPoint> buildSpawnPoints(List<SpawnPointDefinition> definitions,
                                              Map<String, List<ComponentDefinition>> componentsByOwner,
                                              TransformResolver resolver,
                                              Map<String, SpawnGroup> spawnGroupsByName) {
        List<SpawnPoint> spawnPoints = new ArrayList<>();
        for (SpawnPointDefinition definition : definitions) {
            Vector3D location = resolveActorLocation(definition.name(), componentsByOwner, resolver).orElse(Vector3D.ZERO);
            SpawnGroup group = spawnGroupsByName.get(definition.groupName());
            String team = group != null ? group.team() : deriveTeamName(definition.name());
            int lifeSpan = group != null ? group.initialLifeSpan() : 0;
            String spawnGroupName = group != null ? group.displayName() : adjustSpawnTokens(prettifyName(definition.groupName()));
            spawnPoints.add(new SpawnPoint(
                    location.x(),
                    location.y(),
                    location.z(),
                    team,
                    lifeSpan,
                    true,
                    spawnGroupName
            ));
        }
        return spawnPoints;
    }

    private List<MapAssetObject> buildObjects(String owner,
                                              Map<String, List<ComponentDefinition>> componentsByOwner,
                                              TransformResolver resolver) {
        List<ComponentDefinition> definitions = componentsByOwner.getOrDefault(owner, List.of());
        List<MapAssetObject> objects = new ArrayList<>();
        for (ComponentDefinition definition : definitions) {
            if (!definition.type().isRenderable()) {
                continue;
            }
            if (shouldSkipComponent(definition)) {
                continue;
            }
            ResolvedTransform transform = resolver.resolve(definition.key());
            MapAssetObject object = toObject(definition, transform);
            if (object != null) {
                objects.add(object);
            }
        }
        objects.sort(Comparator.comparing(MapAssetObject::objectName));
        return objects;
    }

    private boolean shouldSkipComponent(ComponentDefinition definition) {
        String name = definition.key().name();
        return "DummyPresetCollision".equals(name);
    }

    private MapAssetObject toObject(ComponentDefinition definition, ResolvedTransform transform) {
        double locationX = transform.location().x();
        double locationY = transform.location().y();
        double locationZ = transform.location().z();
        Rotation rotation = transform.rotation();
        Vector3D scale = transform.scale();

        return switch (definition.type()) {
            case BOX -> createBoxObject(definition, locationX, locationY, locationZ, rotation, scale);
            case SPHERE -> createSphereObject(definition, locationX, locationY, locationZ, rotation, scale);
            case CAPSULE -> createCapsuleObject(definition, locationX, locationY, locationZ, rotation, scale);
            default -> null;
        };
    }

    private MapAssetObject createBoxObject(ComponentDefinition definition,
                                           double locationX,
                                           double locationY,
                                           double locationZ,
                                           Rotation rotation,
                                           Vector3D scale) {
        Vector3D extent = definition.extent().multiply(scale);
        double radius = Math.sqrt(extent.x() * extent.x() + extent.y() * extent.y() + extent.z() * extent.z());
        MapAssetObjectExtent boxExtent = new MapAssetObjectExtent(
                extent.x(),
                extent.y(),
                extent.z(),
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw()
        );
        return new MapAssetObject(
                definition.key().name(),
                false,
                radius,
                locationX,
                locationY,
                locationZ,
                true,
                boxExtent,
                false
        );
    }

    private MapAssetObject createSphereObject(ComponentDefinition definition,
                                              double locationX,
                                              double locationY,
                                              double locationZ,
                                              Rotation rotation,
                                              Vector3D scale) {
        double radius = definition.sphereRadius() * scale.x();
        MapAssetObjectExtent boxExtent = new MapAssetObjectExtent(
                radius,
                radius,
                radius,
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw()
        );
        return new MapAssetObject(
                definition.key().name(),
                true,
                radius,
                locationX,
                locationY,
                locationZ,
                false,
                boxExtent,
                false
        );
    }

    private MapAssetObject createCapsuleObject(ComponentDefinition definition,
                                               double locationX,
                                               double locationY,
                                               double locationZ,
                                               Rotation rotation,
                                               Vector3D scale) {
        double radius = definition.capsuleRadius() * scale.x();
        double halfHeight = definition.capsuleHalfHeight() * scale.z();
        MapAssetObjectExtent boxExtent = new MapAssetObjectExtent(
                radius,
                radius,
                halfHeight,
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw()
        );
        return new MapAssetObject(
                definition.key().name(),
                false,
                radius,
                locationX,
                locationY,
                locationZ,
                false,
                boxExtent,
                true
        );
    }

    private Optional<Vector3D> resolveActorLocation(String owner,
                                                    Map<String, List<ComponentDefinition>> componentsByOwner,
                                                    TransformResolver resolver) {
        List<ComponentDefinition> definitions = componentsByOwner.get(owner);
        if (definitions == null || definitions.isEmpty()) {
            return Optional.empty();
        }
        for (ComponentDefinition definition : definitions) {
            if (definition.type() == ComponentType.CAPSULE || definition.type() == ComponentType.SPHERE || definition.type() == ComponentType.BOX) {
                ResolvedTransform transform = resolver.resolve(definition.key());
                return Optional.of(transform.location());
            }
        }
        ComponentDefinition first = definitions.getFirst();
        ResolvedTransform transform = resolver.resolve(first.key());
        return Optional.of(transform.location());
    }

    private Vector3D readVector(JsonNode node, String xField, String yField, String zField, Vector3D defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }
        double x = node.path(xField).asDouble(0.0);
        double y = node.path(yField).asDouble(0.0);
        double z = node.path(zField).asDouble(0.0);
        return new Vector3D(x, y, z);
    }

    private Rotation readRotation(JsonNode rotationNode) {
        if (rotationNode == null || rotationNode.isMissingNode()) {
            return Rotation.ZERO;
        }
        double pitch = rotationNode.path("Pitch").asDouble(0.0);
        double yaw = rotationNode.path("Yaw").asDouble(0.0);
        double roll = rotationNode.path("Roll").asDouble(0.0);
        return new Rotation(pitch, yaw, roll);
    }

    private ComponentKey parseAttachParent(JsonNode attachParent) {
        if (attachParent == null || attachParent.isMissingNode()) {
            return null;
        }
        String objectName = attachParent.path("ObjectName").asText(null);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String normalized = extractInnerName(objectName);
        if (normalized == null) {
            return null;
        }
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String owner = normalizeOwner(normalized.substring(0, lastDot));
        String name = normalized.substring(lastDot + 1);
        return new ComponentKey(owner, name);
    }

    private String normalizeOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return owner;
        }
        int lastColon = owner.lastIndexOf(':');
        if (lastColon >= 0 && lastColon < owner.length() - 1) {
            owner = owner.substring(lastColon + 1);
        }
        int lastDot = owner.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < owner.length() - 1) {
            owner = owner.substring(lastDot + 1);
        }
        return owner;
    }

    private String extractInnerName(String objectName) {
        int first = objectName.indexOf('\'');
        int last = objectName.lastIndexOf('\'');
        if (first >= 0 && last > first) {
            return objectName.substring(first + 1, last);
        }
        return objectName;
    }

    private String extractObjectOuter(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            return null;
        }
        String objectName = reference.path("ObjectName").asText(null);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String normalized = extractInnerName(objectName);
        if (normalized == null) {
            return null;
        }
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0) {
            return normalized;
        }
        return normalized.substring(lastDot + 1);
    }

    private String prettifyName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String noTrailingIndex = value.replaceAll("_[0-9]+$", "");
        String withSpaces = noTrailingIndex.replace('_', ' ');
        StringBuilder result = new StringBuilder();
        char[] chars = withSpaces.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            if (i > 0 && Character.isUpperCase(current)) {
                char previous = chars[i - 1];
                if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
                    result.append(' ');
                }
            }
            result.append(current);
        }
        return result.toString().trim();
    }

    private String adjustSpawnTokens(String displayName) {
        if (displayName == null) {
            return null;
        }
        return displayName
                .replace("Spawn Group", "SpawnGroup")
                .replace("Spawn Point", "SpawnPoint");
    }

    private String deriveTeamName(String actorName) {
        if (actorName == null) {
            return "Neutral";
        }
        String lower = actorName.toLowerCase(Locale.ROOT);
        if (lower.contains("team1")) {
            return "Team One";
        }
        if (lower.contains("team2")) {
            return "Team Two";
        }
        return "Neutral";
    }

    private record ProtectionZoneDefinition(String name, int teamId, double deployableLockDistance) {
    }

    private record SpawnGroupDefinition(String name, boolean temporary) {
    }

    private record SpawnPointDefinition(String name, String groupName) {
    }

    private enum ComponentType {
        SCENE,
        BOX,
        SPHERE,
        CAPSULE;

        boolean isRenderable() {
            return this == BOX || this == SPHERE || this == CAPSULE;
        }
    }

    private record ComponentKey(String owner, String name) {
    }

    private record ComponentDefinition(ComponentKey key,
                                       ComponentType type,
                                       ComponentKey parentKey,
                                       Transform localTransform,
                                       Vector3D scale,
                                       Vector3D extent,
                                       double sphereRadius,
                                       double capsuleRadius,
                                       double capsuleHalfHeight) {
    }

    private record Transform(Vector3D location, Rotation rotation) {
    }

    private record Vector3D(double x, double y, double z) {
        static final Vector3D ZERO = new Vector3D(0.0, 0.0, 0.0);
        static final Vector3D ONES = new Vector3D(1.0, 1.0, 1.0);

        Vector3D add(Vector3D other) {
            return new Vector3D(x + other.x, y + other.y, z + other.z);
        }

        Vector3D multiply(Vector3D other) {
            return new Vector3D(x * other.x, y * other.y, z * other.z);
        }
    }

    private record Rotation(double pitch, double yaw, double roll) {
        static final Rotation ZERO = new Rotation(0.0, 0.0, 0.0);

        Rotation add(Rotation other) {
            double[][] combined = multiplyMatrices(toMatrix(), other.toMatrix());
            return fromMatrix(combined);
        }

        Vector3D rotate(Vector3D vector) {
            double[][] matrix = toMatrix();
            double rx = matrix[0][0] * vector.x() + matrix[0][1] * vector.y() + matrix[0][2] * vector.z();
            double ry = matrix[1][0] * vector.x() + matrix[1][1] * vector.y() + matrix[1][2] * vector.z();
            double rz = matrix[2][0] * vector.x() + matrix[2][1] * vector.y() + matrix[2][2] * vector.z();

            return new Vector3D(rx, ry, rz);
        }

        private double[][] toMatrix() {
            double pitchRad = Math.toRadians(pitch);
            double yawRad = Math.toRadians(yaw);
            double rollRad = Math.toRadians(roll);

            double cp = Math.cos(pitchRad);
            double sp = Math.sin(pitchRad);
            double cy = Math.cos(yawRad);
            double sy = Math.sin(yawRad);
            double cr = Math.cos(rollRad);
            double sr = Math.sin(rollRad);

            double m00 = cy * cp;
            double m01 = cy * sp * sr - sy * cr;
            double m02 = cy * sp * cr + sy * sr;

            double m10 = sy * cp;
            double m11 = sy * sp * sr + cy * cr;
            double m12 = sy * sp * cr - cy * sr;

            double m20 = -sp;
            double m21 = cp * sr;
            double m22 = cp * cr;

            return new double[][]{
                    {m00, m01, m02},
                    {m10, m11, m12},
                    {m20, m21, m22}
            };
        }

        private static double[][] multiplyMatrices(double[][] a, double[][] b) {
            double[][] result = new double[3][3];
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    result[row][col] = a[row][0] * b[0][col]
                            + a[row][1] * b[1][col]
                            + a[row][2] * b[2][col];
                }
            }
            return result;
        }

        private static Rotation fromMatrix(double[][] matrix) {
            double pitchRad = Math.asin(-matrix[2][0]);
            double cp = Math.cos(pitchRad);

            double yawRad;
            double rollRad;
            if (Math.abs(cp) > 1e-6) {
                yawRad = Math.atan2(matrix[1][0], matrix[0][0]);
                rollRad = Math.atan2(matrix[2][1], matrix[2][2]);
            } else {
                yawRad = Math.atan2(-matrix[0][1], matrix[1][1]);
                rollRad = 0.0;
            }

            return new Rotation(
                    Math.toDegrees(pitchRad),
                    Math.toDegrees(yawRad),
                    Math.toDegrees(rollRad)
            );
        }
    }

    private record ResolvedTransform(Vector3D location, Rotation rotation, Vector3D scale) {
        static final ResolvedTransform IDENTITY = new ResolvedTransform(Vector3D.ZERO, Rotation.ZERO, Vector3D.ONES);
    }

    private static final class TransformResolver {
        private final Map<ComponentKey, ComponentDefinition> components;
        private final Map<ComponentKey, ResolvedTransform> cache = new HashMap<>();

        private TransformResolver(Map<ComponentKey, ComponentDefinition> components) {
            this.components = components;
        }

        private ResolvedTransform resolve(ComponentKey key) {
            if (key == null) {
                return ResolvedTransform.IDENTITY;
            }
            ResolvedTransform cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            ResolvedTransform resolved = resolveInternal(key);
            cache.put(key, resolved);
            return resolved;
        }

        private ResolvedTransform resolveInternal(ComponentKey key) {
            ComponentDefinition definition = components.get(key);
            if (definition == null) {
                return ResolvedTransform.IDENTITY;
            }
            ResolvedTransform parent = resolve(definition.parentKey());
            Vector3D scaledLocation = definition.localTransform().location().multiply(parent.scale());
            Vector3D rotatedLocation = parent.rotation().rotate(scaledLocation);
            Vector3D worldLocation = parent.location().add(rotatedLocation);
            Rotation worldRotation = parent.rotation().add(definition.localTransform().rotation());
            Vector3D worldScale = parent.scale().multiply(definition.scale());
            return new ResolvedTransform(worldLocation, worldRotation, worldScale);
        }
    }
}