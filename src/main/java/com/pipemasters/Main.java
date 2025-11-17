package com.pipemasters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pipemasters.app.LayerBatchExportRequest;
import com.pipemasters.app.LayerExportApplication;
import com.pipemasters.app.LayerExportArgumentsParser;
import com.pipemasters.app.LayerExportException;
import com.pipemasters.app.LayerExportRequest;
import com.pipemasters.app.LayerExportResult;
import com.pipemasters.util.MissingAssetLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    private Main() {}

    public static void main(String[] args) throws IOException {
        LayerExportArgumentsParser argumentsParser = new LayerExportArgumentsParser();
        LayerBatchExportRequest batchRequest;
        LOGGER.info("Starting layer export process with {} argument(s).", args.length);
        try {
            batchRequest = argumentsParser.parse(args);
        } catch (LayerExportException e) {
            LOGGER.error("Failed to parse command line arguments: {}", e.getMessage());
            System.exit(1);
            return;
        }

        if (!Files.exists(batchRequest.layerListPath())) {
            LOGGER.error("Layer list file '{}' does not exist.", batchRequest.layerListPath());
            System.exit(1);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        LayerExportApplication application = new LayerExportApplication(mapper);
        List<String> layerDefinitions;
        LOGGER.info("Reading layer definitions from '{}'.", batchRequest.layerListPath());
        try {
            layerDefinitions = Files.readAllLines(batchRequest.layerListPath());
        } catch (IOException e) {
            LOGGER.error("Failed to read layer list file '{}': {}", batchRequest.layerListPath(), e.getMessage());
            System.exit(1);
            return;
        }
        LOGGER.info("Loaded {} layer definition(s).", layerDefinitions.size());

        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        Set<String> processedRequests = new HashSet<>();

        for (int i = 0; i < layerDefinitions.size(); i++) {
            int lineNumber = i + 1;
            LOGGER.debug("Parsing layer definition at line {}: {}", lineNumber, layerDefinitions.get(i));
            LayerExportRequest request;
            try {
                request = argumentsParser.parseLayerDefinition(layerDefinitions.get(i), batchRequest, lineNumber);
            } catch (LayerExportException e) {
                LOGGER.warn("Line {}: {}", lineNumber, e.getMessage());
                failed++;
                continue;
            }

            if (request == null) {
                LOGGER.debug("Line {} did not produce a request (blank/comment). Skipping.", lineNumber);
                continue;
            }

            String deduplicationKey = buildDeduplicationKey(request);
            if (!processedRequests.add(deduplicationKey)) {
                LOGGER.info("[{}] Gameplay data '{}' (layer '{}') already queued earlier. Skipping duplicate entry.",
                        lineNumber,
                        request.gameplayDataPath(),
                        request.explicitLayerPath());
                processed--;
                continue;
            }

            processed++;
            LOGGER.info("[{}] Running export for gameplay data '{}'.", lineNumber, request.gameplayDataPath());
            try {
                LayerExportResult result = application.run(request);
                String reportedVersion = result.layerVersion() != null && !result.layerVersion().isBlank()
                        ? result.layerVersion()
                        : "<unknown>";
                LOGGER.info("[{}] Wrote layer with version {} JSON  to '{}'", lineNumber, reportedVersion, result.outputPath());
                succeeded++;
            } catch (LayerExportException | IOException e) {
                LOGGER.error("[{}] {}", lineNumber, e.getMessage());
                failed++;
            }
        }

        LOGGER.info("Finished processing {} layer definitions. Successes: {}. Failures: {}.", processed, succeeded, failed);
        if (failed > 0) {
            LOGGER.warn("Exiting with non-zero status because {} layer definition(s) failed.", failed);
            System.exit(1);
        }
    }
    private static String buildDeduplicationKey(LayerExportRequest request) {
//        String explicitLayerPath = request.explicitLayerPath() == null ? "" : request.explicitLayerPath().toString();
//        return request.gameplayDataPath().toString() + "|" + explicitLayerPath;
        return request.gameplayDataPath().toString();
    }
}