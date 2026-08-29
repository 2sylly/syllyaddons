package net.syllyaddons.impact;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.syllyaddons.routing.RuleConfidence;

public record TerritoryRouteImpact(
        String sourceTerritory,
        Set<RouteChangeKind> changes,
        List<String> baselineRoute,
        List<String> simulatedRoute,
        long baselineDeliverySeconds,
        long simulatedDeliverySeconds,
        double baselineSelectionCost,
        double simulatedSelectionCost,
        List<String> newlyCriticalTerritories,
        RuleConfidence selectedRouteConfidence) {
    public TerritoryRouteImpact {
        sourceTerritory = Objects.requireNonNull(sourceTerritory, "sourceTerritory").strip();
        changes = Set.copyOf(Objects.requireNonNull(changes, "changes"));
        baselineRoute = List.copyOf(Objects.requireNonNull(baselineRoute, "baselineRoute"));
        simulatedRoute = List.copyOf(Objects.requireNonNull(simulatedRoute, "simulatedRoute"));
        newlyCriticalTerritories = List.copyOf(Objects.requireNonNull(newlyCriticalTerritories, "newlyCriticalTerritories"));
        selectedRouteConfidence = Objects.requireNonNull(selectedRouteConfidence, "selectedRouteConfidence");
        if (sourceTerritory.isEmpty() || changes.isEmpty()) throw new IllegalArgumentException("Invalid route impact");
    }

    public long deliveryDeltaSeconds() {
        return simulatedDeliverySeconds - baselineDeliverySeconds;
    }

    public boolean changed() {
        return !changes.equals(Set.of(RouteChangeKind.UNCHANGED));
    }
}
