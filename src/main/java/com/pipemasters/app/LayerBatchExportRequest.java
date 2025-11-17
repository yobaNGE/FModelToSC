package com.pipemasters.app;

import java.nio.file.Path;

public record LayerBatchExportRequest(Path projectRoot, Path layerListPath, Path unitsPath) {
}