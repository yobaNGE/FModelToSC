package com.pipemasters.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class AssetResolver {
    private final Path rootDir;
    private final boolean rootIsSettingsDir;
    private final String modName;
    private final List<Path> searchRoots;

    public AssetResolver(Path rootDir) {
        this.rootDir = rootDir;
        Path fileName = rootDir != null ? rootDir.getFileName() : null;
        this.rootIsSettingsDir = fileName != null && "Settings".equalsIgnoreCase(fileName.toString());
        this.modName = determineModName(rootDir);
        this.searchRoots = buildSearchRoots(rootDir);
    }

    public Path resolve(String objectPath) {
        if (objectPath == null || objectPath.isBlank() || rootDir == null) {
            return null;
        }
        String normalized = objectPath.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int quote = normalized.indexOf('\'');
        if (quote >= 0) {
            normalized = normalized.substring(0, quote);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(0, dot);
        }
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        List<String> segments = split(normalized);
        if (segments.isEmpty()) {
            return null;
        }

        boolean preferBaseGame = !segments.isEmpty() && equalsIgnoreCase(segments.get(0), "Game");
        List<List<String>> candidates = buildCandidates(segments);
        Path fallback = null;
        List<Path> searchOrder = searchRoots;
        if (preferBaseGame) {
            searchOrder = prioritizeBaseGameRoots(searchRoots);
        }
        for (Path searchRoot : searchOrder) {
            for (List<String> candidate : candidates) {
                Path relative = toPath(candidate);
                if (relative == null || relative.getNameCount() == 0) {
                    continue;
                }
                Path resolved = searchRoot.resolve(relative);
                Path candidateFile = appendJsonExtension(resolved);
                if (candidateFile == null) {
                    continue;
                }
                Path existing = findExistingCandidate(candidateFile);
                if (existing != null) {
                    if (isBetterFallback(existing, fallback)) {
                        fallback = existing;
                    }
                    return existing;
                }
                if (isBetterFallback(candidateFile, fallback)) {
                    fallback = candidateFile;
                }
            }
        }
        return fallback;
    }

    private String determineModName(Path rootDir) {
        Path current = rootDir;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "Content".equalsIgnoreCase(name.toString())) {
                Path parent = current.getParent();
                if (parent != null && parent.getFileName() != null) {
                    return parent.getFileName().toString();
                }
                break;
            }
            current = current.getParent();
        }
        if (rootDir != null) {
            Path fileName = rootDir.getFileName();
            if (fileName != null && !"Content".equalsIgnoreCase(fileName.toString())) {
                return fileName.toString();
            }
        }
        return null;
    }

    private List<Path> buildSearchRoots(Path rootDir) {
        List<Path> roots = new ArrayList<>();
        if (rootDir == null) {
            return roots;
        }
        Set<Path> seen = new LinkedHashSet<>();
        Path current = rootDir.toAbsolutePath().normalize();
        while (current != null) {
            addIfDirectory(seen, roots, current);
            addIfDirectory(seen, roots, current.resolve("Content"));

            Path pluginsDir = current.resolve("Plugins");
            if (Files.isDirectory(pluginsDir)) {
                addIfDirectory(seen, roots, pluginsDir);

                Path modsDir = pluginsDir.resolve("Mods");
                if (Files.isDirectory(modsDir)) {
                    addIfDirectory(seen, roots, modsDir);

                    if (modName != null && !modName.isBlank()) {
                        Path modDir = modsDir.resolve(modName);
                        addIfDirectory(seen, roots, modDir);
                        addIfDirectory(seen, roots, modDir.resolve("Content"));
                    }

                    try (Stream<Path> mods = Files.list(modsDir)) {
                        mods.filter(Files::isDirectory)
                                .forEach(mod -> {
                                    addIfDirectory(seen, roots, mod);
                                    addIfDirectory(seen, roots, mod.resolve("Content"));
                                });
                    } catch (IOException ignored) {
                        // Ignore directory traversal issues and fall back to already discovered roots.
                    }
                }
            }

            current = current.getParent();
        }
        return roots;
    }

    private void addIfDirectory(Set<Path> seen, List<Path> roots, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized) && seen.add(normalized)) {
            roots.add(normalized);
        }
    }

    private List<Path> prioritizeBaseGameRoots(List<Path> roots) {
        List<Path> prioritized = new ArrayList<>(roots.size());
        List<Path> mods = new ArrayList<>();
        for (Path root : roots) {
            if (isModPath(root)) {
                mods.add(root);
            } else {
                prioritized.add(root);
            }
        }
        prioritized.addAll(mods);
        return prioritized;
    }

    private boolean isModPath(Path path) {
        if (path == null) {
            return false;
        }
        for (Path segment : path) {
            if (equalsIgnoreCase(segment.toString(), "Mods")) {
                return true;
            }
        }
        return false;
    }

    private boolean isBetterFallback(Path candidate, Path current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }

        boolean candidateIsMod = isModPath(candidate);
        boolean currentIsMod = isModPath(current);
        if (candidateIsMod != currentIsMod) {
            return !candidateIsMod;
        }

        boolean candidateHasContent = hasPathSegment(candidate, "Content");
        boolean currentHasContent = hasPathSegment(current, "Content");
        if (candidateHasContent != currentHasContent) {
            return candidateHasContent;
        }

        int candidateDepth = candidate.getNameCount();
        int currentDepth = current.getNameCount();
        if (candidateDepth != currentDepth) {
            return candidateDepth < currentDepth;
        }

        return false;
    }

    private boolean hasPathSegment(Path path, String segmentName) {
        if (path == null || segmentName == null || segmentName.isBlank()) {
            return false;
        }
        for (Path segment : path) {
            if (equalsIgnoreCase(segmentName, segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private List<String> split(String normalized) {
        String[] raw = normalized.split("/");
        List<String> result = new ArrayList<>(raw.length);
        for (String segment : raw) {
            if (segment == null) {
                continue;
            }
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<List<String>> buildCandidates(List<String> segments) {
        LinkedHashSet<List<String>> candidates = new LinkedHashSet<>();
        if (segments.isEmpty()) {
            return List.of();
        }
        Deque<List<String>> queue = new ArrayDeque<>();
        queue.add(new ArrayList<>(segments));

        List<String> prefixes = buildPrefixes();

        while (!queue.isEmpty()) {
            List<String> current = queue.removeFirst();
            if (current == null || current.isEmpty()) {
                continue;
            }
            List<String> normalized = normalizeCandidate(current);
            if (normalized.isEmpty()) {
                continue;
            }
            List<String> key = List.copyOf(normalized);
            if (!candidates.add(key)) {
                continue;
            }
            String first = current.get(0);
            for (String prefix : prefixes) {
                if (equalsIgnoreCase(first, prefix) && current.size() > 1) {
                    queue.addLast(new ArrayList<>(current.subList(1, current.size())));
                }
            }
        }

        augmentWithModPrefixes(candidates);

        return new ArrayList<>(candidates);

    }

    private List<String> normalizeCandidate(List<String> candidate) {
        List<String> normalized = new ArrayList<>(candidate.size());
        for (String segment : candidate) {
            if (segment == null) {
                continue;
            }
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private void augmentWithModPrefixes(Set<List<String>> candidates) {
        if (modName == null || modName.isBlank() || candidates.isEmpty()) {
            return;
        }

        List<List<String>> snapshot = new ArrayList<>(candidates);
        List<String> modPrefix = List.of(modName);
        List<String> modContentPrefix = List.of(modName, "Content");
        List<String> pluginModPrefix = List.of("Plugins", "Mods", modName);
        List<String> pluginModContentPrefix = List.of("Plugins", "Mods", modName, "Content");

        for (List<String> candidate : snapshot) {
            if (!candidate.isEmpty()) {
                if (!startsWith(candidate, modName)) {
                    addCandidate(candidates, concat(modPrefix, candidate));
                }
                if (!startsWith(candidate, modName, "Content")) {
                    addCandidate(candidates, concat(modContentPrefix, candidate));
                }
                if (!startsWith(candidate, "Plugins", "Mods", modName)) {
                    addCandidate(candidates, concat(pluginModPrefix, candidate));
                }
                if (!startsWith(candidate, "Plugins", "Mods", modName, "Content")) {
                    addCandidate(candidates, concat(pluginModContentPrefix, candidate));
                }
            }
        }
    }

    private void addCandidate(Set<List<String>> candidates, List<String> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        List<String> normalized = normalizeCandidate(candidate);
        if (normalized.isEmpty()) {
            return;
        }
        candidates.add(List.copyOf(normalized));
    }

    private List<String> concat(List<String> prefix, List<String> suffix) {
        List<String> combined = new ArrayList<>(prefix.size() + suffix.size());
        combined.addAll(prefix);
        combined.addAll(suffix);
        return combined;
    }

    private boolean startsWith(List<String> candidate, String... prefix) {
        if (candidate.size() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!equalsIgnoreCase(candidate.get(i), prefix[i])) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildPrefixes() {
        List<String> prefixes = new ArrayList<>();
        prefixes.add("Game");
        prefixes.add("Content");
        prefixes.add("Plugins");
        prefixes.add("Mods");
        prefixes.add("SquadGame");
        prefixes.add("Steel_Division");
        if (modName != null && !modName.isBlank()) {
            prefixes.add(modName);
        }
        if (rootIsSettingsDir) {
            prefixes.add("Settings");
        }
        return prefixes;
    }

    private boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null
                && first.length() == second.length()
                && first.toLowerCase(Locale.ROOT).equals(second.toLowerCase(Locale.ROOT));
    }

    private Path toPath(List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        Path path = Path.of(segments.get(0));
        for (int i = 1; i < segments.size(); i++) {
            path = path.resolve(segments.get(i));
        }
        return path;
    }

    private Path appendJsonExtension(Path path) {
        if (path == null) {
            return null;
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        return path.resolveSibling(fileName + ".json");
    }

    private Path findExistingCandidate(Path candidateFile) {
        if (candidateFile == null) {
            return null;
        }
        if (Files.exists(candidateFile)) {
            return candidateFile;
        }
        Path parent = candidateFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        Path candidateName = candidateFile.getFileName();
        if (candidateName == null) {
            return null;
        }
        String target = candidateName.toString().toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.list(parent)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        Path file = path.getFileName();
                        return file != null
                                && file.toString().toLowerCase(Locale.ROOT).endsWith(target);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
