package net.syllyaddons.audit;

import java.util.Map;
import java.util.Objects;

/** A numeric claim with its exact inputs and formula retained for UI traceability. */
public record AuditCalculation(
        String label, String formula, Map<String, Double> inputs, double result, String unit) {
    public AuditCalculation {
        label = Objects.requireNonNull(label, "label").strip();
        formula = Objects.requireNonNull(formula, "formula").strip();
        inputs = Map.copyOf(Objects.requireNonNull(inputs, "inputs"));
        unit = unit == null ? "" : unit.strip();
        if (label.isEmpty() || formula.isEmpty() || !Double.isFinite(result)) {
            throw new IllegalArgumentException("Invalid audit calculation");
        }
        if (inputs.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Calculation inputs must be finite");
        }
    }
}
