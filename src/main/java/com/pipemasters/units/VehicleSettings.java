package com.pipemasters.units;

import java.util.List;

public record VehicleSettings(String displayName,
                              String rawType,
                              String icon,
                              String vehicleType,
                              String spawnerSize,
                              int passengerSeats,
                              int driverSeats,
                              List<String> tags,
                              boolean amphibious,
                              int ticketValue,
                              boolean atgm) {
    public static final VehicleSettings UNKNOWN = new VehicleSettings(
            "Unknown",
            "",
            "questionmark",
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