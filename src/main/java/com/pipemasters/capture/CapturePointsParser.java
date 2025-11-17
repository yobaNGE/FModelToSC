package com.pipemasters.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipemasters.util.MainNameFormatter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CapturePointsParser {
    private final ObjectMapper objectMapper;

    public CapturePointsParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public CapturePoints parseCapturePoints(Path exportPath) throws IOException {
        JsonNode root = objectMapper.readTree(exportPath.toFile());
        return parseCapturePoints(root);
    }

    public CapturePoints parseCapturePoints(JsonNode root) {
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("FModel export is expected to be a JSON array of objects");
        }

        JsonNode initializerNode = findInitializerNode(root);
        JsonNode designOutgoingLinks = initializerNode.path("Properties").path("DesignOutgoingLinks");
        if (!designOutgoingLinks.isArray()) {
            throw new IllegalStateException("DesignOutgoingLinks array is missing in the initializer component");
        }

        List<RawLink> rawLinks = new ArrayList<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Set<String> allNodes = new LinkedHashSet<>();
        Set<String> incomingNodes = new HashSet<>();
        Set<String> outgoingNodes = new HashSet<>();

        for (int i = 0; i < designOutgoingLinks.size(); i++) {
            JsonNode linkNode = designOutgoingLinks.get(i);
            String nodeA = extractNodeName(linkNode.path("NodeA").path("ObjectName").asText());
            String nodeB = extractNodeName(linkNode.path("NodeB").path("ObjectName").asText());

            allNodes.add(nodeA);
            allNodes.add(nodeB);
            incomingNodes.add(nodeB);
            outgoingNodes.add(nodeA);

            adjacency.computeIfAbsent(nodeA, key -> new ArrayList<>()).add(nodeB);
            adjacency.putIfAbsent(nodeB, new ArrayList<>());

            rawLinks.add(new RawLink("Link" + i, nodeA, nodeB));
        }

        String startNode = allNodes.stream()
                .filter(node -> !incomingNodes.contains(node))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to determine graph start node"));

        String endNode = allNodes.stream()
                .filter(node -> !outgoingNodes.contains(node))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to determine graph end node"));

        List<List<String>> paths = findAllPaths(startNode, endNode, adjacency);
        if (paths.isEmpty()) {
            throw new IllegalStateException("No capture point paths could be derived from the graph definition");
        }

        paths.sort(Comparator.comparing(path -> path.stream()
                .map(this::toDisplayName)
                .collect(Collectors.joining("->"))));

        List<NodeLabel> pointsOrder = buildPointsOrder(paths);
        LinkedHashSet<String> mains = new LinkedHashSet<>();
        for (NodeLabel label : pointsOrder) {
            if (label.displayName() != null && label.displayName().endsWith(" Main")) {
                mains.add(label.rawName());
            }
        }

        List<String> mainsInOrder = new ArrayList<>(mains);
        Map<String, String> mainNameOverrides = MainNameFormatter.canonicalize(mainsInOrder);

        List<String> canonicalPointsOrder = pointsOrder.stream()
                .map(label -> applyMainOverride(label.rawName(), label.displayName(), mainNameOverrides))
                .toList();

        List<CaptureLink> canonicalLinks = rawLinks.stream()
                .map(link -> new CaptureLink(
                        link.name(),
                        applyMainOverride(link.nodeA(), toDisplayName(link.nodeA()), mainNameOverrides),
                        applyMainOverride(link.nodeB(), toDisplayName(link.nodeB()), mainNameOverrides)))
                .toList();

        List<String> canonicalMains = mainsInOrder.stream()
                .map(name -> applyMainOverride(name, toDisplayName(name), mainNameOverrides))
                .toList();

        CaptureClusters clusters = new CaptureClusters(
                canonicalLinks,
                canonicalPointsOrder,
                canonicalPointsOrder.size(),
                canonicalMains,
                mainNameOverrides
        );

        return new CapturePoints(
                "Invasion Random Graph",
                Map.of(),
                Map.of(),
                clusters,
                Map.of(),
                List.of(),
                Map.of()
        );
    }

    private JsonNode findInitializerNode(JsonNode root) {
        for (JsonNode node : root) {
            if ("SQGraphRAASInitializerComponent".equals(node.path("Type").asText())) {
                return node;
            }
        }
        throw new IllegalStateException("Unable to locate SQGraphRAASInitializerComponent in the export");
    }

    private String extractNodeName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("Object name is missing for graph node reference");
        }
        int lastDot = objectName.lastIndexOf('.');
        int lastQuote = objectName.lastIndexOf('\'');
        if (lastDot < 0 || lastQuote < 0 || lastQuote <= lastDot) {
            throw new IllegalArgumentException("Unexpected object name format: " + objectName);
        }
        return objectName.substring(lastDot + 1, lastQuote);
    }

    private String toDisplayName(String rawName) {
        if (rawName == null) {
            return null;
        }
        if (rawName.contains("Main")) {
            return MainNameFormatter.normalize(rawName);
        }
        return rawName;
    }

    private List<List<String>> findAllPaths(String start,
                                            String end,
                                            Map<String, List<String>> adjacency) {
        List<List<String>> paths = new ArrayList<>();
        Deque<String> currentPath = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        depthFirstSearch(start, end, adjacency, currentPath, visited, paths);
        return paths;
    }

    private void depthFirstSearch(String current,
                                  String target,
                                  Map<String, List<String>> adjacency,
                                  Deque<String> currentPath,
                                  Set<String> visited,
                                  List<List<String>> result) {
        currentPath.addLast(current);
        if (current.equals(target)) {
            result.add(new ArrayList<>(currentPath));
        } else {
            visited.add(current);
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (!visited.contains(next)) {
                    depthFirstSearch(next, target, adjacency, currentPath, visited, result);
                }
            }
            visited.remove(current);
        }
        currentPath.removeLast();
    }

    private List<NodeLabel> buildPointsOrder(List<List<String>> paths) {
        List<NodeLabel> order = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            List<String> path = paths.get(i);
            int limit = path.size();
            if (i < paths.size() - 1) {
                limit -= 1;
            }
            for (int j = 0; j < limit; j++) {
                String rawName = path.get(j);
                order.add(new NodeLabel(rawName, toDisplayName(rawName)));
            }
        }
        return order;
    }

    private String applyMainOverride(String rawName,
                                     String defaultDisplayName,
                                     Map<String, String> overrides) {
        if (rawName == null) {
            return defaultDisplayName;
        }
        if (overrides != null) {
            String override = overrides.get(rawName);
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        return defaultDisplayName;
    }

    private record NodeLabel(String rawName, String displayName) {
    }

    private record RawLink(String name, String nodeA, String nodeB) {
    }
}