package com.pipemasters.app;

import java.nio.file.Path;
import java.util.Locale;

public final class LayerExportArgumentsParser {
    private static final int MIN_ARGS = 1;
    private static final int MAX_ARGS = 3;

    public LayerExportRequest parse(String[] args) {
        if (args == null || args.length < MIN_ARGS || args.length > MAX_ARGS) {
            throw new LayerExportException("Usage: java -jar app.jar <path-to-gameplay-data-json> [path-to-gameplay-layer-json] [path-to-units-json]");
        }

        Path projectRoot = Path.of("").toAbsolutePath().normalize();

        Path gameplayDataPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path explicitLayerPath = null;
        Path unitsPath = projectRoot.resolve("output").resolve("units.json");

        for (int i = 1; i < args.length; i++) {
            Path candidate = Path.of(args[i]).toAbsolutePath().normalize();
            if (explicitLayerPath == null && isLikelyGameplayLayerFile(candidate)) {
                explicitLayerPath = candidate;
            } else {
                unitsPath = candidate;
            }
        }

        return new LayerExportRequest(projectRoot, gameplayDataPath, explicitLayerPath, unitsPath);
    }

    private boolean isLikelyGameplayLayerFile(Path path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toString().toLowerCase(Locale.ROOT);
        return normalized.contains("gameplay_layers");
    }
}
