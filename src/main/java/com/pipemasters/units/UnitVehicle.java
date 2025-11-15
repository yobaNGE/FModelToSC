package com.pipemasters.units;

import java.util.List;

public record UnitVehicle(String type,
                          String rawType,
                          String icon,
                          int count,
                          int delay,
                          int respawnTime,
                          boolean singleUse,
                          String vehType,
                          String spawnerSize,
                          int passengerSeats,
                          int driverSeats,
                          List<String> vehTags,
                          boolean isAmphibious,
                          int ticketValue,
                          boolean ATGM) {
    public static final UnitVehicle UNKNOWN = new UnitVehicle(
            "Unknown",
            "",
            "questionmark",
            0,
            0,
            0,
            false,
            "Unknown",
            "Unknown",
            0,
            0,
            List.of(),
            false,
            0,
            false
    );
}