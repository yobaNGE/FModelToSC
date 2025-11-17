package com.pipemasters.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LayerExportArgumentsParser {
    private static final int MIN_ARGS = 1;
    private static final int MAX_ARGS = 2;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final Logger LOGGER = LogManager.getLogger(LayerExportArgumentsParser.class);

    public LayerBatchExportRequest parse(String[] args) {
        int argumentCount = args == null ? 0 : args.length;
        LOGGER.info("Received {} command line argument(s).", argumentCount);
        if (args == null || argumentCount < MIN_ARGS || argumentCount > MAX_ARGS) {
            LOGGER.error(
                    "Invalid number of arguments: {}. Expected between {} and {}.",
                    argumentCount,
                    MIN_ARGS,
                    MAX_ARGS);
            throw new LayerExportException("Usage: java -jar app.jar <path-to-layer-list-txt> [path-to-units-json]");
        }

        Path projectRoot = Path.of("").toAbsolutePath().normalize();

        Path layerListPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path unitsPath = projectRoot.resolve("output").resolve("units.json");
        if (args.length == 2) {
            unitsPath = Path.of(args[1]).toAbsolutePath().normalize();
        }

        LOGGER.info("Using layer list '{}' and units path '{}'.", layerListPath, unitsPath);
        return new LayerBatchExportRequest(projectRoot, layerListPath, unitsPath);
    }

    public LayerExportRequest parseLayerDefinition(String line, LayerBatchExportRequest batchRequest, int lineNumber) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            LOGGER.debug("Line {} is blank. Skipping.", lineNumber);
            return null;
        }
        if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
            LOGGER.debug("Line {} is a comment. Skipping.", lineNumber);
            return null;
        }

        LOGGER.debug("Parsing line {}: {}", lineNumber, trimmed);
        String[] tokens = normalizeTokens(tokenize(trimmed));
        if (tokens.length == 0) {
            LOGGER.debug("No tokens were parsed from line {}.", lineNumber);
            return null;
        }
        if (tokens.length > 2) {
            throw new LayerExportException(String.format("Line %d contains too many entries. Expected '<gameplay-data-json> [gameplay-layer-json]'.", lineNumber));
        }

        Path gameplayDataPath = Path.of(tokens[0]).toAbsolutePath().normalize();
        Path explicitLayerPath = null;
        if (tokens.length == 2) {
            explicitLayerPath = Path.of(tokens[1]).toAbsolutePath().normalize();
        }

        if (explicitLayerPath != null) {
            LOGGER.info("Line {} parsed gameplay data '{}' with explicit layer '{}'.", lineNumber, gameplayDataPath, explicitLayerPath);
        } else {
            LOGGER.info("Line {} parsed gameplay data '{}' and will resolve layer path automatically.", lineNumber, gameplayDataPath);
        }

        return new LayerExportRequest(batchRequest.projectRoot(), gameplayDataPath, explicitLayerPath, batchRequest.unitsPath());
    }

    private String[] tokenize(String value) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(value);
        while (matcher.find()) {
            String quoted = matcher.group(1);
            if (quoted != null) {
                parts.add(quoted);
            } else {
                String raw = matcher.group(2);
                if (raw != null) {
                    parts.add(raw);
                }
            }
        }
        return parts.toArray(new String[0]);
    }

    private String[] normalizeTokens(String[] tokens) {
        if (tokens.length == 0) {
            return tokens;
        }

        List<String> normalized = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(token);

            if (endsWithJson(token)) {
                normalized.add(builder.toString());
                builder.setLength(0);
            }
        }

        if (builder.length() > 0) {
            normalized.add(builder.toString());
        }

        if (normalized.size() > 2) {
            throw new LayerExportException("Unable to determine gameplay data and layer paths from line. Please quote paths with spaces or ensure there are at most two JSON paths per line.");
        }

        if (!normalized.isEmpty() && !endsWithJson(normalized.get(normalized.size() - 1))) {
            throw new LayerExportException("Each entry must end with '.json'. Quote the path if it contains spaces.");
        }

        return normalized.toArray(new String[0]);
    }

    private boolean endsWithJson(String value) {
        return value.toLowerCase().endsWith(".json");
    }
}