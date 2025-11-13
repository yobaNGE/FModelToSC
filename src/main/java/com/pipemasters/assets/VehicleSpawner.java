package com.pipemasters.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "icon",
        "name",
        "type",
        "size",
        "maxNum",
        "location_x",
        "location_y",
        "location_z",
        "rotation_x",
        "rotation_y",
        "rotation_z",
        "typePriorities",
        "tagPriorities"
})
public record VehicleSpawner(String icon,
                             String name,
                             String type,
                             String size,
                             int maxNum,
                             @JsonProperty("location_x") double locationX,
                             @JsonProperty("location_y") double locationY,
                             @JsonProperty("location_z") double locationZ,
                             @JsonProperty("rotation_x") double rotationX,
                             @JsonProperty("rotation_y") double rotationY,
                             @JsonProperty("rotation_z") double rotationZ,
                             List<AssetPriority> typePriorities,
                             List<AssetPriority> tagPriorities) {
}