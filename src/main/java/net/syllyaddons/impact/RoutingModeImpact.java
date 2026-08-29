package net.syllyaddons.impact;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.RouteDiagnostic;

public record RoutingModeImpact(
        RoutingMode mode,
        List<TerritoryRouteImpact> routeImpacts,
        Map<ResourceType, ResourceImpactDelta> resourceDeltas,
        ImpactCertainty topologyCertainty,
        ImpactCertainty selectedRouteCertainty,
        ImpactCertainty economyCertainty,
        ImpactScore defensiveScore,
        ImpactScore offensiveScore,
        List<RouteDiagnostic> diagnostics) {
    public RoutingModeImpact {
        mode = Objects.requireNonNull(mode, "mode");
        routeImpacts = List.copyOf(Objects.requireNonNull(routeImpacts, "routeImpacts"));
        resourceDeltas = Map.copyOf(Objects.requireNonNull(resourceDeltas, "resourceDeltas"));
        topologyCertainty = Objects.requireNonNull(topologyCertainty, "topologyCertainty");
        selectedRouteCertainty = Objects.requireNonNull(selectedRouteCertainty, "selectedRouteCertainty");
        economyCertainty = Objects.requireNonNull(economyCertainty, "economyCertainty");
        defensiveScore = Objects.requireNonNull(defensiveScore, "defensiveScore");
        offensiveScore = Objects.requireNonNull(offensiveScore, "offensiveScore");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
