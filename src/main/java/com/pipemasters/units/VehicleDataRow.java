package com.pipemasters.units;

record VehicleDataRow(String displayName, String description, String icon) {
    static final VehicleDataRow EMPTY = new VehicleDataRow("", "", "");
}