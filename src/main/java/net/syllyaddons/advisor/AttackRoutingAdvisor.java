package net.syllyaddons.advisor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;
import net.syllyaddons.routing.OwnerTaxPolicy;
import net.syllyaddons.routing.RouteEngine;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RouteResult;
import net.syllyaddons.routing.RouteStep;
import net.syllyaddons.routing.RuleConfidence;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.RouteTaxPolicy;
import net.syllyaddons.routing.TaxQuote;
import net.syllyaddons.routing.TerritoryNode;
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
                "The displayed queue timer is required.", false, nowEpochMillis);

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
        List<String> displayedRoute = completeDisplayedRoute(menu.observedRoute(), hq, target);
        RouteResult displayedRouteResult = displayedRoute.isEmpty()
                ? null
                : observedRoute(observedMode, displayedRoute, graph, policy, rules);
        if (!menu.observedRoute().isEmpty()) {
            if (displayedRoute.isEmpty()) {
                diagnostics.add("Displayed route text was partial; only the timer consistency guard was applied.");
            } else if (Math.abs(menu.observedTimerSeconds().getAsInt()
                    - attackTimerSeconds(displayedRouteResult)) > TIMER_TOLERANCE_SECONDS) {
                return unavailable(target, hq, diagnostics,
                        "Displayed route length does not match its timer; live evidence is inconsistent.",
                        false, nowEpochMillis);
            }
        }
        int displayedTimer = menu.observedTimerSeconds().getAsInt();
        int fastestTimer = attackTimerSeconds(fastestRoute);
        if (displayedTimer < fastestTimer - TIMER_TOLERANCE_SECONDS) {
            return unavailable(target, hq, diagnostics,
                    "Displayed timer is shorter than the local shortest path; topology needs a fresh live capture.",
                    false, nowEpochMillis);
        }
        if (observedMode == RoutingMode.FASTEST
                && Math.abs(displayedTimer - fastestTimer) > TIMER_TOLERANCE_SECONDS) {
            return unavailable(target, hq, diagnostics,
                    "Displayed Fastest timer does not match the local shortest path.", false, nowEpochMillis);
        }

        RouteResult observedRoute = displayedRouteResult != null
                ? displayedRouteResult
                : observedMode == RoutingMode.CHEAPEST ? cheapestRoute : fastestRoute;

        AttackRouteEstimate cheapest = estimate(
                RoutingMode.CHEAPEST, observedMode == RoutingMode.CHEAPEST ? observedRoute : cheapestRoute,
                observedMode == RoutingMode.CHEAPEST ? menu.observedTimerSeconds() : OptionalInt.empty());
        AttackRouteEstimate fastest = estimate(
                RoutingMode.FASTEST, observedMode == RoutingMode.FASTEST ? observedRoute : fastestRoute,
                observedMode == RoutingMode.FASTEST ? menu.observedTimerSeconds() : OptionalInt.empty());

        int timeSaved = Math.max(0, cheapest.comparisonTimerSeconds() - fastest.comparisonTimerSeconds());
        AttackAdviceDecision decision = decide(timeSaved);
        diagnostics.add("The current mode's queue time comes from the displayed attack menu.");
        if (observedMode == RoutingMode.CHEAPEST
                && Math.abs(displayedTimer - attackTimerSeconds(cheapestRoute)) > TIMER_TOLERANCE_SECONDS) {
            diagnostics.add("Displayed Cheapest timing replaced the fallback-tax A* estimate.");
        }
        if (resolution.inferred()) {
            diagnostics.add("Current routing mode was inferred as " + displayName(observedMode)
                    + " from a unique attack timer/route match.");
        }
        return new AttackRoutingAdvice(
                target, hq, cheapest, fastest, timeSaved, decision,
                observedMode, resolution.inferred(), false, diagnostics, nowEpochMillis);
    }

    private static AttackRouteEstimate estimate(
            RoutingMode mode,
            RouteResult route,
            OptionalInt observedTimer) {
        return new AttackRouteEstimate(
                mode,
                route,
                attackTimerSeconds(route),
                observedTimer);
    }

    private static RouteResult observedRoute(
            RoutingMode mode,
            List<String> path,
            TerritoryGraph graph,
            RouteTaxPolicy taxPolicy,
            RoutingRules rules) {
        List<RouteStep> steps = new ArrayList<>();
        double selectionCost = 0;
        for (int index = 1; index < path.size(); index++) {
            TerritoryNode from = graph.node(path.get(index - 1));
            TerritoryNode to = graph.node(path.get(index));
            TaxQuote quote = from == null || to == null
                    ? new TaxQuote(0, RuleConfidence.UNKNOWN, "Displayed route node is absent from the local graph")
                    : taxPolicy.quote(from, to);
            double stepCost = mode == RoutingMode.CHEAPEST ? 1.0 + quote.rate() : 1.0;
            selectionCost += stepCost;
            steps.add(new RouteStep(
                    path.get(index - 1), path.get(index), quote.rate(), stepCost,
                    quote.confidence(), quote.basis()));
        }
        return new RouteResult(
                mode,
                rules.version(),
                path,
                steps,
                selectionCost,
                Math.multiplyExact((long) steps.size(), rules.secondsPerHop()),
                RuleConfidence.RESEARCH_ASSUMPTION,
                List.of(new RouteDiagnostic(
                        "OBSERVED_ATTACK_ROUTE",
                        "Route order and timer were read from the open attack menu")));
    }

    static int attackTimerSeconds(RouteResult route) {
        return Math.multiplyExact(route.steps().size() + 1, 60);
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
                target, hq, null, null, 0, AttackAdviceDecision.UNAVAILABLE,
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
        int displayedTimer = menu.observedTimerSeconds().getAsInt();
        int fastestTimer = attackTimerSeconds(fastest);
        if (displayedTimer > fastestTimer + TIMER_TOLERANCE_SECONDS) {
            diagnostics.add("Displayed queue is longer than the local shortest path, uniquely identifying Cheapest.");
            return new ModeResolution(RoutingMode.CHEAPEST, true, false, "");
        }
        if (!cheapestMatches) {
            return new ModeResolution(null, false, true,
                    "Local route estimates disagree. Right-click this panel to open HQ management.");
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

    private record ModeResolution(RoutingMode mode, boolean inferred, boolean needsObservation, String reason) {}
}
