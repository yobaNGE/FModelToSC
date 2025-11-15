package com.pipemasters.layer;

import java.util.List;

public record LayerMetadata(String rawName,
                            String mapId,
                            String mapName,
                            String gamemode,
                            String layerVersion,
                            double seaLevel,
                            MapCameraActor mapCameraActor,
                            List<BorderPoint> border,
                            List<MapTextureCorner> mapTextureCorners) {

    public LayerMetadata withLayerVersion(String newLayerVersion) {
        return new LayerMetadata(rawName,
                mapId,
                mapName,
                gamemode,
                newLayerVersion,
                seaLevel,
                mapCameraActor,
                border,
                mapTextureCorners);
    }
}
