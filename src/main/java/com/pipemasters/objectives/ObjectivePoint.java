package com.pipemasters.objectives;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"name", "objectName", "objectDisplayName", "location_x", "location_y", "location_z", "objects"})
public record ObjectivePoint(String name,
                             String objectName,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String objectDisplayName,
                             @JsonProperty("location_x") double locationX,
                             @JsonProperty("location_y") double locationY,
                             @JsonProperty("location_z") double locationZ,
                             List<ObjectiveObject> objects) {
}