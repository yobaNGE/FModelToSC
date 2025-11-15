package com.pipemasters.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import com.pipemasters.app.LayerExportException;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameplayDataParser {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)v\\d+(?:[._-]?\\d+)*");

    public GameplayDataInfo parse(JsonNode root) {
        Objects.requireNonNull(root, "root");
        if (!root.isArray()) {
            throw new LayerExportException("Gameplay data export is expected to be a JSON array of objects");
        }

        JsonNode layerNode = null;
        for (JsonNode node : root) {
            if ("BP_SQLayer_C".equals(node.path("Type").asText())) {
                layerNode = node;
                break;
            }
        }

        if (layerNode == null) {
            throw new LayerExportException("Gameplay data file does not contain BP_SQLayer_C entry");
        }

        String layerName = layerNode.path("Name").asText(null);
        JsonNode dataNode = layerNode.path("Properties").path("Data");
        String rowName = dataNode.path("RowName").asText(layerName);
        String layerVersion = deriveLayerVersion(rowName, layerName);
        JsonNode worldsNode = layerNode.path("Properties").path("Worlds");
        String worldAssetPath = null;
        if (worldsNode != null && worldsNode.isArray()) {
            for (JsonNode world : worldsNode) {
                String assetPath = world.path("AssetPathName").asText(null);
                if (assetPath != null && !assetPath.isBlank()) {
                    worldAssetPath = assetPath;
                    break;
                }
            }
        }

        return new GameplayDataInfo(layerName, rowName, layerVersion, worldAssetPath);
    }

    private String deriveLayerVersion(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return "";
        }
        for (String candidate : candidates) {
            String version = findVersion(candidate);
            if (version != null && !version.isBlank()) {
                return version;
            }
        }
        return "";
    }

    private String findVersion(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(candidate);
        String result = null;
        while (matcher.find()) {
            result = matcher.group();
        }
        if (result != null && !result.isBlank()) {
            return normalizeVersion(result);
        }
        String[] segments = candidate.split("_");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            if (segment == null || segment.isBlank()) {
                continue;
            }
            Matcher segmentMatcher = VERSION_PATTERN.matcher(segment);
            if (segmentMatcher.matches()) {
                return normalizeVersion(segmentMatcher.group());
            }
        }
        return null;
    }

    private String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() == 1) {
            return String.valueOf(Character.toLowerCase(trimmed.charAt(0)) == 'v' ? 'v' : trimmed.charAt(0));
        }
        char first = trimmed.charAt(0);
        if (first == 'v' || first == 'V') {
            return "v" + trimmed.substring(1);
        }
        return trimmed;
    }
}
