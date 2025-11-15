package com.pipemasters.assets;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public class AssetsParser {
    private static final String VEHICLE_ICON = "questionmark";

    private static final Map<String, String> VEHICLE_SIZE_BY_SETTINGS = Map.of(
            "Ground_QuadBikeSize", "QuadBike",
            "Ground_CarSize", "Car",
            "Air_Helo", "Helicopter"
    );

    private static final Map<String, List<AssetPriority>> VEHICLE_PRIORITIES_BY_SETTINGS = Map.of(
            "Ground_LOG", List.of(new AssetPriority("LOGI", "10\"")),
            "Ground_CarSize", List.of(
                    new AssetPriority("MRAP", "10\""),
                    new AssetPriority("LTV", "10\""),
                    new AssetPriority("TD", "8\""),
                    new AssetPriority("RSV", "0\"")
            ),
            "Ground_LightArmor", List.of(
                    new AssetPriority("IFV", "10\""),
                    new AssetPriority("APC", "10\"")
            ),
            "Ground_MBT", List.of(new AssetPriority("MBT", "10\""))
    );

    private static final Map<String, DeployableConfig> DEPLOYABLE_SETTINGS = Map.of(
            "AmmoBox", new DeployableConfig("Ammo Crate", "deployable_ammocrate"),
            "RepairStation", new DeployableConfig("Repair Station", "inventory_category_repair")
    );

    public Assets parse(JsonNode root) {
        Objects.requireNonNull(root, "root");
        if (!root.isArray()) {
            throw new IllegalArgumentException("FModel export is expected to be a JSON array of objects");
        }

        Map<ComponentKey, ComponentDefinition> components = new LinkedHashMap<>();
        Map<String, List<ComponentDefinition>> componentsByOwner = new HashMap<>();

        List<VehicleSpawnerDefinition> vehicleSpawnerDefinitions = new ArrayList<>();
        List<HelipadDefinition> helipadDefinitions = new ArrayList<>();
        List<DeployableDefinition> deployableDefinitions = new ArrayList<>();

        for (JsonNode node : root) {
            String type = node.path("Type").asText();
            switch (type) {
                case "SceneComponent" -> registerComponent(parseSceneComponent(node), components, componentsByOwner);
                case "BP_VehicleSpawner_C" -> vehicleSpawnerDefinitions.add(parseVehicleSpawner(node));
                case "BP_SQDeployableSpawner_C" -> deployableDefinitions.add(parseDeployable(node));
                case "BP_helicopter_repair_pad_C" -> helipadDefinitions.add(parseHelipad(node));
                default -> {
                }
            }
        }

        TransformResolver resolver = new TransformResolver(components);

        List<VehicleSpawner> vehicleSpawners = new ArrayList<>(vehicleSpawnerDefinitions.size());
        for (VehicleSpawnerDefinition definition : vehicleSpawnerDefinitions) {
            ResolvedTransform transform = resolveActorTransform(definition.name(), componentsByOwner, resolver);
            vehicleSpawners.add(new VehicleSpawner(
                    VEHICLE_ICON,
                    definition.name(),
                    definition.team(),
                    determineVehicleSize(definition),
                    definition.maxNum(),
                    transform.location().x(),
                    transform.location().y(),
                    transform.location().z(),
                    transform.rotation().pitch(),
                    transform.rotation().roll(),
                    transform.rotation().yaw(),
                    determineVehiclePriorities(definition),
                    Collections.emptyList()
            ));
        }

        List<Helipad> helipads = new ArrayList<>(helipadDefinitions.size());
        for (HelipadDefinition definition : helipadDefinitions) {
            ResolvedTransform transform = resolveActorTransform(definition.name(), componentsByOwner, resolver);
            helipads.add(new Helipad(
                    definition.name(),
                    "deployable_helipad",
                    definition.team(),
                    transform.location().x(),
                    transform.location().y(),
                    transform.location().z(),
                    transform.rotation().pitch(),
                    transform.rotation().roll(),
                    transform.rotation().yaw()
            ));
        }

        List<Deployable> deployables = new ArrayList<>(deployableDefinitions.size());
        for (DeployableDefinition definition : deployableDefinitions) {
            ResolvedTransform transform = resolveActorTransform(definition.name(), componentsByOwner, resolver);
            DeployableConfig config = DEPLOYABLE_SETTINGS.getOrDefault(definition.settingsName(), DeployableConfig.UNKNOWN);
            deployables.add(new Deployable(
                    config.type(),
                    config.icon(),
                    definition.team(),
                    transform.location().x(),
                    transform.location().y(),
                    transform.location().z(),
                    transform.rotation().pitch(),
                    transform.rotation().roll(),
                    transform.rotation().yaw()
            ));
        }

        return new Assets(vehicleSpawners, helipads, deployables);
    }

    private ComponentDefinition parseSceneComponent(JsonNode node) {
        ComponentKey key = createComponentKey(node);
        if (key == null) {
            return null;
        }
        JsonNode properties = node.path("Properties");
        Vector3D location = readVector(properties.path("RelativeLocation"), "X", "Y", "Z", Vector3D.ZERO);
        Rotation rotation = readRotation(properties.path("RelativeRotation"));
        Vector3D scale = readVector(properties.path("RelativeScale3D"), "X", "Y", "Z", Vector3D.ONES);
        ComponentKey parentKey = parseAttachParent(properties.path("AttachParent"));
        return new ComponentDefinition(key, parentKey, new Transform(location, rotation), scale);
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

    private VehicleSpawnerDefinition parseVehicleSpawner(JsonNode node) {
        String name = node.path("Name").asText();
        JsonNode properties = node.path("Properties");
        String team = toTeamName(properties.path("Team").asText(null));
        String settingsName = extractReferenceName(properties.path("Settings"));
        int maxNum = properties.path("MaxNum").asInt(0);
        return new VehicleSpawnerDefinition(name, team, settingsName, maxNum);
    }

    private DeployableDefinition parseDeployable(JsonNode node) {
        String name = node.path("Name").asText();
        JsonNode properties = node.path("Properties");
        String team = toTeamName(properties.path("Team").asText(null));
        String settingsName = extractReferenceName(properties.path("Settings"));
        return new DeployableDefinition(name, team, settingsName);
    }

    private HelipadDefinition parseHelipad(JsonNode node) {
        String name = node.path("Name").asText();
        String team = toTeamName(node.path("Properties").path("InitialTeam").asText(null));
        return new HelipadDefinition(name, team);
    }

    private ResolvedTransform resolveActorTransform(String owner,
                                                    Map<String, List<ComponentDefinition>> componentsByOwner,
                                                    TransformResolver resolver) {
        List<ComponentDefinition> definitions = componentsByOwner.get(owner);
        if (definitions == null || definitions.isEmpty()) {
            return ResolvedTransform.IDENTITY;
        }
        ComponentDefinition candidate = findPreferredComponent(definitions, "DefaultSceneRoot");
        if (candidate == null) {
            candidate = findPreferredComponent(definitions, "Root");
        }
        if (candidate == null) {
            candidate = definitions.stream().findFirst().orElse(null);
        }
        if (candidate == null) {
            return ResolvedTransform.IDENTITY;
        }
        return resolver.resolve(candidate.key());
    }

    private ComponentDefinition findPreferredComponent(List<ComponentDefinition> definitions, String name) {
        for (ComponentDefinition definition : definitions) {
            if (definition.key().name().equals(name)) {
                return definition;
            }
        }
        return null;
    }

    private String determineVehicleSize(VehicleSpawnerDefinition definition) {
        String settingsName = definition.settingsName();
        if (settingsName != null) {
            String size = VEHICLE_SIZE_BY_SETTINGS.get(settingsName);
            if (size != null) {
                return size;
            }
        }
        String lowerName = definition.name().toLowerCase(Locale.ROOT);
        if (lowerName.contains("bike") || lowerName.contains("quad")) {
            return "QuadBike";
        }
        if (lowerName.contains("car") || lowerName.contains("ltv") || lowerName.contains("mrap")) {
            return "Car";
        }
        if (lowerName.contains("lightarmor") || lowerName.contains("ifv") || lowerName.contains("apc")) {
            return "MBT";
        }
        if (lowerName.contains("helo") || lowerName.contains("heli") || lowerName.contains("air")) {
            return "Helicopter";
        }
        return "MBT";
    }

    private List<AssetPriority> determineVehiclePriorities(VehicleSpawnerDefinition definition) {
        String settingsName = definition.settingsName();
        if (settingsName != null) {
            List<AssetPriority> priorities = VEHICLE_PRIORITIES_BY_SETTINGS.get(settingsName);
            if (priorities != null) {
                return priorities;
            }
        }
        if (settingsName == null) {
            String lowerName = definition.name().toLowerCase(Locale.ROOT);
            if (lowerName.contains("lightarmor")) {
                return VEHICLE_PRIORITIES_BY_SETTINGS.get("Ground_LightArmor");
            }
            if (lowerName.contains("logi") || lowerName.contains("transport")) {
                return VEHICLE_PRIORITIES_BY_SETTINGS.get("Ground_LOG");
            }
            if (lowerName.contains("mbt")) {
                return VEHICLE_PRIORITIES_BY_SETTINGS.get("Ground_MBT");
            }
        }
        return Collections.emptyList();
    }

    private String toTeamName(String rawTeam) {
        if (rawTeam == null) {
            return "Neutral";
        }
        return switch (rawTeam) {
            case "ESQTeam::Team_One" -> "Team One";
            case "ESQTeam::Team_Two" -> "Team Two";
            default -> "Neutral";
        };
    }

    private ComponentKey createComponentKey(JsonNode node) {
        String owner = node.path("Outer").asText(null);
        String name = node.path("Name").asText(null);
        if (owner == null || name == null || owner.isBlank() || name.isBlank()) {
            return null;
        }
        return new ComponentKey(owner, name);
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
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String owner = normalized.substring(0, lastDot);
        String componentName = normalized.substring(lastDot + 1);
        owner = normalizeOwner(owner);
        return new ComponentKey(owner, componentName);
    }

    private String normalizeOwner(String owner) {
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

    private String extractReferenceName(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            return null;
        }
        String objectName = reference.path("ObjectName").asText(null);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        return extractInnerName(objectName);
    }

    private String extractInnerName(String objectName) {
        int first = objectName.indexOf('\'');
        int last = objectName.lastIndexOf('\'');
        if (first >= 0 && last > first) {
            return objectName.substring(first + 1, last);
        }
        return objectName;
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

    private record VehicleSpawnerDefinition(String name, String team, String settingsName, int maxNum) {
    }

    private record DeployableDefinition(String name, String team, String settingsName) {
    }

    private record HelipadDefinition(String name, String team) {
    }

    private record ComponentKey(String owner, String name) {
    }

    private record ComponentDefinition(ComponentKey key,
                                       ComponentKey parentKey,
                                       Transform localTransform,
                                       Vector3D scale) {
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

    private record DeployableConfig(String type, String icon) {
        static final DeployableConfig UNKNOWN = new DeployableConfig("", "questionmark");
    }
}