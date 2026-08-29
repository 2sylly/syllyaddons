package net.syllyaddons.optimizer;

import java.util.List;
import java.util.Optional;

public record OptimizerView(
        OptimizerRunStatus status,
        long sourceRevision,
        long startedAtEpochMillis,
        long completedAtEpochMillis,
        Optional<OptimizationResult> result,
        List<String> diagnostics) {
    public OptimizerView {
        java.util.Objects.requireNonNull(status, "status");
        result = result == null ? Optional.empty() : result;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OptimizerView idle() {
        return new OptimizerView(OptimizerRunStatus.IDLE, 0, 0, 0, Optional.empty(), List.of());
    }
}
