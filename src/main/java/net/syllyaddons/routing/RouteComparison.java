package net.syllyaddons.routing;

import java.util.List;
import net.syllyaddons.domain.RoutingMode;

public record RouteComparison(
        RouteResult cheapest,
        RouteResult fastest,
        RoutingMode observedMode,
        RouteResult selected,
        List<RouteDiagnostic> diagnostics) {
    public RouteComparison {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean modeKnown() {
        return observedMode != null;
    }
}
