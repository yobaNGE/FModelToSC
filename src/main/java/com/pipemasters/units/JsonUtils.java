package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JsonUtils {
    private JsonUtils() {
    }

    static JsonNode findFirstProperty(JsonNode node, String fragment) {
        if (node == null || node.isMissingNode()) {
            return MissingNode.getInstance();
        }
        String lower = fragment.toLowerCase(Locale.ROOT);
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(lower)) {
                return entry.getValue();
            }
        }
        return MissingNode.getInstance();
    }

    static String readString(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.has("Value")) {
            return node.path("Value").asText("");
        }
        if (node.has("Name")) {
            return node.path("Name").asText("");
        }
        return node.asText("");
    }

    static int readInt(JsonNode node) {
        Integer value = readIntRecursive(node, 0);
        return value != null ? value : 0;
    }

    private static Integer readIntRecursive(JsonNode node, int depth) {
        if (node == null || node.isMissingNode() || depth > 20) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (node.has("Value")) {
            Integer nested = readIntRecursive(node.get("Value"), depth + 1);
            if (nested != null) {
                return nested;
            }
        }
        if (node.isArray()) {
            Integer candidate = null;
            for (JsonNode element : node) {
                Integer nested = readIntRecursive(element, depth + 1);
                if (nested != null) {
                    if (nested != 0) {
                        return nested;
                    }
                    if (candidate == null) {
                        candidate = nested;
                    }
                }
            }
            if (candidate != null) {
                return candidate;
            }
            return node.size();
        }
        if (node.isObject()) {
            Integer candidate = null;
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Integer nested = readIntRecursive(entry.getValue(), depth + 1);
                if (nested != null) {
                    if (nested != 0) {
                        return nested;
                    }
                    if (candidate == null) {
                        candidate = nested;
                    }
                }
            }
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    static boolean readBoolean(JsonNode node) {
        Boolean value = readBooleanRecursive(node, 0);
        return value != null ? value : false;
    }

    private static Boolean readBooleanRecursive(JsonNode node, int depth) {
        if (node == null || node.isMissingNode() || depth > 20) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asInt(0) != 0;
        }
        if (node.isTextual()) {
            String text = node.asText("").trim();
            if (text.isEmpty()) {
                return null;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.equals("true") || lower.equals("1") || lower.equals("yes") || lower.equals("on")) {
                return true;
            }
            if (lower.equals("false") || lower.equals("0") || lower.equals("no") || lower.equals("off")) {
                return false;
            }
            if (lower.contains("newenumerator1") || lower.endsWith("::true")) {
                return true;
            }
            if (lower.contains("newenumerator0") || lower.endsWith("::false")) {
                return false;
            }
            return null;
        }
        if (node.has("Value")) {
            Boolean nested = readBooleanRecursive(node.get("Value"), depth + 1);
            if (nested != null) {
                return nested;
            }
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                Boolean nested = readBooleanRecursive(element, depth + 1);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Boolean nested = readBooleanRecursive(entry.getValue(), depth + 1);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    static List<String> readStringArray(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            if (element.isTextual()) {
                values.add(element.asText());
            } else if (element.has("Name")) {
                values.add(element.path("Name").asText(""));
            } else if (element.has("Value")) {
                values.add(element.path("Value").asText(""));
            } else {
                values.add(element.asText(""));
            }
        }
        return values;
    }
}