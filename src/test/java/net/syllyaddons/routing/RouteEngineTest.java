package net.syllyaddons.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.RoutingMode;
import org.junit.jupiter.api.Test;

class RouteEngineTest {
    private static final RoutingRules FIXTURE_RULES =
            new RoutingRules("fixture-1", 12_831, 60, RuleConfidence.EXPLICIT_INPUT, "synthetic fixture");
    private final RouteEngine engine = new RouteEngine();

    @Test
    void lineHasOneMinuteOfDeliveryTimePerHop() {
        TerritoryGraph graph = graph(
                node("A", "g", "B"),
                node("B", "g", "A", "C"),
                node("C", "g", "B"));

        RouteResult route = route(graph, "A", "C", RoutingMode.FASTEST, zeroTax());

        assertEquals(List.of("A", "B", "C"), route.path());
        assertEquals(120, route.deliverySeconds());
        assertEquals(2, route.selectionCost());
        assertTrue(route.exact());
    }

    @Test
    void equalCostForkUsesConnectionOrderWithoutLexicalTieBreak() {
        TerritoryGraph graph = graph(
                node("Start", "g", "Z first", "A second"),
                node("Z first", "g", "Start", "Goal"),
                node("A second", "g", "Start", "Goal"),
                node("Goal", "g", "Z first", "A second"));

        assertEquals(
                List.of("Start", "Z first", "Goal"),
                route(graph, "Start", "Goal", RoutingMode.FASTEST, zeroTax()).path());
        assertEquals(
                List.of("Start", "Z first", "Goal"),
                route(graph, "Start", "Goal", RoutingMode.CHEAPEST, zeroTax()).path());
    }

    @Test
    void cheapestCanPreferLongerUntaxedRouteWhileFastestUsesFewestHops() {
        TerritoryGraph graph = splitGraph();
        ExplicitTaxPolicy taxes = new ExplicitTaxPolicy(
                Map.of(
                        new RouteEdge("Start", "Short"), 0.8,
                        new RouteEdge("Short", "Goal"), 0.8),
                0.0);

        RouteResult fastest = route(graph, "Start", "Goal", RoutingMode.FASTEST, taxes);
        RouteResult cheapest = route(graph, "Start", "Goal", RoutingMode.CHEAPEST, taxes);

        assertEquals(List.of("Start", "Short", "Goal"), fastest.path());
        assertEquals(List.of("Start", "Long 1", "Long 2", "Goal"), cheapest.path());
        assertEquals(120, fastest.deliverySeconds());
        assertEquals(180, cheapest.deliverySeconds());
        assertEquals(3.0, cheapest.selectionCost());
    }

    @Test
    void changedTaxCanRerouteWithoutDisconnectingTheGraph() {
        TerritoryGraph graph = splitGraph();
        RouteTaxPolicy beforeLoss = new ExplicitTaxPolicy(Map.of(), 0.0);
        RouteTaxPolicy afterLoss = new ExplicitTaxPolicy(
                Map.of(
                        new RouteEdge("Start", "Short"), 0.8,
                        new RouteEdge("Short", "Goal"), 0.8),
                0.0);

        assertEquals(
                List.of("Start", "Short", "Goal"),
                route(graph, "Start", "Goal", RoutingMode.CHEAPEST, beforeLoss).path());
        assertEquals(
                List.of("Start", "Long 1", "Long 2", "Goal"),
                route(graph, "Start", "Goal", RoutingMode.CHEAPEST, afterLoss).path());
    }

    @Test
    void handlesCyclesAndDisconnectedBranches() {
        TerritoryGraph graph = graph(
                node("A", "g", "B", "C"),
                node("B", "g", "A", "C"),
                node("C", "g", "A", "B"),
                node("D", "g", "E"),
                node("E", "g", "D"));

        RouteResult missing = route(graph, "A", "E", RoutingMode.FASTEST, zeroTax());

        assertFalse(missing.found());
        assertTrue(missing.diagnostics().stream().anyMatch(value -> value.code().equals("DISCONNECTED")));
    }

    @Test
    void unobservedTaxAndResearchRulesCannotBeLabelledExact() {
        TerritoryGraph graph = graph(node("A", "g", "B"), node("B", "g", "A"));
        RouteResult result = engine.find(
                graph,
                "A",
                "B",
                RoutingMode.CHEAPEST,
                new ExplicitTaxPolicy(Map.of(), null),
                RoutingRules.research2026_08_29());

        assertFalse(result.exact());
        assertEquals(RuleConfidence.UNKNOWN, result.confidence());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("UNKNOWN_TAX")));
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("UNVALIDATED_RULES")));
    }

    @Test
    void unknownRoutingModeReturnsBothCandidatesWithoutSelectingOne() {
        TerritoryGraph graph = splitGraph();

        RouteComparison comparison = engine.compare(
                graph, "Start", "Goal", null, zeroTax(), FIXTURE_RULES);

        assertFalse(comparison.modeKnown());
        assertEquals(null, comparison.selected());
        assertTrue(comparison.cheapest().found());
        assertTrue(comparison.fastest().found());
        assertTrue(comparison.diagnostics().stream()
                .anyMatch(value -> value.code().equals("UNKNOWN_ROUTING_MODE")));
    }

    @Test
    void asymmetricInputIsUsableButNeverSilentlyExact() {
        TerritoryGraph graph = graph(node("A", "g", "B"), node("B", "g"));

        RouteResult route = route(graph, "B", "A", RoutingMode.FASTEST, zeroTax());

        assertEquals(List.of("B", "A"), route.path());
        assertFalse(route.exact());
        assertTrue(route.diagnostics().stream().anyMatch(value -> value.code().equals("NORMALIZED_GRAPH")));
    }

    private RouteResult route(
            TerritoryGraph graph, String start, String goal, RoutingMode mode, RouteTaxPolicy policy) {
        return engine.find(graph, start, goal, mode, policy, FIXTURE_RULES);
    }

    private static ExplicitTaxPolicy zeroTax() {
        return new ExplicitTaxPolicy(Map.of(), 0.0);
    }

    private static TerritoryGraph splitGraph() {
        return graph(
                node("Start", "g", "Short", "Long 1"),
                node("Short", "other", "Start", "Goal"),
                node("Long 1", "g", "Start", "Long 2"),
                node("Long 2", "g", "Long 1", "Goal"),
                node("Goal", "g", "Short", "Long 2"));
    }

    private static TerritoryGraph graph(TerritoryNode... nodes) {
        return TerritoryGraph.from(List.of(nodes));
    }

    private static TerritoryNode node(String name, String owner, String... links) {
        return new TerritoryNode(name, owner, null, List.of(links));
    }
}
