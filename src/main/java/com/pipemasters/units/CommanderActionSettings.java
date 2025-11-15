package com.pipemasters.units;

public record CommanderActionSettings(String displayName, String icon) {
    public static final CommanderActionSettings UNKNOWN = new CommanderActionSettings("Unknown", "questionmark");
}