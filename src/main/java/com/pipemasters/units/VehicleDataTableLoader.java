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
import java.util.Iterator;
import java.util.Map;

final class VehicleDataTableLoader {
    private static final Logger LOGGER = LogManager.getLogger(VehicleDataTableLoader.class);
    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final MissingAssetLogger logger;
    private final Map<Path, Map<String, VehicleDataRow>> cache = new HashMap<>();

    VehicleDataTableLoader(ObjectMapper mapper, Path rootDir, MissingAssetLogger logger) {
        this.mapper = mapper;
        this.resolver = new AssetResolver(rootDir);
        this.logger = logger;
    }

    VehicleDataRow load(JsonNode dataNode, String settingsName) {
        if (dataNode == null || dataNode.isMissingNode()) {
            LOGGER.debug("Vehicle data node missing for settings '{}'; using empty row.", settingsName);
            return VehicleDataRow.EMPTY;
        }
        JsonNode tableNode = dataNode.path("DataTable");
        String rowName = dataNode.path("RowName").asText(null);
        if (tableNode.isMissingNode() || rowName == null || rowName.isBlank()) {
            LOGGER.debug("Vehicle data node missing table or row for settings '{}'", settingsName);
            return VehicleDataRow.EMPTY;
        }
        String objectPath = tableNode.path("ObjectPath").asText(null);
        LOGGER.debug("Resolving vehicle data table '{}' for row '{}'", objectPath, rowName);
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null) {
            if (logger != null && objectPath != null) {
                logger.missing(objectPath, "vehicle data table for " + settingsName);
            }
            LOGGER.debug("Unable to resolve vehicle data table for path '{}'", objectPath);
            return VehicleDataRow.EMPTY;
        }
        if (!Files.exists(resolved)) {
            if (logger != null) {
                logger.missing(objectPath, "vehicle data table for " + settingsName);
            }
            LOGGER.debug("Vehicle data table '{}' does not exist for row '{}'", resolved, rowName);
            return VehicleDataRow.EMPTY;
        }
        LOGGER.trace("Loading vehicle data table '{}'", resolved);
        Map<String, VehicleDataRow> rows = cache.computeIfAbsent(resolved, this::readDataTable);
        VehicleDataRow row = rows.get(rowName);
        if (row == null) {
            if (logger != null) {
                logger.missing(rowName + " in " + resolved, "vehicle data row for " + settingsName);
            }
            LOGGER.debug("Vehicle data row '{}' not found in '{}'", rowName, resolved);
            return VehicleDataRow.EMPTY;
        }
        LOGGER.debug("Resolved vehicle data row '{}' from '{}'", rowName, resolved);
        return row;
    }

    private Map<String, VehicleDataRow> readDataTable(Path path) {
        Map<String, VehicleDataRow> result = new HashMap<>();
        try {
            LOGGER.trace("Parsing vehicle data table '{}'", path);
            JsonNode root = mapper.readTree(path.toFile());
            if (!root.isArray()) {
                LOGGER.warn("Vehicle data table '{}' is not an array node.", path);
                return result;
            }
            for (JsonNode node : root) {
                if (!"DataTable".equals(node.path("Type").asText())) {
                    continue;
                }
                JsonNode rowsNode = node.path("Rows");
                if (!rowsNode.isObject()) {
                    continue;
                }
                Iterator<Map.Entry<String, JsonNode>> iterator = rowsNode.fields();
                while (iterator.hasNext()) {
                    Map.Entry<String, JsonNode> entry = iterator.next();
                    String rowName = entry.getKey();
                    JsonNode rowNode = entry.getValue();
                    JsonNode displayNode = JsonUtils.findFirstProperty(rowNode, "DisplayName");
                    JsonNode detailsNode = JsonUtils.findFirstProperty(rowNode, "Details");
                    JsonNode specificsNode = JsonUtils.findFirstProperty(rowNode, "Specifics");
                    JsonNode iconNode = JsonUtils.findFirstProperty(rowNode, "Icon");
                    String displayName = TextUtils.readText(displayNode);
                    if (displayName.isBlank()) {
                        displayName = BlueprintUtils.prettifyName(rowName);
                    }
                    String description = TextUtils.readText(detailsNode);
                    if (description.isBlank()) {
                        description = TextUtils.readText(specificsNode);
                    }
                    String icon = TextUtils.readAssetName(iconNode);
                    result.put(rowName, new VehicleDataRow(displayName, description, icon));
                }
            }
            LOGGER.info("Loaded {} vehicle data rows from '{}'", result.size(), path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vehicle data table from " + path, e);
        }
        return result;
    }
}