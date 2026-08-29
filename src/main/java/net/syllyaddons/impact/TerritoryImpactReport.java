package net.syllyaddons.impact;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.RoutingMode;

public record TerritoryImpactReport(
        String removedTerritory,
        OwnerRelation ownerRelation,
        boolean headquartersRemoved,
        long sourceRevision,
        String cacheKey,
        Map<RoutingMode, RoutingModeImpact> modes,
        List<String> missingInputs) {
    public TerritoryImpactReport {
        removedTerritory = Objects.requireNonNull(removedTerritory, "removedTerritory").strip();
        ownerRelation = Objects.requireNonNull(ownerRelation, "ownerRelation");
        cacheKey = Objects.requireNonNull(cacheKey, "cacheKey").strip();
        modes = Map.copyOf(Objects.requireNonNull(modes, "modes"));
        missingInputs = List.copyOf(Objects.requireNonNull(missingInputs, "missingInputs"));
        if (removedTerritory.isEmpty() || cacheKey.isEmpty() || modes.isEmpty()) {
            throw new IllegalArgumentException("Invalid territory impact report");
        }
    }

    public ImpactSeverity maximumSeverity() {
        return modes.values().stream()
                .flatMap(mode -> java.util.stream.Stream.of(
                        mode.defensiveScore().severity(), mode.offensiveScore().severity()))
                .max(java.util.Comparator.naturalOrder())
                .orElse(ImpactSeverity.MINOR);
    }
}
