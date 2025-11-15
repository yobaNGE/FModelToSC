// java
package com.pipemasters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pipemasters.app.LayerExportApplication;
import com.pipemasters.app.LayerExportArgumentsParser;
import com.pipemasters.app.LayerExportException;
import com.pipemasters.app.LayerExportRequest;
import com.pipemasters.app.LayerExportResult;

import java.io.IOException;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        LayerExportArgumentsParser argumentsParser = new LayerExportArgumentsParser();
        LayerExportRequest request;
        try {
            request = argumentsParser.parse(args);
        } catch (LayerExportException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        LayerExportApplication application = new LayerExportApplication(mapper);
        try {
            LayerExportResult result = application.run(request);
            String reportedVersion = result.layerVersion() != null && !result.layerVersion().isBlank()
                    ? result.layerVersion()
                    : "<unknown>";
            System.out.printf("Layer version: %s%n", reportedVersion);
            System.out.printf("Wrote layer JSON to '%s'%n", result.outputPath());
        } catch (LayerExportException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
