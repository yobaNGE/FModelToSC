package com.pipemasters.units;

import java.util.List;

public record Unit(String unitObjectName,
                   String unitIcon,
                   String factionID,
                   String shortName,
                   String factionName,
                   String displayName,
                   String description,
                   String unitBadge,
                   String type,
                   boolean useCommanderActionNearVehicle,
                   boolean hasBuddyRally,
                   List<UnitVehicle> vehicles,
                   List<UnitCommanderAsset> commanderAssets) {
}