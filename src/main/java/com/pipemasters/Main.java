// java
package com.pipemasters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pipemasters.assets.Assets;
import com.pipemasters.assets.AssetsParser;
import com.pipemasters.capture.CapturePoints;
import com.pipemasters.capture.CapturePointsParser;
import com.pipemasters.layer.LayerMetadata;
import com.pipemasters.layer.LayerMetadataParser;
import com.pipemasters.mapassets.MapAssets;
import com.pipemasters.mapassets.MapAssetsParser;
import com.pipemasters.model.Layer;
import com.pipemasters.objectives.Objective;
import com.pipemasters.objectives.ObjectivesParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar app.jar <path-to-input-json>");
            System.exit(1);
        }

        Path exportPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path projectRoot = Path.of("").toAbsolutePath().normalize();

        if (!Files.exists(exportPath)) {
            System.err.printf("Input file '%s' does not exist.%n", exportPath);
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        JsonNode root = mapper.readTree(exportPath.toFile());
        CapturePointsParser parser = new CapturePointsParser(mapper);
        CapturePoints capturePoints = parser.parseCapturePoints(root);

        ObjectivesParser objectivesParser = new ObjectivesParser();
        Map<String, Objective> objectives = objectivesParser.parseObjectives(root, capturePoints.clusters());

        LayerMetadataParser metadataParser = new LayerMetadataParser();
        LayerMetadata metadata = metadataParser.parse(exportPath, root);

        MapAssetsParser mapAssetsParser = new MapAssetsParser();
        MapAssets mapAssets = mapAssetsParser.parse(root);

        AssetsParser assetsParser = new AssetsParser();
        Assets assets = assetsParser.parse(root);

        Layer layer = new Layer(metadata, capturePoints, objectives, mapAssets, assets);

        Path outputDir = projectRoot.resolve("output");
        Files.createDirectories(outputDir);

        String outputFileName = exportPath.getFileName().toString();
        Path outputPath = outputDir.resolve(outputFileName);

        Files.deleteIfExists(outputPath);
        mapper.writeValue(outputPath.toFile(), layer);

        System.out.printf("Wrote layer JSON to '%s'%n", outputPath);
    }
}