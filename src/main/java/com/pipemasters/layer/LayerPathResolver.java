package com.pipemasters.layer;

import com.pipemasters.app.LayerExportException;
import com.pipemasters.gameplay.GameplayDataInfo;
import com.pipemasters.util.AssetResolver;
import com.pipemasters.util.MissingAssetLogger;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LayerPathResolver {
    public Path resolveExportsRoot(Path gameplayDataPath) {
        if (gameplayDataPath == null) {
            return null;
        }
        Path current = gameplayDataPath.toAbsolutePath().normalize().getParent();
        while (current != null) {
            if (Files.isDirectory(current.resolve("Content"))
                    || Files.isDirectory(current.resolve("Maps"))
                    || Files.isDirectory(current.resolve("Settings"))) {
                return current;
            }
            current = current.getParent();
        }
        return gameplayDataPath.getParent();
    }

    public Path resolveLayerJson(Path explicitLayerPath,
                                 GameplayDataInfo gameplayDataInfo,
                                 Path exportsRoot,
                                 MissingAssetLogger missingLayerLogger,
                                 Path gameplayDataPath) {
        Path layerJsonPath = explicitLayerPath;
        String layerAssetPath = gameplayDataInfo.worldAssetPath();
        if (layerJsonPath == null && layerAssetPath != null && !layerAssetPath.isBlank()) {
            AssetResolver resolver = new AssetResolver(exportsRoot);
            layerJsonPath = resolver.resolve(layerAssetPath);
        }

        if (layerJsonPath == null) {
            if (layerAssetPath != null && !layerAssetPath.isBlank()) {
                missingLayerLogger.missing(layerAssetPath, "gameplay layer for " + gameplayDataInfo.layerName());
            } else {
                missingLayerLogger.missing(gameplayDataPath, "gameplay layer reference");
            }
            throw new LayerExportException(String.format("Unable to resolve gameplay layer file for '%s'.", gameplayDataPath));
        }

        if (!Files.exists(layerJsonPath)) {
            if (layerAssetPath != null && !layerAssetPath.isBlank()) {
                missingLayerLogger.missing(layerAssetPath, "gameplay layer for " + gameplayDataInfo.layerName());
            } else {
                missingLayerLogger.missing(layerJsonPath, "gameplay layer for " + gameplayDataInfo.layerName());
            }
            throw new LayerExportException(String.format("Gameplay layer file '%s' does not exist.", layerJsonPath));
        }

        return layerJsonPath;
    }
}
