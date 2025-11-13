package com.pipemasters.objectives;

import com.fasterxml.jackson.databind.JsonNode;
import com.pipemasters.capture.CaptureClusters;
import com.pipemasters.util.MainNameFormatter;

import java.math.BigDecimal;
import java.util.*;

public class ObjectivesParser {
    public ObjectivesParser() {
    }

    public Map<String, Objective> parseObjectives(JsonNode root, CaptureClusters captureClusters) {
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("FModel export is expected to be a JSON array of objects");
        }

        Map<ComponentKey, ComponentDefinition> components = new LinkedHashMap<>();
        Map<String, List<ComponentDefinition>> componentsByOwner = new HashMap<>();
        Map<String, String> pointDisplayNames = new HashMap<>();
        Set<String> captureZoneActors = new LinkedHashSet<>();
        Set<String> clusterActors = new LinkedHashSet<>();
        Set<String> mainActors = new LinkedHashSet<>();

        for (JsonNode node : root) {
            String type = node.path("Type").asText();
            switch (type) {
                case "SceneComponent" -> registerComponent(parseSceneComponent(node), components, componentsByOwner);
                case "BoxComponent" -> registerComponent(parseBoxComponent(node), components, componentsByOwner);
                case "SphereComponent" -> registerComponent(parseSphereComponent(node), components, componentsByOwner);
                case "CapsuleComponent" ->
                        registerComponent(parseCapsuleComponent(node), components, componentsByOwner);
                case "BP_CaptureZoneInvasion_C" -> captureZoneActors.add(node.path("Name").asText());
                case "BP_CaptureZoneCluster_C" -> clusterActors.add(node.path("Name").asText());
                case "BP_CaptureZoneMain_C" -> mainActors.add(node.path("Name").asText());
                case "SQCaptureZoneInvasionComponent" -> storeCaptureZoneDisplayName(node, pointDisplayNames);
                default -> {
                }
            }
        }

        TransformResolver resolver = new TransformResolver(components);
        Map<String, List<ObjectivePoint>> clusterPoints = new HashMap<>();

        Map<String, Integer> stageIndex = computeStageIndex(captureClusters);

        for (String zoneName : captureZoneActors) {
            ComponentKey zoneRootKey = new ComponentKey(zoneName, "DefaultSceneRoot");
            ResolvedTransform zoneTransform = resolver.resolve(zoneRootKey);
            ComponentDefinition zoneDefinition = components.get(zoneRootKey);
            if (zoneDefinition == null || zoneDefinition.parentKey() == null) {
                continue;
            }
            String clusterName = zoneDefinition.parentKey().owner();
            if (clusterName == null) {
                continue;
            }

            String displayName = pointDisplayNames.getOrDefault(zoneName, zoneName);
            List<ObjectiveObject> objects = buildObjectiveObjects(zoneName, resolver, componentsByOwner);
            ObjectivePoint point = new ObjectivePoint(
                    displayName,
                    zoneName,
                    zoneTransform.location().x(),
                    zoneTransform.location().y(),
                    zoneTransform.location().z(),
                    objects
            );
            clusterPoints.computeIfAbsent(clusterName, key -> new ArrayList<>()).add(point);
        }

        LinkedHashMap<String, Objective> objectives = new LinkedHashMap<>();
        List<String> sortedClusters = new ArrayList<>(clusterActors);
        sortedClusters.sort(Comparator.naturalOrder());
        for (String clusterName : sortedClusters) {
            List<ObjectivePoint> points = clusterPoints.getOrDefault(clusterName, List.of());
            List<ObjectivePoint> sortedPoints = sortPoints(points);
            ObjectiveLocation avgLocation = computeAverageLocation(sortedPoints);
            int pointPosition = stageIndex.getOrDefault(clusterName, 0);
            objectives.put(clusterName, new ObjectiveCluster(clusterName, pointPosition, avgLocation, sortedPoints));
        }

        List<ObjectiveWithKey> mainObjectives = new ArrayList<>();
        for (String mainName : mainActors) {
            ComponentKey mainRootKey = new ComponentKey(mainName, "DefaultSceneRoot");
            ResolvedTransform transform = resolver.resolve(mainRootKey);
            String displayName = formatMainDisplayName(mainName);
            List<ObjectiveObject> objects = buildObjectiveObjects(mainName, resolver, componentsByOwner);
            int pointPosition = stageIndex.getOrDefault(displayName, 0);
            ObjectiveMain main = new ObjectiveMain(
                    "Main",
                    displayName,
                    displayName,
                    transform.location().x(),
                    transform.location().y(),
                    transform.location().z(),
                    objects,
                    pointPosition
            );
            mainObjectives.add(new ObjectiveWithKey(displayName, main));
        }

        mainObjectives.sort(Comparator
                .comparingInt((ObjectiveWithKey entry) -> ((ObjectiveMain) entry.objective()).pointPosition())
                .thenComparing(ObjectiveWithKey::key));

        for (ObjectiveWithKey entry : mainObjectives) {
            objectives.put(entry.key(), entry.objective());
        }

        return objectives;
    }

    private void storeCaptureZoneDisplayName(JsonNode node, Map<String, String> pointDisplayNames) {
        String outer = node.path("Outer").asText();
        JsonNode flagName = node.path("Properties").path("FlagName");
        String displayName = flagName.path("LocalizedString").asText();
        if (displayName == null || displayName.isBlank()) {
            displayName = flagName.path("SourceString").asText();
        }
        if (displayName != null && !displayName.isBlank()) {
            pointDisplayNames.put(outer, displayName);
        }
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

    private ComponentKey parseAttachParent(JsonNode attachParent) {
        if (attachParent == null || attachParent.isMissingNode()) {
            return null;
        }
        String objectName = attachParent.path("ObjectName").asText(null);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        int firstQuote = objectName.indexOf('\'');
        int lastQuote = objectName.lastIndexOf('\'');
        if (firstQuote >= 0 && lastQuote > firstQuote) {
            objectName = objectName.substring(firstQuote + 1, lastQuote);
        }
        int persistentIndex = objectName.indexOf("PersistentLevel.");
        if (persistentIndex >= 0) {
            objectName = objectName.substring(persistentIndex + "PersistentLevel.".length());
        }
        int lastDot = objectName.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String owner = objectName.substring(0, lastDot);
        String name = objectName.substring(lastDot + 1);
        return new ComponentKey(owner, name);
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

    private Vector3D readVector(JsonNode node, String xField, String yField, String zField, Vector3D defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }
        double x = node.path(xField).asDouble(0.0);
        double y = node.path(yField).asDouble(0.0);
        double z = node.path(zField).asDouble(0.0);
        return new Vector3D(x, y, z);
    }

    private Map<String, Integer> computeStageIndex(CaptureClusters captureClusters) {
        Map<String, Integer> stageIndex = new HashMap<>();
        if (captureClusters == null) {
            return stageIndex;
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        Set<String> incoming = new HashSet<>();
        for (var link : captureClusters.links()) {
            adjacency.computeIfAbsent(link.nodeA(), key -> new ArrayList<>()).add(link.nodeB());
            incoming.add(link.nodeB());
        }

        String start = captureClusters.pointsOrder() != null && !captureClusters.pointsOrder().isEmpty()
                ? captureClusters.pointsOrder().getFirst()
                : adjacency.keySet().stream()
                .filter(node -> !incoming.contains(node))
                .findFirst()
                .orElse(null);

        if (start == null) {
            return stageIndex;
        }

        Map<String, Integer> distance = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        distance.put(start, 0);
        queue.addLast(start);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            int currentDistance = distance.get(current);
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (!distance.containsKey(next)) {
                    distance.put(next, currentDistance + 1);
                    queue.addLast(next);
                }
            }
        }

        distance.forEach((node, dist) -> stageIndex.put(node, dist + 1));
        return stageIndex;
    }

    private List<ObjectivePoint> sortPoints(List<ObjectivePoint> points) {
        return List.copyOf(points);
    }

    private ObjectiveLocation computeAverageLocation(List<ObjectivePoint> points) {
        if (points.isEmpty()) {
            return new ObjectiveLocation(0.0, 0.0, 0.0);
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        for (ObjectivePoint point : points) {
            sumX += point.locationX();
            sumY += point.locationY();
            sumZ += point.locationZ();
        }
        int size = points.size();
        return new ObjectiveLocation(sumX / size, sumY / size, sumZ / size);
    }

    private List<ObjectiveObject> buildObjectiveObjects(String owner,
                                                        TransformResolver resolver,
                                                        Map<String, List<ComponentDefinition>> componentsByOwner) {
        List<ComponentDefinition> definitions = componentsByOwner.getOrDefault(owner, List.of());
        List<ObjectiveVolume> volumes = new ArrayList<>();
        for (ComponentDefinition definition : definitions) {
            if (!definition.type().isRenderable()) {
                continue;
            }
            ResolvedTransform transform = resolver.resolve(definition.key());
            ObjectiveVolume volume = toObjectiveVolume(definition, transform);
            if (volume != null) {
                volumes.add(volume);
            }
        }
        volumes.sort(Comparator
                .comparingDouble(ObjectiveVolume::radius)
                .thenComparing(volume -> volume.object().objectName()));
        return volumes.stream()
                .map(ObjectiveVolume::object)
                .toList();
    }

    private ObjectiveVolume toObjectiveVolume(ComponentDefinition definition, ResolvedTransform transform) {
        double locationX = transform.location().x();
        double locationY = transform.location().y();
        double locationZ = transform.location().z();
        Rotation rotation = transform.rotation();
        Vector3D scale = transform.scale();

        return switch (definition.type()) {
            case BOX -> createBoxVolume(definition, transform, locationX, locationY, locationZ, rotation, scale);
            case SPHERE -> createSphereVolume(definition, locationX, locationY, locationZ, rotation, scale);
            case CAPSULE -> createCapsuleVolume(definition, locationX, locationY, locationZ, rotation, scale);
            default -> null;
        };
    }

    private ObjectiveVolume createBoxVolume(ComponentDefinition definition,
                                            ResolvedTransform transform,
                                            double locationX,
                                            double locationY,
                                            double locationZ,
                                            Rotation rotation,
                                            Vector3D scale) {
        Vector3D extent = definition.extent().multiply(scale);
        double radius = Math.sqrt(extent.x() * extent.x() + extent.y() * extent.y() + extent.z() * extent.z());
        // Squadcalc expects yaw to "rotation_z". what about roll and pitch? sharkman only knows...
        ObjectiveBoxExtent boxExtent = new ObjectiveBoxExtent(
                extent.x(),
                extent.y(),
                extent.z(),
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw(),
                scale.x(),
                scale.y(),
                scale.z()
        );
        ObjectiveObject object = new ObjectiveObject(
                definition.key().name(),
                locationX,
                locationY,
                locationZ,
                false,
                formatDecimal(radius),
                true,
                boxExtent,
                false,
                null,
                null,
                null,
                null,
                null
        );
        return new ObjectiveVolume(object, radius);
    }

    private ObjectiveVolume createSphereVolume(ComponentDefinition definition,
                                               double locationX,
                                               double locationY,
                                               double locationZ,
                                               Rotation rotation,
                                               Vector3D scale) {
        double radius = definition.sphereRadius() * scale.x();
        // Squadcalc expects yaw to "rotation_z". what about roll and pitch? sharkman only knows...
        ObjectiveBoxExtent boxExtent = new ObjectiveBoxExtent(
                radius,
                radius,
                radius,
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw(),
                scale.x(),
                scale.y(),
                scale.z()
        );
        ObjectiveObject object = new ObjectiveObject(
                definition.key().name(),
                locationX,
                locationY,
                locationZ,
                true,
                formatDecimal(radius),
                false,
                boxExtent,
                false,
                null,
                null,
                null,
                null,
                null
        );
        return new ObjectiveVolume(object, radius);
    }

    private ObjectiveVolume createCapsuleVolume(ComponentDefinition definition,
                                                double locationX,
                                                double locationY,
                                                double locationZ,
                                                Rotation rotation,
                                                Vector3D scale) {
        double radius = definition.capsuleRadius();
        double halfHeight = definition.capsuleHalfHeight();
        double scaledRadiusX = radius * scale.x();
        double scaledRadiusY = radius * scale.y();
        double scaledRadius = Math.max(scaledRadiusX, scaledRadiusY);
        double scaledHalfHeight = halfHeight * scale.z();
        double capsuleLength = scaledHalfHeight * 2.0;

        double boundingAxis = scaledHalfHeight + scaledRadius;
        Vector3D localExtents = new Vector3D(scaledRadiusX, scaledRadiusY, boundingAxis);
        Vector3D orientedExtents = rotation.rotateExtents(localExtents);
        // Squadcalc expects yaw to "rotation_z". what about roll and pitch? sharkman only knows...
        ObjectiveBoxExtent boxExtent = new ObjectiveBoxExtent(
                orientedExtents.x(),
                orientedExtents.y(),
                orientedExtents.z(),
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw(),
                scale.x(),
                scale.y(),
                scale.z()
        );

        String capsuleRadiusValue = formatDecimal(scaledRadius);
        String capsuleLengthValue = formatDecimal(capsuleLength);

        ObjectiveObject object = new ObjectiveObject(
                definition.key().name(),
                locationX,
                locationY,
                locationZ,
                false,
                capsuleLengthValue,
                false,
                boxExtent,
                true,
                capsuleRadiusValue,
                capsuleLengthValue,
                rotation.pitch(),
                rotation.roll(),
                rotation.yaw()
        );
        double effectiveRadius = Math.max(scaledRadius, capsuleLength / 2.0);
        return new ObjectiveVolume(object, effectiveRadius);
    }

    private String formatDecimal(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value);
        return decimal.stripTrailingZeros().toPlainString();
    }

    private String formatMainDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "Main";
        }
        return MainNameFormatter.normalize(name);
    }

    private record ObjectiveWithKey(String key, Objective objective) {
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
            return new Rotation(pitch + other.pitch, yaw + other.yaw, roll + other.roll);
        }

        Vector3D rotate(Vector3D vector) {
            double[][] matrix = toMatrix();
            double rx = matrix[0][0] * vector.x()
                    + matrix[0][1] * vector.y()
                    + matrix[0][2] * vector.z();
            double ry = matrix[1][0] * vector.x()
                    + matrix[1][1] * vector.y()
                    + matrix[1][2] * vector.z();
            double rz = matrix[2][0] * vector.x()
                    + matrix[2][1] * vector.y()
                    + matrix[2][2] * vector.z();

            return new Vector3D(rx, ry, rz);
        }

        Vector3D rotateExtents(Vector3D extents) {
            double[][] matrix = toMatrix();
            double ex = Math.abs(matrix[0][0]) * extents.x()
                    + Math.abs(matrix[0][1]) * extents.y()
                    + Math.abs(matrix[0][2]) * extents.z();
            double ey = Math.abs(matrix[1][0]) * extents.x()
                    + Math.abs(matrix[1][1]) * extents.y()
                    + Math.abs(matrix[1][2]) * extents.z();
            double ez = Math.abs(matrix[2][0]) * extents.x()
                    + Math.abs(matrix[2][1]) * extents.y()
                    + Math.abs(matrix[2][2]) * extents.z();
            return new Vector3D(ex, ey, ez);
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
    }

    private record ResolvedTransform(Vector3D location, Rotation rotation, Vector3D scale) {
        static final ResolvedTransform IDENTITY = new ResolvedTransform(Vector3D.ZERO, Rotation.ZERO, Vector3D.ONES);
    }

    private static final class TransformResolver {
        private final Map<ComponentKey, ComponentDefinition> components;
        private final Map<ComponentKey, ResolvedTransform> cache = new HashMap<>();

        TransformResolver(Map<ComponentKey, ComponentDefinition> components) {
            this.components = components;
        }

        ResolvedTransform resolve(ComponentKey key) {
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

    private record ObjectiveVolume(ObjectiveObject object, double radius) {
    }
}