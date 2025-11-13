package com.pipemasters.util;

public final class MainNameFormatter {
    private MainNameFormatter() {
    }

    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }

        String trimmed = rawName.strip();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        // Remove trailing "_#" suffixes that appear on some main actors.
        String withoutSuffix = trimmed.replaceFirst("_\\d+$", "");

        int index = withoutSuffix.indexOf("Main");
        if (index < 0) {
            return withoutSuffix;
        }

        if (index > 0 && withoutSuffix.charAt(index - 1) != ' ') {
            return withoutSuffix.substring(0, index) + " " + withoutSuffix.substring(index);
        }

        return withoutSuffix;
    }
}