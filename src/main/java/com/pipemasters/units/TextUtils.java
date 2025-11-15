package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;

final class TextUtils {
    private TextUtils() {
    }

    static String readText(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        if (node.hasNonNull("CultureInvariantString")) {
            return node.path("CultureInvariantString").asText("");
        }
        if (node.hasNonNull("LocalizedString")) {
            String value = node.path("LocalizedString").asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        if (node.hasNonNull("SourceString")) {
            return node.path("SourceString").asText("");
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        return "";
    }

    static String readAssetName(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        String assetPath = node.path("AssetPathName").asText(null);
        if (assetPath != null && !assetPath.isBlank()) {
            int slash = assetPath.lastIndexOf('/') + 1;
            int dot = assetPath.lastIndexOf('.');
            if (dot > slash) {
                return assetPath.substring(slash, dot);
            }
            if (slash > 0 && slash < assetPath.length()) {
                return assetPath.substring(slash);
            }
            return assetPath;
        }
        String objectPath = node.path("ObjectPath").asText(null);
        if (objectPath != null && !objectPath.isBlank()) {
            int slash = objectPath.lastIndexOf('/') + 1;
            int dot = objectPath.lastIndexOf('.');
            if (dot > slash) {
                return objectPath.substring(slash, dot);
            }
            if (slash > 0 && slash < objectPath.length()) {
                return objectPath.substring(slash);
            }
            return objectPath;
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        return "";
    }
}