package com.pipemasters.mapassets;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "extent_x",
        "extent_y",
        "extent_z",
        "rotation_x",
        "rotation_y",
        "rotation_z"
})
public record MapAssetObjectExtent(double extent_x,
                                   double extent_y,
                                   double extent_z,
                                   double rotation_x,
                                   double rotation_y,
                                   double rotation_z) {
}