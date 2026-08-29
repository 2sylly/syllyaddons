package net.syllyaddons.optimizer;

import java.util.List;
import java.util.Optional;

public record OptimizationResult(
        OptimizationCandidate baseline,
        Optional<OptimizationCandidate> recommendation,
        Optional<OptimizationCandidate> bestEffort,
        boolean optimalityProven,
        boolean independentlyVerified,
        long evaluatedNodes,
        long elapsedMillis,
        OptimizationTermination termination,
        List<String> diagnostics) {
    public OptimizationResult {
        java.util.Objects.requireNonNull(baseline, "baseline");
        recommendation = recommendation == null ? Optional.empty() : recommendation;
        bestEffort = bestEffort == null ? Optional.empty() : bestEffort;
        java.util.Objects.requireNonNull(termination, "termination");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
