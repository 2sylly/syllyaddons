package net.syllyaddons.advisor;

import java.util.List;

public record AttackRoutingAdvice(
        String target,
        String headquarters,
        AttackRouteEstimate cheapest,
        AttackRouteEstimate fastest,
        int timeSavedSeconds,
        long additionalCostEmeralds,
        AttackAdviceDecision decision,
        List<String> diagnostics,
        long calculatedAtEpochMillis) {
    public AttackRoutingAdvice {
        target = target == null ? "" : target.strip();
        headquarters = headquarters == null ? "" : headquarters.strip();
        decision = java.util.Objects.requireNonNull(decision, "decision");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (calculatedAtEpochMillis < 0) throw new IllegalArgumentException("calculatedAtEpochMillis must be non-negative");
    }

    public boolean available() {
        return decision != AttackAdviceDecision.UNAVAILABLE && cheapest != null && fastest != null;
    }
}
