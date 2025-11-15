package com.pipemasters.app;

import java.nio.file.Path;

public record LayerExportRequest(Path projectRoot,
                                 Path gameplayDataPath,
                                 Path explicitLayerPath,
                                 Path unitsPath) {
}
