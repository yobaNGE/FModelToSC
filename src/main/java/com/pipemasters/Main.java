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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        LayerExportArgumentsParser argumentsParser = new LayerExportArgumentsParser();
        LayerBatchExportRequest batchRequest;
        try {
            batchRequest = argumentsParser.parse(args);
        } catch (LayerExportException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (!Files.exists(batchRequest.layerListPath())) {
            System.err.printf("Layer list file '%s' does not exist.%n", batchRequest.layerListPath());
            System.exit(1);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        LayerExportApplication application = new LayerExportApplication(mapper);
        List<String> layerDefinitions;
        try {
            layerDefinitions = Files.readAllLines(batchRequest.layerListPath());
        } catch (IOException e) {
            System.err.printf("Failed to read layer list file '%s': %s%n", batchRequest.layerListPath(), e.getMessage());
            System.exit(1);
            return;
        }

        int processed = 0;
        int succeeded = 0;
        int failed = 0;

        for (int i = 0; i < layerDefinitions.size(); i++) {
            int lineNumber = i + 1;
            LayerExportRequest request;
            try {
                request = argumentsParser.parseLayerDefinition(layerDefinitions.get(i), batchRequest, lineNumber);
            } catch (LayerExportException e) {
                System.err.printf("Line %d: %s%n", lineNumber, e.getMessage());
                failed++;
                continue;
            }

            if (request == null) {
                continue;
            }

            processed++;
            try {
                LayerExportResult result = application.run(request);
                String reportedVersion = result.layerVersion() != null && !result.layerVersion().isBlank()
                        ? result.layerVersion()
                        : "<unknown>";
                System.out.printf("[%d] Layer version: %s%n", lineNumber, reportedVersion);
                System.out.printf("[%d] Wrote layer JSON to '%s'%n", lineNumber, result.outputPath());
                succeeded++;
            } catch (LayerExportException | IOException e) {
                System.err.printf("[%d] %s%n", lineNumber, e.getMessage());
                failed++;
            }
        }

        System.out.printf("Finished processing %d layer definitions. Successes: %d. Failures: %d.%n", processed, succeeded, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}