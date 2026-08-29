package net.syllyaddons.routing;

import java.util.Objects;

public record TaxQuote(double rate, RuleConfidence confidence, String basis) {
    public TaxQuote {
        if (!Double.isFinite(rate) || rate < 0 || rate > 1) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 1");
        }
        confidence = Objects.requireNonNull(confidence, "confidence");
        basis = Objects.requireNonNull(basis, "basis");
    }
}
