package com.pipemasters.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.pipemasters.util.AssetResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class CommanderActionSettingsLoader {
    private final ObjectMapper mapper;
    private final AssetResolver resolver;
    private final Map<Path, Map<String, CommanderActionSettings>> cache = new HashMap<>();

    CommanderActionSettingsLoader(ObjectMapper mapper, Path rootDir) {
        this.mapper = mapper;
        this.resolver = new AssetResolver(rootDir);
    }

    CommanderActionSettings load(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) {
            return CommanderActionSettings.UNKNOWN;
        }
        String settingsName = BlueprintUtils.extractReferenceName(reference);
        String objectPath = reference.path("ObjectPath").asText(null);
        if (settingsName == null || objectPath == null) {
            return CommanderActionSettings.UNKNOWN;
        }
        Path resolved = resolver.resolve(objectPath);
        if (resolved == null || !Files.exists(resolved)) {
            return CommanderActionSettings.UNKNOWN;
        }
        Map<String, CommanderActionSettings> map = cache.computeIfAbsent(resolved, this::readSettingsFile);
        return map.getOrDefault(settingsName, CommanderActionSettings.UNKNOWN);
    }

    private Map<String, CommanderActionSettings> readSettingsFile(Path path) {
        Map<String, CommanderActionSettings> result = new HashMap<>();
        try {
            JsonNode root = mapper.readTree(path.toFile());
            if (!root.isArray()) {
                return result;
            }
            for (JsonNode node : root) {
                if (!"BP_SQCommanderActionSettings_C".equals(node.path("Type").asText())) {
                    continue;
                }
                String name = node.path("Name").asText("");
                JsonNode properties = node.path("Properties");
                String displayName = TextUtils.readText(JsonUtils.findFirstProperty(properties, "DisplayName"));
                if (displayName.isBlank()) {
                    displayName = BlueprintUtils.prettifyName(name);
                }
                String icon = TextUtils.readAssetName(JsonUtils.findFirstProperty(properties, "Icon"));
                if (icon.isBlank()) {
                    icon = "questionmark";
                }
                result.put(name, new CommanderActionSettings(displayName, icon));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read commander action settings from " + path, e);
        }
        return result;
    }
}