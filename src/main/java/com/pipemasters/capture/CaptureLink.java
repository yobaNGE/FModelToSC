package com.pipemasters.capture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"name", "nodeA", "nodeB"})
public record CaptureLink(String name, String nodeA, String nodeB) {
}