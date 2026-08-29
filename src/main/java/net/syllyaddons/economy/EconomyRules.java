package net.syllyaddons.economy;

import java.util.Objects;
import net.syllyaddons.routing.RuleConfidence;

/** Economy semantics are versioned separately from observations and route selection. */
public record EconomyRules(
        String version,
        RuleConfidence confidence,
        boolean taxesCompoundPerRouteStep,
        boolean openingStorageSpentFirst,
        String basis) {
    public EconomyRules {
        version = Objects.requireNonNull(version, "version");
        if (version.isBlank()) throw new IllegalArgumentException("version must not be blank");
        confidence = Objects.requireNonNull(confidence, "confidence");
        basis = Objects.requireNonNull(basis, "basis");
    }

    public static EconomyRules research2026_08_29() {
        return new EconomyRules(
                "economy-research-2026-08-29.1",
                RuleConfidence.RESEARCH_ASSUMPTION,
                true,
                true,
                "Taxes are modelled as compounding loss per route step; opening HQ storage pays expenses before new production");
    }
}
