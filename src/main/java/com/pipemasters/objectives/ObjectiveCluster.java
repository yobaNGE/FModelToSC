package com.pipemasters.objectives;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"name", "pointPosition", "avgLocation", "points"})
public record ObjectiveCluster(String name,
                               Integer pointPosition,
                               ObjectiveLocation avgLocation,
                               List<ObjectivePoint> points) implements Objective {
}