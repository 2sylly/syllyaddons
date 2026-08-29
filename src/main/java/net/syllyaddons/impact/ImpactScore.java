package net.syllyaddons.impact;

import java.util.List;
import java.util.Objects;

public record ImpactScore(
        double score,
        ImpactSeverity severity,
        ImpactCertainty certainty,
        List<ImpactScoreFactor> factors,
        List<String> missingInputs) {
    public ImpactScore {
        if (!Double.isFinite(score) || score < 0 || score > 100) throw new IllegalArgumentException("Invalid impact score");
        severity = Objects.requireNonNull(severity, "severity");
        certainty = Objects.requireNonNull(certainty, "certainty");
        factors = List.copyOf(Objects.requireNonNull(factors, "factors"));
        missingInputs = List.copyOf(Objects.requireNonNull(missingInputs, "missingInputs"));
    }
}
