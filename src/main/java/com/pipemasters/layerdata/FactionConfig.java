package com.pipemasters.layerdata;

import java.util.List;

public record FactionConfig(String factionID, String defaultUnit, List<FactionType> types) {
}
