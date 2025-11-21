package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class FactionSetupTableParser {
    private static final Logger LOGGER = LogManager.getLogger(FactionSetupTableParser.class);

    private final ObjectMapper mapper;

    FactionSetupTableParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    Map<String, FactionSetupRow> parse(Path path) throws IOException {
        if (path == null) {
            LOGGER.debug("No faction setup table path provided; returning empty result.");
            return Map.of();
        }
        if (!Files.exists(path)) {
            LOGGER.error("Faction setup table '{}' does not exist.", path);
            return Map.of();
        }
        LOGGER.info("Parsing faction setup table from '{}'", path);
        JsonNode root = mapper.readTree(path.toFile());
        Map<String, FactionSetupRow> rows = new HashMap<>();
        if (!root.isArray()) {
            LOGGER.warn("Faction setup table '{}' is not an array node.", path);
            return rows;
        }
        for (JsonNode node : root) {
            if (!"DataTable".equals(node.path("Type").asText())) {
                continue;
            }
            JsonNode rowsNode = node.path("Rows");
            if (rowsNode == null || rowsNode.isMissingNode()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> iterator = rowsNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String rowName = entry.getKey();
                JsonNode rowNode = entry.getValue();
                JsonNode shortNameNode = JsonUtils.findFirstProperty(rowNode, "ShortName");
                JsonNode displayNameNode = JsonUtils.findFirstProperty(rowNode, "DisplayName");
                JsonNode descriptionNode = JsonUtils.findFirstProperty(rowNode, "Description");
                JsonNode badgeNode = JsonUtils.findFirstProperty(rowNode, "UnitBadge");
                JsonNode factionIdNode = JsonUtils.findFirstProperty(rowNode, "OuterFactionId");

                String shortName = TextUtils.readText(shortNameNode);
                String displayName = TextUtils.readText(displayNameNode);
                String description = TextUtils.readText(descriptionNode);
                String unitBadge = TextUtils.readAssetName(badgeNode);
                String factionId = JsonUtils.readString(factionIdNode);

                rows.put(rowName, new FactionSetupRow(rowName, shortName, displayName, description, unitBadge, factionId));
            }
        }
        LOGGER.info("Loaded {} faction setup rows from '{}'", rows.size(), path);
        return rows;
    }
}