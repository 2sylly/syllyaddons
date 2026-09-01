package net.syllyaddons.advisor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;
import net.syllyaddons.routing.OwnerTaxPolicy;
import net.syllyaddons.routing.RouteEngine;
import net.syllyaddons.routing.RouteResult;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.TerritoryGraph;
import net.syllyaddons.snapshot.ObservedEconomyAnalyzer;

/** Pure comparison of the attack target under both routing modes. */
public final class AttackRoutingAdvisor {
    private static final int TIMER_TOLERANCE_SECONDS = 5;
    private final ObservedTerritoryGraphFactory graphFactory = new ObservedTerritoryGraphFactory();
    private final RouteEngine routeEngine = new RouteEngine();

    public AttackRoutingAdvice advise(
            ObservedState state,
            AttackMenuSnapshot menu,
            long nowEpochMillis) {
        java.util.Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(menu, "menu");
        List<String> diagnostics = new ArrayList<>(menu.diagnostics());
        if (!state.guild().isKnown()) return unavailable(
                menu.target(), "", diagnostics, "Current guild is unknown.", false, nowEpochMillis);
        if (!state.hqTerritory().isKnown()) return unavailable(
                menu.target(), "", diagnostics, "Guild headquarters is unknown.", false, nowEpochMillis);
        if (!menu.hasRequiredInputs()) return unavailable(
                menu.target(), state.hqTerritory().value(), diagnostics,
                "The displayed attack cost and timer are required.", false, nowEpochMillis);

        String hq = state.hqTerritory().value();
        String target = canonicalTarget(menu.target(), state);
        if (!state.territories().containsKey(target)) {
            return unavailable(target, hq, diagnostics,
                    "Attack target is absent from the observed territory graph.", false, nowEpochMillis);
        }
        TerritoryGraph graph = graphFactory.create(EcoSnapshot.from(state, nowEpochMillis));
        Set<String> ownerIds = ownerIds(state.guild().value());
        OwnerTaxPolicy policy = new OwnerTaxPolicy(ownerIds, ObservedEconomyAnalyzer.ASSUMED_FOREIGN_TAX_RATE);
        RoutingRules rules = RoutingRules.research2026_08_29();
        RouteResult cheapestRoute = routeEngine.find(graph, hq, target, RoutingMode.CHEAPEST, policy, rules);
        RouteResult fastestRoute = routeEngine.find(graph, hq, target, RoutingMode.FASTEST, policy, rules);
        if (!cheapestRoute.found() || !fastestRoute.found()) {
            return unavailable(target, hq, diagnostics,
                    "At least one routing mode cannot reach the target.", false, nowEpochMillis);
        }

        ModeResolution resolution = resolveMode(state, menu, hq, target, cheapestRoute, fastestRoute, diagnostics);
        if (resolution.mode() == null) {
            return unavailable(target, hq, diagnostics, resolution.reason(), resolution.needsObservation(), nowEpochMillis);
        }
        RoutingMode observedMode = resolution.mode();
        RouteResult observedRoute = observedMode == RoutingMode.CHEAPEST ? cheapestRoute : fastestRoute;
        if (!menu.observedRoute().isEmpty()) {
            List<String> displayedRoute = completeDisplayedRoute(menu.observedRoute(), hq, target);
            if (displayedRoute.isEmpty()) {
                diagnostics.add("Displayed route text was partial; only the timer consistency guard was applied.");
            } else if (!samePath(displayedRoute, observedRoute.path())) {
                return unavailable(target, hq, diagnostics,
                        "Displayed route does not match the local " + observedMode.name().toLowerCase() + " route.",
                        false, nowEpochMillis);
            }
        }
        int predictedCurrentTimer = attackTimerSeconds(observedRoute);
        if (Math.abs(menu.observedTimerSeconds().getAsInt() - predictedCurrentTimer) > TIMER_TOLERANCE_SECONDS) {
            return unavailable(target, hq, diagnostics,
                    "Displayed timer does not match the local route length; the rules need live validation.",
                    false, nowEpochMillis);
        }

        int ownedTerritories = (int) state.territories().values().stream()
                .filter(territory -> ownedBy(territory, state.guild().value()))
                .count();
        AttackRouteEstimate cheapest = estimate(
                RoutingMode.CHEAPEST, cheapestRoute, state, ownedTerritories,
                observedMode == RoutingMode.CHEAPEST ? menu.observedTimerSeconds() : OptionalInt.empty(),
                observedMode == RoutingMode.CHEAPEST ? menu.observedCostEmeralds() : OptionalLong.empty());
        AttackRouteEstimate fastest = estimate(
                RoutingMode.FASTEST, fastestRoute, state, ownedTerritories,
                observedMode == RoutingMode.FASTEST ? menu.observedTimerSeconds() : OptionalInt.empty(),
                observedMode == RoutingMode.FASTEST ? menu.observedCostEmeralds() : OptionalLong.empty());

        int timeSaved = Math.max(0, cheapest.comparisonTimerSeconds() - fastest.comparisonTimerSeconds());
        long additionalCost = fastest.comparisonCostEmeralds() - cheapest.comparisonCostEmeralds();
        AttackAdviceDecision decision = decide(timeSaved);
        diagnostics.add("Unobserved route costs use a 70% foreign-tax research estimate.");
        diagnostics.add("The current mode's cost and timer come from the displayed attack menu.");
        if (resolution.inferred()) {
            diagnostics.add("Current routing mode was inferred as " + displayName(observedMode)
                    + " from a unique attack timer/route match.");
        }
        return new AttackRoutingAdvice(
                target, hq, cheapest, fastest, timeSaved, additionalCost, decision,
                observedMode, resolution.inferred(), false, diagnostics, nowEpochMillis);
    }

    private static AttackRouteEstimate estimate(
            RoutingMode mode,
            RouteResult route,
            ObservedState state,
            int ownedTerritories,
            OptionalInt observedTimer,
            OptionalLong observedCost) {
        return new AttackRouteEstimate(
                mode,
                route,
                attackTimerSeconds(route),
                estimatedAttackCost(route.path(), state, ownedTerritories),
                observedTimer,
                observedCost);
    }

    static int attackTimerSeconds(RouteResult route) {
        return Math.multiplyExact(route.steps().size() + 1, 60);
    }

    static long estimatedAttackCost(List<String> path, ObservedState state, int ownedTerritories) {
        long base = switch (ownedTerritories) {
            case 0 -> 0;
            case 1 -> 200;
            case 2 -> 800;
            case 3 -> 2_000;
            default -> 4_000;
        };
        if (base == 0 || path.size() < 3) return base;
        double multiplier = 1.0;
        GuildIdentity guild = state.guild().value();
        // HQ and the target are exempt. Only intermediate foreign territories add estimated tax.
        for (int index = 1; index < path.size() - 1; index++) {
            TerritoryState territory = state.territories().get(path.get(index));
            if (territory != null && !ownedBy(territory, guild)) {
                multiplier *= 1.0 + ObservedEconomyAnalyzer.ASSUMED_FOREIGN_TAX_RATE;
            }
        }
        return (long) Math.ceil(base * multiplier);
    }

    static AttackAdviceDecision decide(int timeSaved) {
        return timeSaved > 0 ? AttackAdviceDecision.FASTEST_FASTER : AttackAdviceDecision.SAME_QUEUE_TIME;
    }

    private static AttackRoutingAdvice unavailable(
            String target,
            String hq,
            List<String> diagnostics,
            String reason,
            boolean routingObservationNeeded,
            long nowEpochMillis) {
        diagnostics.add(reason);
        return new AttackRoutingAdvice(
                target, hq, null, null, 0, 0, AttackAdviceDecision.UNAVAILABLE,
                null, false, routingObservationNeeded, diagnostics, nowEpochMillis);
    }

    private static ModeResolution resolveMode(
            ObservedState state,
            AttackMenuSnapshot menu,
            String hq,
            String target,
            RouteResult cheapest,
            RouteResult fastest,
            List<String> diagnostics) {
        boolean existingDerivedInference = state.routingMode().isKnown()
                && state.routingMode().evidence().kind() == net.syllyaddons.domain.EvidenceKind.DERIVED
                && state.routingMode().evidence().source().equals("attack-menu-timer");
        if (state.routingMode().isKnown() && !existingDerivedInference) {
            return new ModeResolution(state.routingMode().value(), false, false, "");
        }

        List<String> displayedRoute = completeDisplayedRoute(menu.observedRoute(), hq, target);
        if (!menu.observedRoute().isEmpty() && displayedRoute.isEmpty()) {
            diagnostics.add("Displayed route text was partial; routing inference used only the timer.");
        }
        boolean cheapestMatches = matches(menu, displayedRoute, cheapest);
        boolean fastestMatches = matches(menu, displayedRoute, fastest);
        if (cheapestMatches != fastestMatches) {
            return new ModeResolution(
                    cheapestMatches ? RoutingMode.CHEAPEST : RoutingMode.FASTEST, true, false, "");
        }
        if (!cheapestMatches) {
            return new ModeResolution(null, false, false,
                    "Displayed timer/route matches neither local routing mode; the rules need live validation.");
        }
        if (existingDerivedInference) {
            return new ModeResolution(state.routingMode().value(), false, false, "");
        }
        return new ModeResolution(null, false, true,
                "Current routing mode is ambiguous. Right-click this panel to open HQ management.");
    }

    private static boolean matches(AttackMenuSnapshot menu, List<String> displayedRoute, RouteResult route) {
        if (Math.abs(menu.observedTimerSeconds().getAsInt() - attackTimerSeconds(route)) > TIMER_TOLERANCE_SECONDS) {
            return false;
        }
        return displayedRoute.isEmpty() || samePath(displayedRoute, route.path());
    }

    private static String displayName(RoutingMode mode) {
        String lower = mode.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean samePath(List<String> observed, List<String> calculated) {
        if (observed.size() != calculated.size()) return false;
        for (int index = 0; index < observed.size(); index++) {
            if (!observed.get(index).equalsIgnoreCase(calculated.get(index))) return false;
        }
        return true;
    }

    private static List<String> completeDisplayedRoute(List<String> observed, String hq, String target) {
        if (observed.size() < 2) return List.of();
        if (observed.getFirst().equalsIgnoreCase(hq) && observed.getLast().equalsIgnoreCase(target)) {
            return observed;
        }
        if (observed.getFirst().equalsIgnoreCase(target) && observed.getLast().equalsIgnoreCase(hq)) {
            return observed.reversed();
        }
        return List.of();
    }

    private static String canonicalTarget(String target, ObservedState state) {
        return state.territories().keySet().stream()
                .filter(name -> name.equalsIgnoreCase(target))
                .findFirst()
                .orElse(target);
    }

    private static Set<String> ownerIds(GuildIdentity guild) {
        Set<String> ids = new HashSet<>();
        if (!guild.uuid().isBlank()) ids.add(guild.uuid());
        if (!guild.name().isBlank()) ids.add(guild.name());
        return ids;
    }

    private static boolean ownedBy(TerritoryState territory, GuildIdentity guild) {
        if (!territory.owner().isKnown()) return false;
        TerritoryOwner owner = territory.owner().value();
        return (!guild.uuid().isBlank() && guild.uuid().equals(owner.guildUuid()))
                || (!guild.name().isBlank() && guild.name().equals(owner.guildName()));
    }

    private record ModeResolution(RoutingMode mode, boolean inferred, boolean needsObservation, String reason) {}
}
