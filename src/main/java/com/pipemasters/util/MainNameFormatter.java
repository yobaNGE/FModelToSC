package com.pipemasters.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainNameFormatter {
    private static final String ATTACK_MAIN = "00-Team1 Main";
    private static final String DEFENSE_MAIN = "Z-Team2 Main";


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
    public static Map<String, String> canonicalize(List<String> mainsInOrder) {
        if (mainsInOrder == null || mainsInOrder.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> overrides = new LinkedHashMap<>();
        String first = mainsInOrder.getFirst();
        if (first != null && !first.isBlank()) {
            overrides.put(first, ATTACK_MAIN);
        }

        if (mainsInOrder.size() > 1) {
            String last = mainsInOrder.getLast();
            if (last != null && !last.isBlank()) {
                overrides.put(last, DEFENSE_MAIN);
            }
        }

        return overrides.isEmpty() ? Collections.emptyMap() : Map.copyOf(overrides);
    }
}