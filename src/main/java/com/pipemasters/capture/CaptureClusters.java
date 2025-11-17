package com.pipemasters.capture;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

@JsonPropertyOrder({"links", "pointsOrder", "numberOfPoints", "listOfMains"})
public record CaptureClusters(List<CaptureLink> links,
                              List<String> pointsOrder,
                              int numberOfPoints,
                              List<String> listOfMains,
                              @JsonIgnore Map<String, String> mainNameOverrides) {
}