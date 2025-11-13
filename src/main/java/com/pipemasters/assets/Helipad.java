package com.pipemasters.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "type",
        "icon",
        "team",
        "location_x",
        "location_y",
        "location_z",
        "rotation_x",
        "rotation_y",
        "rotation_z"
})
public record Helipad(String type,
                      String icon,
                      String team,
                      @JsonProperty("location_x") double locationX,
                      @JsonProperty("location_y") double locationY,
                      @JsonProperty("location_z") double locationZ,
                      @JsonProperty("rotation_x") double rotationX,
                      @JsonProperty("rotation_y") double rotationY,
                      @JsonProperty("rotation_z") double rotationZ) {
}