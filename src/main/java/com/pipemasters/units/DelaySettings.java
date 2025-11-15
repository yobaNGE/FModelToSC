package com.pipemasters.units;

public record DelaySettings(int initialDelayMinutes, int respawnTimeMinutes) {
    public static final DelaySettings NONE = new DelaySettings(0, 0);
}