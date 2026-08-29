package net.syllyaddons.advisor;

import java.util.OptionalInt;
import java.util.OptionalLong;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.RouteResult;

public record AttackRouteEstimate(
        RoutingMode mode,
        RouteResult route,
        int estimatedTimerSeconds,
        long estimatedCostEmeralds,
        OptionalInt observedTimerSeconds,
        OptionalLong observedCostEmeralds) {
    public AttackRouteEstimate {
        java.util.Objects.requireNonNull(mode, "mode");
        java.util.Objects.requireNonNull(route, "route");
        observedTimerSeconds = observedTimerSeconds == null ? OptionalInt.empty() : observedTimerSeconds;
        observedCostEmeralds = observedCostEmeralds == null ? OptionalLong.empty() : observedCostEmeralds;
    }

    public int comparisonTimerSeconds() {
        return observedTimerSeconds.orElse(estimatedTimerSeconds);
    }

    public long comparisonCostEmeralds() {
        return observedCostEmeralds.orElse(estimatedCostEmeralds);
    }
}
