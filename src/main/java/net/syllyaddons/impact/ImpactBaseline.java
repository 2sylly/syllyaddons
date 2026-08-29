package net.syllyaddons.impact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.RouteResult;
import net.syllyaddons.routing.TerritoryGraph;
import net.syllyaddons.snapshot.SnapshotPayload;

public record ImpactBaseline(
        String cacheKey,
        long builtAtEpochMillis,
        ObservedState observation,
        TerritoryGraph graph,
        List<RoutingMode> modes,
        List<String> ownedTerritories,
        Map<RoutingMode, SnapshotPayload> economyByMode,
        Map<RoutingMode, Map<String, RouteResult>> routesByMode,
        Map<String, Set<String>> criticalTerritoriesBySource,
        ImpactCertainty topologyCertainty) {
    public ImpactBaseline {
        cacheKey = Objects.requireNonNull(cacheKey, "cacheKey").strip();
        observation = Objects.requireNonNull(observation, "observation");
        graph = Objects.requireNonNull(graph, "graph");
        modes = List.copyOf(Objects.requireNonNull(modes, "modes"));
        ownedTerritories = List.copyOf(Objects.requireNonNull(ownedTerritories, "ownedTerritories"));
        economyByMode = Map.copyOf(Objects.requireNonNull(economyByMode, "economyByMode"));
        Map<RoutingMode, Map<String, RouteResult>> routeCopy = new LinkedHashMap<>();
        Objects.requireNonNull(routesByMode, "routesByMode")
                .forEach((mode, routes) -> routeCopy.put(mode, Map.copyOf(routes)));
        routesByMode = Map.copyOf(routeCopy);
        Map<String, Set<String>> criticalCopy = new LinkedHashMap<>();
        Objects.requireNonNull(criticalTerritoriesBySource, "criticalTerritoriesBySource")
                .forEach((source, critical) -> criticalCopy.put(source, Set.copyOf(critical)));
        criticalTerritoriesBySource = Map.copyOf(criticalCopy);
        topologyCertainty = Objects.requireNonNull(topologyCertainty, "topologyCertainty");
        if (cacheKey.isEmpty() || builtAtEpochMillis < 0 || modes.isEmpty()) {
            throw new IllegalArgumentException("Invalid impact baseline");
        }
    }
}
