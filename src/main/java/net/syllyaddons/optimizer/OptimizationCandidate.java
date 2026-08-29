package net.syllyaddons.optimizer;

import java.util.List;
import java.util.Map;
import net.syllyaddons.economy.EconomyResult;

public record OptimizationCandidate(
        Map<UpgradeCoordinate, Integer> levels,
        EconomyResult economy,
        OptimizationMetrics metrics,
        List<UpgradeChange> changes,
        boolean feasible,
        List<String> violations) {
    public OptimizationCandidate {
        levels = Map.copyOf(levels);
        java.util.Objects.requireNonNull(economy, "economy");
        java.util.Objects.requireNonNull(metrics, "metrics");
        changes = List.copyOf(changes);
        violations = List.copyOf(violations);
    }
}
