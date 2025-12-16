package com.pipemasters.objectives;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"name", "objectName", "objectDisplayName", "location_x", "location_y", "location_z", "objects", "pointPosition"})
public record ObjectiveSimple(String name,
                              String objectName,
                              String objectDisplayName,
                              @JsonProperty("location_x") double locationX,
                              @JsonProperty("location_y") double locationY,
                              @JsonProperty("location_z") double locationZ,
                              List<ObjectiveObject> objects,
                              Integer pointPosition) implements Objective {
}

