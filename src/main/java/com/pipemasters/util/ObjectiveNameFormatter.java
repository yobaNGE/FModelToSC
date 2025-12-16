package com.pipemasters.util;

public final class ObjectiveNameFormatter {

    private ObjectiveNameFormatter() {
    }

    public static String formatObjectDisplayName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return objectName;
        }

        int dashIndex = objectName.indexOf('-');
        if (dashIndex < 0) {
            return addSpacesBeforeCaps(objectName);
        }

        String prefix = objectName.substring(0, dashIndex + 1);
        String suffix = objectName.substring(dashIndex + 1);

        return prefix + addSpacesBeforeCaps(suffix);
    }

    private static String addSpacesBeforeCaps(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        result.append(text.charAt(0));

        for (int i = 1; i < text.length(); i++) {
            char current = text.charAt(i);
            char previous = text.charAt(i - 1);

            if (Character.isUpperCase(current) &&
                Character.isLowerCase(previous)) {
                result.append(' ');
            }

            result.append(current);
        }

        return result.toString();
    }
}

