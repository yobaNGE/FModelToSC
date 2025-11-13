package com.pipemasters.mapassets;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "protectionZones",
        "spawnGroups",
        "spawnPoints"
})
public record MapAssets(List<ProtectionZone> protectionZones,
                        List<SpawnGroup> spawnGroups,
                        List<SpawnPoint> spawnPoints) {
}