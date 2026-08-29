package net.syllyaddons.snapshot;

import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.ResourceType;

public record SnapshotComparison(
        long baselineRevision,
        long currentRevision,
        String baselineHq,
        String currentHq,
        List<TerritoryDelta> territoryDeltas,
        Map<ResourceType, ResourceTotalDelta> resourceDeltas,
        Map<ResourceType, EconomyResourceDelta> economyDeltas,
        boolean economyComparable) {
    public SnapshotComparison {
        territoryDeltas = List.copyOf(territoryDeltas);
        resourceDeltas = Map.copyOf(resourceDeltas);
        economyDeltas = Map.copyOf(economyDeltas);
    }
}
