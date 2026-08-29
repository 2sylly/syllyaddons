package net.syllyaddons.routing;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.RoutingMode;

public record RouteResult(
        RoutingMode mode,
        String rulesVersion,
        List<String> path,
        List<RouteStep> steps,
        double selectionCost,
        long deliverySeconds,
        RuleConfidence confidence,
        List<RouteDiagnostic> diagnostics) {
    public RouteResult {
        mode = Objects.requireNonNull(mode, "mode");
        rulesVersion = Objects.requireNonNull(rulesVersion, "rulesVersion");
        path = List.copyOf(path);
        steps = List.copyOf(steps);
        confidence = Objects.requireNonNull(confidence, "confidence");
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean found() {
        return !path.isEmpty();
    }

    public boolean exact() {
        return found() && confidence.isExact() && diagnostics.isEmpty();
    }
}
