package com.pipemasters.units;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UnitsMain {
    private static final Logger LOGGER = LogManager.getLogger(UnitsMain.class);

    private UnitsMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            LOGGER.error("Usage: java -jar app.jar <path-to-faction-setup-root>");
            System.exit(1);
        }

        Path baseDir = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.exists(baseDir)) {
            LOGGER.error("Input directory '{}' does not exist.", baseDir);
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        UnitsParser parser = new UnitsParser(mapper, baseDir);
        LOGGER.info("Starting units export using base directory '{}'", baseDir);
        Units units = parser.parse();
        LOGGER.info("Parsed {} Team 1 units and {} Team 2 units.", units.team1Units().size(), units.team2Units().size());

        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path outputDir = projectRoot.resolve("output");
        Files.createDirectories(outputDir);

        Path outputPath = outputDir.resolve("units.json");
        Files.deleteIfExists(outputPath);
        mapper.writeValue(outputPath.toFile(), units);

        LOGGER.info("Wrote units JSON to '{}'", outputPath);
    }
}