package com.pipemasters.mapassets;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "displayName",
        "deployableLockDistance",
        "teamid",
        "objects"
})
public record ProtectionZone(String displayName,
                             double deployableLockDistance,
                             String teamid,
                             List<MapAssetObject> objects) {
}