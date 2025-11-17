package com.pipemasters.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipemasters.assets.Assets;
import com.pipemasters.assets.AssetsParser;
import com.pipemasters.capture.CapturePoints;
import com.pipemasters.capture.CapturePointsParser;
import com.pipemasters.gameplay.GameplayDataInfo;
import com.pipemasters.gameplay.GameplayDataParser;
import com.pipemasters.layer.LayerMetadata;
import com.pipemasters.layer.LayerMetadataParser;
import com.pipemasters.layer.LayerPathResolver;
import com.pipemasters.layerdata.LayerDataParser;
import com.pipemasters.layerdata.TeamConfigurationComposer;
import com.pipemasters.mapassets.MapAssets;
import com.pipemasters.mapassets.MapAssetsParser;
import com.pipemasters.model.Layer;
import com.pipemasters.model.LayerTeamConfiguration;
import com.pipemasters.objectives.Objective;
import com.pipemasters.objectives.ObjectivesParser;
import com.pipemasters.units.UnitFactionFactory;
import com.pipemasters.units.Units;
import com.pipemasters.units.UnitsFilter;
import com.pipemasters.util.MissingAssetLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LayerExportApplication {
    private static final Pattern VERSION_SEGMENT_PATTERN = Pattern.compile("(?i)_v\\d+(?:\\.\\d+)?");
    private static final Logger LOGGER  = LogManager.getLogger(LayerExportApplication.class);
    private final ObjectMapper mapper;
    private final GameplayDataParser gameplayDataParser;
    private final LayerPathResolver layerPathResolver;
    private final TeamConfigurationComposer teamConfigurationComposer;
    private final UnitsFilter unitsFilter;

    public LayerExportApplication(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        LayerDataParser layerDataParser = new LayerDataParser(mapper);
        this.gameplayDataParser = new GameplayDataParser();
        this.layerPathResolver = new LayerPathResolver();
        this.teamConfigurationComposer = new TeamConfigurationComposer(layerDataParser, new UnitFactionFactory());
        this.unitsFilter = new UnitsFilter();
    }

    public LayerExportResult run(LayerExportRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        LOGGER.info("Starting layer export for gameplay data '{}'", request.gameplayDataPath());

        if (!Files.exists(request.gameplayDataPath())) {
            throw new LayerExportException(String.format("Gameplay data file '%s' does not exist.", request.gameplayDataPath()));
        }

        LOGGER.debug("Reading gameplay data from '{}'.", request.gameplayDataPath());
        JsonNode gameplayDataRoot = mapper.readTree(request.gameplayDataPath().toFile());
        GameplayDataInfo gameplayDataInfo = gameplayDataParser.parse(gameplayDataRoot);
        LOGGER.info("Loaded gameplay data '{}' (row '{}', reported version: {}).",
                gameplayDataInfo.layerName(),
                gameplayDataInfo.rowName(),
                gameplayDataInfo.layerVersion());

        Path exportsRoot = layerPathResolver.resolveExportsRoot(request.gameplayDataPath());
        MissingAssetLogger missingLayerLogger = new MissingAssetLogger(exportsRoot, Path.of("missing-layers.txt"));
        
        Path layerJsonPath = layerPathResolver.resolveLayerJson(
                request.explicitLayerPath(),
                gameplayDataInfo,
                exportsRoot,
                missingLayerLogger,
                request.gameplayDataPath());

        LOGGER.info("Resolved layer JSON path to '{}'.", layerJsonPath);
        JsonNode layerRoot = mapper.readTree(layerJsonPath.toFile());
        CapturePointsParser capturePointsParser = new CapturePointsParser(mapper);
        CapturePoints capturePoints = capturePointsParser.parseCapturePoints(layerRoot);

        ObjectivesParser objectivesParser = new ObjectivesParser();
        Map<String, Objective> objectives = objectivesParser.parseObjectives(layerRoot, capturePoints.clusters());

        LayerMetadataParser metadataParser = new LayerMetadataParser();
        LayerMetadata metadata = metadataParser.parse(layerJsonPath, layerRoot);
        String dataLayerVersion = gameplayDataInfo.layerVersion();
        if (dataLayerVersion != null && !dataLayerVersion.isBlank()) {
            metadata = metadata.withLayerVersion(dataLayerVersion);
        }

        MapAssetsParser mapAssetsParser = new MapAssetsParser();
        MapAssets mapAssets = mapAssetsParser.parse(layerRoot);

        AssetsParser assetsParser = new AssetsParser();
        Assets assets = assetsParser.parse(layerRoot);

        Units units = loadUnits(request.unitsPath());
        LayerTeamConfiguration teamConfiguration = teamConfigurationComposer.compose(request.gameplayDataPath(), units);

        Units filteredUnits = unitsFilter.filter(units, teamConfiguration);

        Layer layer = new Layer(metadata, capturePoints, objectives, mapAssets, assets, teamConfiguration, filteredUnits);

        Path outputDir = request.projectRoot().resolve("output");
        Files.createDirectories(outputDir);

        String outputFileName = createOutputFileName(layerJsonPath, metadata.layerVersion());
        Path outputPath = outputDir.resolve(outputFileName);

        LOGGER.info("Writing exported layer JSON to '{}'.", outputPath);
        if (Files.exists(outputPath)) {
            LOGGER.warn("Output file '{}' already exists and will be overwritten.", outputPath);
        }
        Files.deleteIfExists(outputPath);
        mapper.writeValue(outputPath.toFile(), layer);

        return new LayerExportResult(outputPath, metadata.layerVersion());
    }

    private Units loadUnits(Path unitsPath) throws IOException {
        if (unitsPath == null || !Files.exists(unitsPath)) {
            LOGGER.warn("Units data not found at '{}'. Continuing without units data.", unitsPath);
            return null;
        }
        LOGGER.info("Loading units data from '{}'.", unitsPath);
        return mapper.readValue(unitsPath.toFile(), Units.class);
    }

    private String createOutputFileName(Path originalPath, String reportedVersion) {
        String originalFileName = originalPath.getFileName().toString();
        if (reportedVersion == null || reportedVersion.isBlank()) {
            return originalFileName;
        }

        int dotIndex = originalFileName.lastIndexOf('.');
        String extension = "";
        String baseName = originalFileName;
        if (dotIndex >= 0) {
            extension = originalFileName.substring(dotIndex);
            baseName = originalFileName.substring(0, dotIndex);
        }

        String sanitizedVersion = reportedVersion.startsWith("v") ? reportedVersion : "v" + reportedVersion;
        Matcher matcher = VERSION_SEGMENT_PATTERN.matcher(baseName);
        int lastMatchStart = -1;
        int lastMatchEnd = -1;
        while (matcher.find()) {
            lastMatchStart = matcher.start();
            lastMatchEnd = matcher.end();
        }

        if (lastMatchStart >= 0) {
            baseName = baseName.substring(0, lastMatchStart)
                    + "_" + sanitizedVersion
                    + baseName.substring(lastMatchEnd);
        } else if (!baseName.isBlank()) {
            baseName = baseName + "_" + sanitizedVersion;
        } else {
            baseName = sanitizedVersion;
        }

        return baseName + extension;
    }
}
