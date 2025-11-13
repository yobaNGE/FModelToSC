package com.pipemasters.assets;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "vehicleSpawners",
        "helipads",
        "deployables"
})
public record Assets(List<VehicleSpawner> vehicleSpawners,
                     List<Helipad> helipads,
                     List<Deployable> deployables) {
}