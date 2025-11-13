package com.pipemasters.capture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

@JsonPropertyOrder({"type", "lanes", "points", "clusters", "hexs", "objectiveSpawnLocations", "destructionObject"})
public record CapturePoints(String type,
                            Map<String, Object> lanes,
                            Map<String, Object> points,
                            CaptureClusters clusters,
                            Map<String, Object> hexs,
                            List<Object> objectiveSpawnLocations,
                            Map<String, Object> destructionObject) {
}