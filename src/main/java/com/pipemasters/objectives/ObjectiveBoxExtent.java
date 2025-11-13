package com.pipemasters.objectives;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "extent_x",
        "extent_y",
        "extent_z",
        "rotation_x",
        "rotation_y",
        "rotation_z",
        "scaling_x",
        "scaling_y",
        "scaling_z"
})
public record ObjectiveBoxExtent(@JsonProperty("extent_x") double extentX,
                                 @JsonProperty("extent_y") double extentY,
                                 @JsonProperty("extent_z") double extentZ,
                                 @JsonProperty("rotation_x") double rotationX,
                                 @JsonProperty("rotation_y") double rotationY,
                                 @JsonProperty("rotation_z") double rotationZ,
                                 @JsonProperty("scaling_x") double scalingX,
                                 @JsonProperty("scaling_y") double scalingY,
                                 @JsonProperty("scaling_z") double scalingZ) {
}