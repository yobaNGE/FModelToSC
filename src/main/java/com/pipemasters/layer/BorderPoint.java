package com.pipemasters.layer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"point", "location_x", "location_y", "location_z"})
public record BorderPoint(int point,
                          @JsonProperty("location_x") double locationX,
                          @JsonProperty("location_y") double locationY,
                          @JsonProperty("location_z") double locationZ) {
}