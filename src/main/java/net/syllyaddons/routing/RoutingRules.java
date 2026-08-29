package net.syllyaddons.routing;

import java.util.Objects;

public record RoutingRules(
        String version,
        double heuristicDistanceDivisor,
        long secondsPerHop,
        RuleConfidence algorithmConfidence,
        String basis) {
    public RoutingRules {
        version = Objects.requireNonNull(version, "version");
        if (version.isBlank()) throw new IllegalArgumentException("version must not be blank");
        if (!Double.isFinite(heuristicDistanceDivisor) || heuristicDistanceDivisor <= 0) {
            throw new IllegalArgumentException("heuristicDistanceDivisor must be positive");
        }
        if (secondsPerHop <= 0) throw new IllegalArgumentException("secondsPerHop must be positive");
        algorithmConfidence = Objects.requireNonNull(algorithmConfidence, "algorithmConfidence");
        basis = Objects.requireNonNull(basis, "basis");
    }

    public static RoutingRules research2026_08_29() {
        return new RoutingRules(
                "routing-research-2026-08-29.1",
                12_831.0,
                60,
                RuleConfidence.RESEARCH_ASSUMPTION,
                "Community algorithm report; connection-order equivalence and current server behavior unverified");
    }
}
