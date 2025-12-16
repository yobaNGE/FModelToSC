package com.pipemasters.layerdata;

public enum GameMode {
    INVASION,
    RAAS,
    AAS,
    UNKNOWN;

    public static GameMode fromRowName(String rowName) {
        if (rowName == null || rowName.isBlank()) {
            return UNKNOWN;
        }
        return switch (rowName.trim().toLowerCase()) {
            case "invasion" -> INVASION;
            case "raas" -> RAAS;
            case "aas" -> AAS;
            default -> UNKNOWN;
        };
    }

    public boolean hasMirroredTeams() {
        return this == RAAS || this == AAS;
    }
}