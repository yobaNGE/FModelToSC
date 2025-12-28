package com.pipemasters.vehicles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pipemasters.units.VehiclesParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class VehiclesMain {
    private static final Logger LOGGER = LogManager.getLogger(VehiclesMain.class);

    private VehiclesMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            LOGGER.error("Usage: java -jar app.jar <path-to-faction-setup-root> [threads]");
            System.exit(1);
        }

        Path baseDir = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.exists(baseDir)) {
            LOGGER.error("Input directory '{}' does not exist.", baseDir);
            System.exit(1);
        }

        int threads = 8;
        if (args.length == 2) {
            try {
                threads = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid thread count '{}'. Expected a positive integer.", args[1]);
                System.exit(1);
            }
            if (threads < 1) {
                LOGGER.error("Thread count must be >= 1. Received {}.", threads);
                System.exit(1);
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        VehiclesParser parser = new VehiclesParser(mapper, baseDir);
        LOGGER.info("Starting vehicles export using base directory '{}'", baseDir);
        LOGGER.info("Using {} thread(s) for vehicle parsing.", threads);
        List<VehicleExport> vehicles = parser.parse(threads);
        LOGGER.info("Parsed {} unique vehicles.", vehicles.size());

        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path outputDir = projectRoot.resolve("output");
        Files.createDirectories(outputDir);

        Path outputPath = outputDir.resolve("vehiclesSAT.json");
        Files.deleteIfExists(outputPath);
        mapper.writeValue(outputPath.toFile(), vehicles);

        LOGGER.info("Wrote vehicles JSON to '{}'", outputPath);
    }
}
