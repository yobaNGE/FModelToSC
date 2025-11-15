package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

final class BlueprintUtils {
    private BlueprintUtils() {
    }

    static String extractReferenceName(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            return null;
        }
        String objectName = reference.path("ObjectName").asText(null);
        return extractInnerName(objectName);
    }

    static String extractInnerName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        int first = objectName.indexOf('\'');
        int last = objectName.lastIndexOf('\'');
        String inner;
        if (first >= 0 && last > first) {
            inner = objectName.substring(first + 1, last);
        } else {
            inner = objectName;
        }
        int colon = inner.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < inner.length()) {
            return inner.substring(colon + 1);
        }
        return inner;
    }

    static String prettifyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String sanitized = raw.replace('_', ' ').trim();
        if (sanitized.isEmpty()) {
            return "Unknown";
        }
        StringBuilder builder = new StringBuilder(sanitized.length());
        boolean capitalizeNext = true;
        for (char ch : sanitized.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                builder.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(ch));
                capitalizeNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    static String normalizeEnum(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        int idx = value.lastIndexOf("::");
        String raw = idx >= 0 ? value.substring(idx + 2) : value;
        raw = raw.replace('_', ' ').trim();
        if (raw.startsWith("NewEnumerator")) {
            raw = raw.substring("NewEnumerator".length()).trim();
        }
        if (raw.isBlank()) {
            return "Unknown";
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }
}