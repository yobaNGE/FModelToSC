package com.pipemasters.objectives;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"location_x", "location_y", "location_z"})
public record ObjectiveLocation(@JsonProperty("location_x") double locationX,
                                @JsonProperty("location_y") double locationY,
                                @JsonProperty("location_z") double locationZ) {
}