package com.pipemasters.layerdata;

import java.util.List;

public record TeamConfig(int index,
                         String defaultFactionUnit,
                         int tickets,
                         boolean vehiclesDisabled,
                         int playerPercent,
                         List<String> allowedAlliances,
                         List<String> allowedFactionUnitTypes,
                         List<String> requiredTags) {
}
