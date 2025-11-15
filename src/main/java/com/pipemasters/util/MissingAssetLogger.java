package com.pipemasters.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MissingAssetLogger {
    private static final Logger LOGGER = LogManager.getLogger(MissingAssetLogger.class);
    private static final Path DEFAULT_OUTPUT_FILE = Path.of("missing-assets.txt");
    private static final Set<Path> RESET_OUTPUT_FILES = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> messages = new HashSet<>();
    private final Set<String> recordedPaths = new HashSet<>();
    private final Path rootDir;
    private final Path exportsDir;
    private final String rootDirForward;
    private final String rootDirForwardLower;
    private final String exportsDirForward;
    private final String exportsDirForwardLower;
    private final Path outputFile;

    public MissingAssetLogger(Path rootDir) {
        this(rootDir, DEFAULT_OUTPUT_FILE);
    }

    public MissingAssetLogger(Path rootDir, Path outputFile) {
        this.rootDir = rootDir != null ? rootDir.toAbsolutePath().normalize() : null;
        this.outputFile = (outputFile != null ? outputFile : DEFAULT_OUTPUT_FILE).toAbsolutePath().normalize();
        resetOutputFile(this.outputFile);
        this.exportsDir = findExportsRoot(this.rootDir);
        this.rootDirForward = toForwardSlashes(this.rootDir);
        this.rootDirForwardLower = rootDirForward != null ? rootDirForward.toLowerCase(Locale.ROOT) : null;
        this.exportsDirForward = toForwardSlashes(this.exportsDir);
        this.exportsDirForwardLower = exportsDirForward != null ? exportsDirForward.toLowerCase(Locale.ROOT) : null;
    }

    public MissingAssetLogger() {
        this(null, DEFAULT_OUTPUT_FILE);
    }

    public void missing(Path path, String context) {
        if (path == null) {
            return;
        }
        String formatted = normalizePathComponent(path.toAbsolutePath().normalize().toString());
        missing(formatted, context);
    }

    public void missing(String asset, String context) {
        if (asset == null || asset.isBlank()) {
            return;
        }
        String normalized = normalizeAssetString(asset);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        String display = buildMissingAssetDisplay(normalized);
        String message;
        if (context == null || context.isBlank()) {
            message = "Missing asset: " + display;
        } else {
            message = String.format("Missing asset: %s (%s)", display, context);
        }
        if (messages.add(message)) {
            LOGGER.warn(message);
            writeMissingAssetPath(normalized);
        } else {
            LOGGER.debug("Suppressed duplicate missing asset log: {}", message);
        }
    }

    private void writeMissingAssetPath(String asset) {
        String relativePath = buildRelativeAssetPath(asset, true);
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        synchronized (recordedPaths) {
            String key = relativePath.toLowerCase(Locale.ROOT);
            if (!recordedPaths.add(key)) {
                return;
            }
        }
        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputFile, relativePath + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to record missing asset path {} to {}", relativePath, outputFile, e);
        }
    }

    private String buildMissingAssetDisplay(String asset) {
        Path expected = resolveExpectedFullPath(asset);
        if (expected != null) {
            return expected.toString();
        }
        String relative = buildRelativeAssetPath(asset, false);
        if (relative != null && !relative.isBlank()) {
            return relative;
        }
        return asset;
    }

    private Path resolveExpectedFullPath(String asset) {
        String relative = buildRelativeAssetPath(asset, false);
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path relativePath = toSystemPath(relative);
        if (relativePath == null) {
            return null;
        }
        if (exportsDir != null) {
            return exportsDir.resolve(relativePath).normalize();
        }
        if (rootDir != null) {
            return rootDir.resolve(relativePath).normalize();
        }
        return relativePath.normalize();
    }

    private String buildRelativeAssetPath(String asset, boolean forOutputFile) {
        String candidate = extractPathComponent(asset);
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = trimLeadingSlashes(candidate);
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] segments = trimmed.split("/");
        if (segments.length == 0) {
            return null;
        }
        List<String> result = new ArrayList<>();
        addBaseSegments(result, segments[0], forOutputFile);
        for (int i = 1; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (segment != null) {
                String cleaned = segment.trim();
                if (!cleaned.isBlank()) {
                    result.add(cleaned);
                }
            }
        }
        String fileSegment = segments[segments.length - 1];
        String fileName = toJsonFileName(fileSegment);
        if (fileName == null || fileName.isBlank()) {
            if (segments.length == 1) {
                String cleaned = segments[0] != null ? segments[0].trim() : null;
                if (cleaned != null && !cleaned.isBlank()) {
                    result.add(cleaned);
                }
            } else {
                String cleaned = fileSegment != null ? fileSegment.trim() : null;
                if (cleaned != null && !cleaned.isBlank()) {
                    result.add(cleaned);
                }
            }
        } else {
            result.add(fileName);
        }
        if (result.isEmpty()) {
            return null;
        }
        return String.join("/", result);
    }

    private void addBaseSegments(List<String> segments, String firstSegment, boolean forOutputFile) {
        if (firstSegment == null || firstSegment.isBlank()) {
            return;
        }
        String cleaned = firstSegment.trim();
        if (cleaned.isEmpty()) {
            return;
        }
        if (cleaned.equalsIgnoreCase("Game")) {
//            segments.add("SquadGame");
//            segments.add("Plugins");
//            segments.add("Mods");
//            segments.add("Steel_Division");
//            segments.add("Content");
            segments.add("SquadGame");
            segments.add("Content");
        } else if (cleaned.equalsIgnoreCase("Steel_Division")) {
            if (forOutputFile) {
                segments.add("SquadGame");
                segments.add("Plugins");
                segments.add("Mods");
                segments.add("Steel_Division");
                segments.add("Content");
            } else {
                segments.add("SquadGame");
                segments.add("Content");
            }
        } else {
            segments.add(cleaned);
        }
    }

    private String toJsonFileName(String segment) {
        if (segment == null) {
            return null;
        }
        String trimmed = segment.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return trimmed;
        }
        int dotIndex = trimmed.indexOf('.');
        String base = dotIndex >= 0 ? trimmed.substring(0, dotIndex) : trimmed;
        if (base.isEmpty()) {
            return null;
        }
        return base + ".json";
    }

    private Path toSystemPath(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        try {
            return Path.of(relative.replace('/', File.separatorChar));
        } catch (InvalidPathException e) {
            LOGGER.debug("Failed to convert relative path {} to system path", relative, e);
            return null;
        }
    }

    private static void resetOutputFile(Path outputFile) {
        if (outputFile == null) {
            return;
        }
        Path normalized = outputFile.toAbsolutePath().normalize();
        synchronized (RESET_OUTPUT_FILES) {
            if (!RESET_OUTPUT_FILES.add(normalized)) {
                return;
            }
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException e) {
            LOGGER.error("Failed to reset missing asset file {}", normalized, e);
        }
    }

    private String normalizeAssetString(String asset) {
        String sanitized = sanitize(asset);
        if (sanitized.isEmpty()) {
            return null;
        }
        int inIndex = indexOfIn(sanitized);
        if (inIndex >= 0) {
            String prefix = sanitized.substring(0, inIndex).trim();
            String pathPart = sanitized.substring(inIndex + 4).trim();
            String normalizedPath = normalizePathComponent(pathPart);
            if (normalizedPath != null && !normalizedPath.isBlank()) {
                return prefix.isEmpty() ? normalizedPath : prefix + " in " + normalizedPath;
            }
            return prefix.isEmpty() ? pathPart : prefix + " in " + pathPart;
        }
        String normalizedPath = normalizePathComponent(sanitized);
        return normalizedPath != null ? normalizedPath : sanitized;
    }

    private String extractPathComponent(String asset) {
        if (asset == null || asset.isBlank()) {
            return null;
        }
        String sanitized = sanitize(asset);
        if (sanitized.isEmpty()) {
            return null;
        }
        int inIndex = indexOfIn(sanitized);
        if (inIndex >= 0) {
            sanitized = sanitized.substring(inIndex + 4).trim();
        }
        if (sanitized.isEmpty()) {
            return null;
        }
        return normalizePathComponent(sanitized);
    }

    private static int indexOfIn(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.lastIndexOf(" in ");
    }

    private static Path findExportsRoot(Path root) {
        Path current = root;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "Exports".equalsIgnoreCase(name.toString())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String normalizePathComponent(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = sanitize(value);
        if (sanitized.isEmpty()) {
            return null;
        }
        String withoutPrefix = stripKnownPrefixes(sanitized);
        String collapsed = collapseSlashes(withoutPrefix);
        if (collapsed.isEmpty()) {
            return collapsed;
        }
        if (!collapsed.equals(sanitized)) {
            return trimLeadingSlashes(collapsed);
        }
        return collapsed;
    }

    private String stripKnownPrefixes(String value) {
        String result = value;
        String lower = result.toLowerCase(Locale.ROOT);
        if (exportsDirForwardLower != null) {
            int index = lower.indexOf(exportsDirForwardLower);
            if (index >= 0) {
                result = result.substring(index + exportsDirForward.length());
                lower = result.toLowerCase(Locale.ROOT);
            }
        }
        if (rootDirForwardLower != null) {
            int index = lower.indexOf(rootDirForwardLower);
            if (index >= 0) {
                result = result.substring(index + rootDirForward.length());
                lower = result.toLowerCase(Locale.ROOT);
            }
        }
        if (lower.startsWith("exports/")) {
            result = result.substring("exports/".length());
            lower = result.toLowerCase(Locale.ROOT);
        } else if (lower.startsWith("/exports/")) {
            result = result.substring("/exports/".length());
            lower = result.toLowerCase(Locale.ROOT);
        }
        int marker = lower.indexOf("/exports/");
        if (marker >= 0) {
            result = result.substring(marker + "/exports/".length());
        }
        return result;
    }

    private String collapseSlashes(String value) {
        if (value.indexOf('/') < 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        char prev = 0;
        for (char ch : value.toCharArray()) {
            if (ch == '/') {
                if (prev == '/') {
                    continue;
                }
            }
            builder.append(ch);
            prev = ch;
        }
        return builder.toString();
    }

    private String trimLeadingSlashes(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index++;
        }
        return value.substring(index);
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2) {
            char start = trimmed.charAt(0);
            char end = trimmed.charAt(trimmed.length() - 1);
            if ((start == '"' && end == '"') || (start == '\'' && end == '\'')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        String replaced = trimmed.replace('\\', '/');
        return collapseSlashes(replaced);
    }

    private String toForwardSlashes(Path path) {
        if (path == null) {
            return null;
        }
        return path.toString().replace('\\', '/');
    }
}
