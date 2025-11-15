package com.pipemasters.units;

record VehicleBlueprintInfo(String className,
                            int driverSeats,
                            int passengerSeats,
                            boolean amphibious,
                            boolean atgm) {
    static final VehicleBlueprintInfo EMPTY = new VehicleBlueprintInfo("", 0, 0, false, false);
}
