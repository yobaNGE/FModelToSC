package com.pipemasters.objectives;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"name", "objectName", "location_x", "location_y", "location_z", "objects"})
public record ObjectivePoint(String name,
                             String objectName,
                             @JsonProperty("location_x") double locationX,
                             @JsonProperty("location_y") double locationY,
                             @JsonProperty("location_z") double locationZ,
                             List<ObjectiveObject> objects) {
}