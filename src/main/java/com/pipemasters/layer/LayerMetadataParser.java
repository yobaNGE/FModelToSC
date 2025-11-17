package com.pipemasters.layer;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LayerMetadataParser {
    private static final Pattern DISPLAY_TITLE_PATTERN = Pattern.compile("^(.+?)\\s+(\\S+)\\s+(v\\d+(?:\\.\\d+)?)$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> CORNER_NAME_TO_INDEX = Map.of(
            "Zero", 0,
            "One", 1,
            "Two", 2,
            "Three", 3
    );

    public LayerMetadata parse(Path exportPath, JsonNode root) {
        Objects.requireNonNull(exportPath, "exportPath");
        Objects.requireNonNull(root, "root");
        if (!root.isArray()) {
            throw new IllegalArgumentException("FModel export is expected to be a JSON array of objects");
        }

        String rawName = findRawName(root)
                .orElseGet(() -> extractFileStem(exportPath)
                        .orElseThrow(() -> new IllegalStateException("Unable to derive layer raw name")));

        JsonNode worldSettings = findFirstByType(root, "SQWorldSettings").orElse(null);

        JsonNode layerDefinition = findFirstByType(root, "BP_SQLayer_C").orElse(null);

        ParsedLayerInfo layerInfo = parseLayerInfo(rawName, worldSettings, layerDefinition);
        String mapId = extractLevelId(layerDefinition)
                .or(() -> extractMapId(worldSettings))
                .orElse(layerInfo.mapName().replace(" ", ""));

        MapCameraActor mapCameraActor = parseMapCameraActor(root, worldSettings);
        List<MapTextureCorner> mapTextureCorners = parseMapTextureCorners(root, worldSettings);
        List<BorderPoint> border = parseBorder(root);
        if (border.isEmpty() && !mapTextureCorners.isEmpty()) {
            border = mapTextureCorners.stream()
                    .map(corner -> new BorderPoint(
                            corner.point(),
                            corner.locationX(),
                            corner.locationY(),
                            corner.locationZ()))
                    .toList();
        }
        double seaLevel = parseSeaLevel(root).orElse(0.0);

        return new LayerMetadata(
                rawName,
                mapId,
                layerInfo.mapName(),
                layerInfo.gamemode(),
                layerInfo.layerVersion(),
                seaLevel,
                mapCameraActor,
                border,
                mapTextureCorners
        );
    }

    private Optional<String> findRawName(JsonNode root) {
        for (JsonNode node : root) {
            if ("Level".equals(node.path("Type").asText())
                    && "PersistentLevel".equals(node.path("Name").asText())) {
                String outer = node.path("Outer").asText(null);
                if (outer != null && !outer.isBlank()) {
                    return Optional.of(outer);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFileStem(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString() : null;
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return Optional.of(fileName.substring(0, dot));
        }
        return Optional.of(fileName);
    }

    private ParsedLayerInfo parseLayerInfo(String rawName, JsonNode worldSettings, JsonNode layerDefinition) {
        String mapName = null;
        String gamemode = null;
        String layerVersion = null;

        String displayTitle = extractDisplayTitle(worldSettings);
        if (displayTitle != null) {
            Matcher matcher = DISPLAY_TITLE_PATTERN.matcher(displayTitle.trim());
            if (matcher.matches()) {
                mapName = matcher.group(1).trim();
                gamemode = matcher.group(2).trim();
                layerVersion = matcher.group(3).trim();
            }
        }

        if (mapName == null) {
            String levelMapName = extractMapNameFromLevelId(layerDefinition);
            if (levelMapName != null && !levelMapName.isBlank()) {
                mapName = prettifyName(levelMapName);
            }
        }
        if (gamemode == null) {
            String gameModeRow = extractGameMode(layerDefinition);
            if (gameModeRow != null && !gameModeRow.isBlank()) {
                gamemode = prettifyName(gameModeRow);
            }
        }

        String[] rawParts = rawName.split("_");
        int prefixOffset = rawParts.length > 0 && isKnownLayerPrefix(rawParts[0]) ? 1 : 0;
        if (mapName == null && rawParts.length > prefixOffset) {
            mapName = prettifyName(rawParts[prefixOffset]);
        }
        if (gamemode == null && rawParts.length > prefixOffset + 1) {
            gamemode = prettifyName(rawParts[prefixOffset + 1]);
        }
        if (layerVersion == null) {
            layerVersion = inferLayerVersion(rawParts);
        }

        if (mapName == null) {
            mapName = rawName;
        }
        if (gamemode == null) {
            gamemode = "";
        }
        if (layerVersion == null) {
            layerVersion = "";
        }

        return new ParsedLayerInfo(mapName, gamemode, layerVersion);
    }

    private String prettifyName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replace('_', ' ').replace('-', ' ').trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private String extractMapNameFromLevelId(JsonNode layerDefinition) {
        if (layerDefinition == null) {
            return null;
        }
        String levelId = layerDefinition.path("Properties").path("LevelId").asText(null);
        return extractMapNameFromLevelId(levelId);
    }

    private String extractMapNameFromLevelId(String levelId) {
        if (levelId == null || levelId.isBlank()) {
            return null;
        }
        int underscore = levelId.indexOf('_');
        if (underscore >= 0 && underscore + 1 < levelId.length()) {
            return levelId.substring(underscore + 1);
        }
        return levelId;
    }

    private String extractGameMode(JsonNode layerDefinition) {
        if (layerDefinition == null) {
            return null;
        }
        String value = layerDefinition.path("Properties").path("GameMode").path("RowName").asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private boolean isKnownLayerPrefix(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SD", "SDL" -> true;
            default -> false;
        };
    }

    private String inferLayerVersion(String[] rawParts) {
        if (rawParts == null || rawParts.length == 0) {
            return null;
        }
        if (rawParts.length >= 2) {
            String last = rawParts[rawParts.length - 1];
            String previous = rawParts[rawParts.length - 2];
            if (last.length() == 1 && previous.startsWith("v")) {
                return previous + "_" + last;
            }
        }
        return rawParts[rawParts.length - 1];
    }

    private String formatMapId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replace('_', ' ').trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        trimmed = trimmed.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        String[] parts = trimmed.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(capitalize(part));
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() == 1) {
            return value.toUpperCase(Locale.ROOT);
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String extractDisplayTitle(JsonNode worldSettings) {
        if (worldSettings == null) {
            return null;
        }
        JsonNode displayTitle = worldSettings.path("Properties").path("DisplayTitle");
        String localized = displayTitle.path("LocalizedString").asText(null);
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        String source = displayTitle.path("SourceString").asText(null);
        if (source != null && !source.isBlank()) {
            return source;
        }
        return null;
    }

    private Optional<String> extractMapId(JsonNode worldSettings) {
        if (worldSettings == null) {
            return Optional.empty();
        }
        String objectName = worldSettings.path("Properties")
                .path("MapTexture")
                .path("ObjectName")
                .asText(null);
        if (objectName == null || objectName.isBlank()) {
            return Optional.empty();
        }
        String innerName = extractInnerName(objectName);
        if (innerName == null) {
            return Optional.empty();
        }
        int firstUnderscore = innerName.indexOf('_');
        int lastUnderscore = innerName.lastIndexOf('_');
        String candidate;
        if (firstUnderscore >= 0 && lastUnderscore > firstUnderscore) {
            candidate = innerName.substring(firstUnderscore + 1, lastUnderscore);
        } else if (firstUnderscore >= 0) {
            candidate = innerName.substring(firstUnderscore + 1);
        } else {
            candidate = innerName;
        }
        String formatted = formatMapId(candidate);
        if (formatted == null) {
            return Optional.empty();
        }
        return Optional.of(formatted);
    }

    private Optional<String> extractLevelId(JsonNode layerDefinition) {
        if (layerDefinition == null) {
            return Optional.empty();
        }
        String levelId = layerDefinition.path("Properties").path("LevelId").asText(null);
        if (levelId == null || levelId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(levelId.trim());
    }

    private MapCameraActor parseMapCameraActor(JsonNode root, JsonNode worldSettings) {
        String actorName = extractActorName(worldSettings, "MapCameraLocation");
        if (actorName == null) {
            throw new IllegalStateException("Unable to determine map camera actor");
        }
        JsonNode sceneComponent = findSceneComponent(root, actorName, "SceneComponent");
        Vector3 location = readVector(sceneComponent.path("Properties").path("RelativeLocation"));
        JsonNode rotationNode = sceneComponent.path("Properties").path("RelativeRotation");
        double rotationX = rotationNode.path("Pitch").asDouble(0.0);
        double rotationY = rotationNode.path("Roll").asDouble(0.0);
        double rotationZ = rotationNode.path("Yaw").asDouble(0.0);
        return new MapCameraActor(
                actorName,
                location.x(),
                location.y(),
                location.z(),
                rotationX,
                rotationY,
                rotationZ
        );
    }

    private List<MapTextureCorner> parseMapTextureCorners(JsonNode root, JsonNode worldSettings) {
        if (worldSettings == null) {
            return List.of();
        }
        JsonNode properties = worldSettings.path("Properties");
        Iterator<String> fieldNames = properties.fieldNames();
        if (fieldNames == null) {
            return List.of();
        }
        Map<Integer, MapTextureCorner> corners = new TreeMap<>();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!fieldName.startsWith("MapTextureCorner")) {
                continue;
            }
            String actorName = extractActorName(properties.path(fieldName));
            if (actorName == null) {
                continue;
            }
            int index = resolveCornerIndex(fieldName, actorName);
            String rootComponentName = findActor(root, actorName)
                    .map(actor -> extractActorName(actor.path("Properties").path("RootComponent")))
                    .filter(name -> name != null && !name.isBlank())
                    .orElse("DefaultSceneRoot");
            JsonNode sceneComponent = findSceneComponent(root, actorName, rootComponentName);
            Vector3 location = readVector(sceneComponent.path("Properties").path("RelativeLocation"));
            corners.put(index, new MapTextureCorner(index, location.x(), location.y(), location.z()));
        }
        if (corners.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(corners.values());
    }

    private int resolveCornerIndex(String propertyName, String actorName) {
        Optional<Integer> actorIndex = parseTrailingInteger(actorName);
        if (actorIndex.isPresent()) {
            return actorIndex.get();
        }
        for (Map.Entry<String, Integer> entry : CORNER_NAME_TO_INDEX.entrySet()) {
            if (propertyName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("Unable to resolve texture corner index for " + propertyName);
    }

    private Optional<Integer> parseTrailingInteger(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        int end = value.length();
        int start = end;
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value.substring(start, end)));
    }

    private List<BorderPoint> parseBorder(JsonNode root) {
        JsonNode splineComponent = findBoundarySplineComponent(root);
        if (splineComponent == null) {
            return Collections.emptyList();
        }
        Vector3 relativeLocation = readVector(splineComponent.path("Properties").path("RelativeLocation"));
        JsonNode pointsNode = splineComponent.path("Properties").path("SplineCurves").path("Position").path("Points");
        if (!pointsNode.isArray()) {
            return Collections.emptyList();
        }
        List<BorderPoint> border = new ArrayList<>();
        int index = 0;
        for (JsonNode pointNode : pointsNode) {
            JsonNode outVal = pointNode.path("OutVal");
            Vector3 point = readVector(outVal);
            Vector3 world = relativeLocation.add(point);
            border.add(new BorderPoint(index, world.x(), world.y(), world.z()));
            index++;
        }
        return border;
    }

    private JsonNode findBoundarySplineComponent(JsonNode root) {
        for (JsonNode node : root) {
            if (!"SQMapBoundary".equals(node.path("Type").asText())) {
                continue;
            }
            String boundaryName = node.path("Name").asText(null);
            if (boundaryName == null) {
                continue;
            }
            JsonNode spline = findSplineComponent(root, boundaryName);
            if (spline != null) {
                return spline;
            }
        }
        return null;
    }

    private JsonNode findSplineComponent(JsonNode root, String outer) {
        for (JsonNode node : root) {
            if ("SplineComponent".equals(node.path("Type").asText())
                    && outer.equals(node.path("Outer").asText())) {
                return node;
            }
        }
        return null;
    }

    private Optional<Double> parseSeaLevel(JsonNode root) {
        for (JsonNode node : root) {
            JsonNode properties = node.path("Properties");
            if (properties.has("SeaLevel")) {
                return Optional.of(properties.path("SeaLevel").asDouble());
            }
        }
        return Optional.empty();
    }

    private JsonNode findSceneComponent(JsonNode root, String outer, String name) {
        JsonNode fallback = null;
        for (JsonNode node : root) {
            if (!outer.equals(node.path("Outer").asText())) {
                continue;
            }
            String type = node.path("Type").asText();
            if (!isComponentType(type)) {
                continue;
            }
            String nodeName = node.path("Name").asText();
            if (name != null && name.equals(nodeName)) {
                return node;
            }
            if (fallback == null) {
                fallback = node;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("Unable to locate component '" + name + "' for '" + outer + "'");
    }

    private boolean isComponentType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        return "SceneComponent".equals(type) || type.endsWith("Component");
    }

    private Optional<JsonNode> findActor(JsonNode root, String name) {
        for (JsonNode node : root) {
            if (name.equals(node.path("Name").asText(null))
                    && !"SceneComponent".equals(node.path("Type").asText())
                    && node.path("Properties").has("RootComponent")) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private String extractActorName(JsonNode ownerNode) {
        if (ownerNode == null || ownerNode.isMissingNode()) {
            return null;
        }
        String objectName = ownerNode.path("ObjectName").asText(null);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String innerName = extractInnerName(objectName);
        if (innerName == null) {
            return null;
        }
        int lastDot = innerName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < innerName.length()) {
            return innerName.substring(lastDot + 1);
        }
        return innerName;
    }

    private String extractActorName(JsonNode worldSettings, String propertyName) {
        if (worldSettings == null) {
            return null;
        }
        JsonNode reference = worldSettings.path("Properties").path(propertyName);
        return extractActorName(reference);
    }

    private Optional<JsonNode> findFirstByType(JsonNode root, String type) {
        for (JsonNode node : root) {
            if (type.equals(node.path("Type").asText())) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private String extractInnerName(String objectName) {
        int first = objectName.indexOf('\'');
        int last = objectName.lastIndexOf('\'');
        if (first >= 0 && last > first) {
            return objectName.substring(first + 1, last);
        }
        return objectName;
    }

    private Vector3 readVector(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return Vector3.ZERO;
        }
        double x = node.path("X").asDouble(0.0);
        double y = node.path("Y").asDouble(0.0);
        double z = node.path("Z").asDouble(0.0);
        return new Vector3(x, y, z);
    }

    private record ParsedLayerInfo(String mapName, String gamemode, String layerVersion) {
    }

    private record Vector3(double x, double y, double z) {
        private static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);

        private Vector3 add(Vector3 other) {
            return new Vector3(x + other.x, y + other.y, z + other.z);
        }
    }
}