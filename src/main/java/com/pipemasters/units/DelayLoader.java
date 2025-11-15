package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pipemasters.util.AssetResolver;
import com.pipemasters.util.MissingAssetLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class DelayLoader {
    private static final Logger LOGGER = LogManager.getLogger(DelayLoader.class);
    private static final double TICKS_PER_MINUTE = 600_000_000d;

    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final Map<Path, DelaySettings> cache = new HashMap<>();
    private final MissingAssetLogger logger;


    DelayLoader(ObjectMapper mapper, Path rootDir, MissingAssetLogger logger) {
        this.mapper = mapper;
        this.resolver = new AssetResolver(rootDir);
        this.logger = logger;
    }

    DelaySettings load(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            LOGGER.debug("Delay reference missing or empty; using default delay settings.");
            return DelaySettings.NONE;
        }
        String objectPath = reference.path("ObjectPath").asText(null);
        LOGGER.debug("Resolving delay settings from object path '{}'", objectPath);
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null) {
            if (objectPath != null && logger != null) {
                logger.missing(objectPath, "delay settings");
            }
            LOGGER.debug("Unable to resolve delay settings path for '{}'", objectPath);
            return DelaySettings.NONE;
        }
        if (!Files.exists(resolved)) {
            if (logger != null) {
                logger.missing(objectPath, "delay settings");
            }
            LOGGER.debug("Resolved delay settings path '{}' does not exist.", resolved);
            return DelaySettings.NONE;
        }
        LOGGER.debug("Loading delay settings from '{}'", resolved);
        return cache.computeIfAbsent(resolved, this::readDelayFile);
    }

    private DelaySettings readDelayFile(Path path) {
        try {
            LOGGER.trace("Reading delay settings file '{}'", path);
            JsonNode root = mapper.readTree(path.toFile());
            if (!root.isArray()) {
                LOGGER.warn("Delay settings file '{}' is not an array node.", path);
                return DelaySettings.NONE;
            }
            for (JsonNode node : root) {
                if (!"SQRestriction_Delay".equals(node.path("Type").asText())) {
                    continue;
                }
                JsonNode properties = node.path("Properties");
                long initial = readTicks(properties.path("InitialDelay"));
                long delay = readTicks(properties.path("Delay"));
                LOGGER.debug("Parsed delay settings from '{}': initial={} minutes, respawn={} minutes", path,
                        toMinutes(initial), toMinutes(delay));
                return new DelaySettings(toMinutes(initial), toMinutes(delay));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read delay from " + path, e);
        }
        LOGGER.warn("Delay settings array in '{}' did not contain SQRestriction_Delay entry.", path);
        return DelaySettings.NONE;
    }

    private long readTicks(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return 0;
        }
        if (node.has("Ticks")) {
            return node.path("Ticks").asLong(0);
        }
        return node.asLong(0);
    }

    private int toMinutes(long ticks) {
        if (ticks == 0) {
            return 0;
        }
        return (int) Math.round(ticks / TICKS_PER_MINUTE);
    }
}