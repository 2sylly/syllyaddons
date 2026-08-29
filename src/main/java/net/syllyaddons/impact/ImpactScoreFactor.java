package net.syllyaddons.impact;

import java.util.Objects;

public record ImpactScoreFactor(
        String label, String formula, double input, double weight, double contribution) {
    public ImpactScoreFactor {
        label = Objects.requireNonNull(label, "label").strip();
        formula = Objects.requireNonNull(formula, "formula").strip();
        if (label.isEmpty() || formula.isEmpty()
                || !Double.isFinite(input) || !Double.isFinite(weight) || !Double.isFinite(contribution)) {
            throw new IllegalArgumentException("Invalid score factor");
        }
    }
}
