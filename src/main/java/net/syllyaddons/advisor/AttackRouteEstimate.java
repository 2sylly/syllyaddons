package net.syllyaddons.advisor;

import java.util.OptionalInt;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.RouteResult;

public record AttackRouteEstimate(
        RoutingMode mode,
        RouteResult route,
        int estimatedTimerSeconds,
        OptionalInt observedTimerSeconds) {
    public AttackRouteEstimate {
        java.util.Objects.requireNonNull(mode, "mode");
        java.util.Objects.requireNonNull(route, "route");
        observedTimerSeconds = observedTimerSeconds == null ? OptionalInt.empty() : observedTimerSeconds;
    }

    public int comparisonTimerSeconds() {
        return observedTimerSeconds.orElse(estimatedTimerSeconds);
    }

}
