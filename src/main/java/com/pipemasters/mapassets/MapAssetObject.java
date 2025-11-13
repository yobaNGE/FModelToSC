package com.pipemasters.mapassets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "objectName",
        "isSphere",
        "sphereRadius",
        "location_x",
        "location_y",
        "location_z",
        "isBox",
        "boxExtent",
        "isCapsule"
})
public record MapAssetObject(String objectName,
                             boolean isSphere,
                             double sphereRadius,
                             @JsonProperty("location_x") double locationX,
                             @JsonProperty("location_y") double locationY,
                             @JsonProperty("location_z") double locationZ,
                             boolean isBox,
                             MapAssetObjectExtent boxExtent,
                             boolean isCapsule) {
}