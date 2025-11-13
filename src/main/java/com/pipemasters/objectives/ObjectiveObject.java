package com.pipemasters.objectives;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "objectName",
        "location_x",
        "location_y",
        "location_z",
        "isSphere",
        "sphereRadius",
        "isBox",
        "boxExtent",
        "isCapsule",
        "capsuleRadius",
        "capsuleLength",
        "rotation_x",
        "rotation_y",
        "rotation_z"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObjectiveObject(String objectName,
                              @JsonProperty("location_x") double locationX,
                              @JsonProperty("location_y") double locationY,
                              @JsonProperty("location_z") double locationZ,
                              boolean isSphere,
                              String sphereRadius,
                              boolean isBox,
                              ObjectiveBoxExtent boxExtent,
                              boolean isCapsule,
                              @JsonProperty("capsuleRadius") String capsuleRadius,
                              @JsonProperty("capsuleLength") String capsuleLength,
                              @JsonProperty("rotation_x") Double rotationX,
                              @JsonProperty("rotation_y") Double rotationY,
                              @JsonProperty("rotation_z") Double rotationZ) {
}