package com.pipemasters.capture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"links", "pointsOrder", "numberOfPoints", "listOfMains"})
public record CaptureClusters(List<CaptureLink> links,
                              List<String> pointsOrder,
                              int numberOfPoints,
                              List<String> listOfMains) {
}