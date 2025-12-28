package com.pipemasters.vehicles;

import java.util.List;

public record VehicleExport(String type,
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
                            boolean ATGM,
                            List<VehicleWeapon> weapons) {
}
