package com.pipemasters.app;

import java.nio.file.Path;

public record LayerExportResult(Path outputPath, String layerVersion) {
}
