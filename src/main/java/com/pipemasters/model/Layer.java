package com.pipemasters.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.pipemasters.assets.Assets;
import com.pipemasters.capture.CapturePoints;
import com.pipemasters.layer.BorderPoint;
import com.pipemasters.layer.LayerMetadata;
import com.pipemasters.layer.MapCameraActor;
import com.pipemasters.layer.MapTextureCorner;
import com.pipemasters.mapassets.MapAssets;
import com.pipemasters.objectives.Objective;
import com.pipemasters.units.Units;

import java.util.List;
import java.util.Map;

@JsonPropertyOrder({
        "rawName",
        "mapId",
        "mapName",
        "gamemode",
        "layerVersion",
        "seaLevel",
        "mapCameraActor",
        "border",
        "mapTextureCorners",
        "assets",
        "capturePoints",
        "objectives",
        "mapAssets",
        "teamConfigs",
        "units"
})
public record Layer(String rawName,
                    String mapId,
                    String mapName,
                    String gamemode,
                    String layerVersion,
                    double seaLevel,
                    MapCameraActor mapCameraActor,
                    List<BorderPoint> border,
                    List<MapTextureCorner> mapTextureCorners,
                    Assets assets,
                    CapturePoints capturePoints,
                    Map<String, Objective> objectives,
                    MapAssets mapAssets,
                    @JsonInclude(JsonInclude.Include.NON_NULL)
                    LayerTeamConfiguration teamConfigs,
                    @JsonInclude(JsonInclude.Include.NON_NULL)
                    Units units) {
    public Layer(LayerMetadata metadata,
                 CapturePoints capturePoints,
                 Map<String, Objective> objectives,
                 MapAssets mapAssets,
                 Assets assets) {
        this(metadata, capturePoints, objectives, mapAssets, assets, null, null);
    }

    public Layer(LayerMetadata metadata,
                 CapturePoints capturePoints,
                 Map<String, Objective> objectives,
                 MapAssets mapAssets,
                 Assets assets,
                 LayerTeamConfiguration teamConfigs,
                 Units units) {
        this(metadata.rawName(),
                metadata.mapId(),
                metadata.mapName(),
                metadata.gamemode(),
                metadata.layerVersion(),
                metadata.seaLevel(),
                metadata.mapCameraActor(),
                metadata.border(),
                metadata.mapTextureCorners(),
                assets,
                capturePoints,
                objectives,
                mapAssets,
                teamConfigs,
                units);
    }
}
