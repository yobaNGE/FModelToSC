package com.pipemasters.mapassets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "location_x",
        "location_y",
        "location_z",
        "team",
        "initialLifeSpan",
        "spawningEnabled",
        "displayName"
})
public record SpawnGroup(@JsonProperty("location_x") double locationX,
                         @JsonProperty("location_y") double locationY,
                         @JsonProperty("location_z") double locationZ,
                         String team,
                         int initialLifeSpan,
                         boolean spawningEnabled,
                         String displayName) {
}