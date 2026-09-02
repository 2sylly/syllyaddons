package net.syllyaddons.advisor;

import java.util.List;
import net.syllyaddons.domain.RoutingMode;

public record AttackRoutingAdvice(
        String target,
        String headquarters,
        AttackRouteEstimate cheapest,
        AttackRouteEstimate fastest,
        int timeSavedSeconds,
        AttackAdviceDecision decision,
        RoutingMode resolvedRoutingMode,
        boolean routingModeInferred,
        boolean routingObservationNeeded,
        List<String> diagnostics,
        long calculatedAtEpochMillis) {
    public AttackRoutingAdvice {
        target = target == null ? "" : target.strip();
        headquarters = headquarters == null ? "" : headquarters.strip();
        decision = java.util.Objects.requireNonNull(decision, "decision");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (calculatedAtEpochMillis < 0) throw new IllegalArgumentException("calculatedAtEpochMillis must be non-negative");
        if (routingModeInferred && resolvedRoutingMode == null) {
            throw new IllegalArgumentException("An inferred routing mode must be resolved");
        }
    }

    public boolean available() {
        return decision != AttackAdviceDecision.UNAVAILABLE && cheapest != null && fastest != null;
    }
}
