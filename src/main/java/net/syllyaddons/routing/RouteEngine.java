package net.syllyaddons.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import net.syllyaddons.domain.RoutingMode;

public final class RouteEngine {
    public RouteComparison compare(
            TerritoryGraph graph,
            String start,
            String goal,
            RoutingMode observedMode,
            RouteTaxPolicy taxPolicy,
            RoutingRules rules) {
        RouteResult cheapest = find(graph, start, goal, RoutingMode.CHEAPEST, taxPolicy, rules);
        RouteResult fastest = find(graph, start, goal, RoutingMode.FASTEST, taxPolicy, rules);
        if (observedMode == null) {
            return new RouteComparison(
                    cheapest,
                    fastest,
                    null,
                    null,
                    List.of(new RouteDiagnostic(
                            "UNKNOWN_ROUTING_MODE",
                            "Both candidates are shown because the active routing mode was not observed")));
        }
        return new RouteComparison(
                cheapest,
                fastest,
                observedMode,
                observedMode == RoutingMode.CHEAPEST ? cheapest : fastest,
                List.of());
    }

    public RouteResult find(
            TerritoryGraph graph,
            String start,
            String goal,
            RoutingMode mode,
            RouteTaxPolicy taxPolicy,
            RoutingRules rules) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(taxPolicy, "taxPolicy");
        Objects.requireNonNull(rules, "rules");
        if (graph.node(start) == null || graph.node(goal) == null) {
            return missingResult(mode, rules, "UNKNOWN_TERRITORY", "Route endpoint is absent from the graph");
        }

        List<String> path = mode == RoutingMode.FASTEST
                ? fastestPath(graph, start, goal)
                : cheapestPath(graph, start, goal, taxPolicy, rules);
        if (path.isEmpty()) {
            return missingResult(mode, rules, "DISCONNECTED", "No route connects the selected territories");
        }
        return describe(graph, path, mode, taxPolicy, rules);
    }

    private static List<String> fastestPath(TerritoryGraph graph, String start, String goal) {
        if (start.equals(goal)) return List.of(start);
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> previous = new HashMap<>();
        queue.addLast(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : graph.neighbors(current)) {
                if (!visited.add(next)) continue;
                previous.put(next, current);
                if (next.equals(goal)) return reconstruct(previous, goal);
                queue.addLast(next);
            }
        }
        return List.of();
    }

    private static List<String> cheapestPath(
            TerritoryGraph graph,
            String start,
            String goal,
            RouteTaxPolicy taxPolicy,
            RoutingRules rules) {
        if (start.equals(goal)) return List.of(start);

        record Candidate(String territory, double score, long sequence, double cost) {}
        PriorityQueue<Candidate> open = new PriorityQueue<>(Comparator
                .comparingDouble(Candidate::score)
                .thenComparingLong(Candidate::sequence));
        Map<String, Double> bestCost = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        long sequence = 0;
        bestCost.put(start, 0.0);
        open.add(new Candidate(start, heuristic(graph, start, goal, rules), sequence++, 0.0));

        while (!open.isEmpty()) {
            Candidate current = open.remove();
            if (current.cost() > bestCost.getOrDefault(current.territory(), Double.POSITIVE_INFINITY)) continue;
            if (current.territory().equals(goal)) return reconstruct(previous, goal);

            TerritoryNode from = graph.node(current.territory());
            for (String next : graph.neighbors(current.territory())) {
                TerritoryNode to = graph.node(next);
                TaxQuote tax = taxPolicy.quote(from, to);
                double tentative = current.cost() + 1.0 + tax.rate();
                double known = bestCost.getOrDefault(next, Double.POSITIVE_INFINITY);
                if (tentative < known) {
                    bestCost.put(next, tentative);
                    previous.put(next, current.territory());
                    open.add(new Candidate(
                            next,
                            tentative + heuristic(graph, next, goal, rules),
                            sequence++,
                            tentative));
                }
            }
        }
        return List.of();
    }

    private static double heuristic(TerritoryGraph graph, String current, String goal, RoutingRules rules) {
        TerritoryNode from = graph.node(current);
        TerritoryNode to = graph.node(goal);
        if (from == null || to == null || from.bounds() == null || to.bounds() == null) return 0;
        return Math.hypot(from.centerX() - to.centerX(), from.centerZ() - to.centerZ())
                / rules.heuristicDistanceDivisor();
    }

    private static List<String> reconstruct(Map<String, String> previous, String goal) {
        ArrayList<String> reversed = new ArrayList<>();
        String current = goal;
        reversed.add(current);
        while (previous.containsKey(current)) {
            current = previous.get(current);
            reversed.add(current);
        }
        return reversed.reversed();
    }

    private static RouteResult describe(
            TerritoryGraph graph,
            List<String> path,
            RoutingMode mode,
            RouteTaxPolicy taxPolicy,
            RoutingRules rules) {
        List<RouteStep> steps = new ArrayList<>();
        List<RouteDiagnostic> diagnostics = new ArrayList<>();
        RuleConfidence confidence = rules.algorithmConfidence();
        double selectionCost = 0;
        for (int index = 1; index < path.size(); index++) {
            TerritoryNode from = graph.node(path.get(index - 1));
            TerritoryNode to = graph.node(path.get(index));
            TaxQuote quote = taxPolicy.quote(from, to);
            confidence = RuleConfidence.weakest(confidence, quote.confidence());
            double stepCost = mode == RoutingMode.CHEAPEST ? 1.0 + quote.rate() : 1.0;
            selectionCost += stepCost;
            steps.add(new RouteStep(from.name(), to.name(), quote.rate(), stepCost, quote.confidence(), quote.basis()));
            if (quote.confidence() == RuleConfidence.UNKNOWN) {
                diagnostics.add(new RouteDiagnostic(
                        "UNKNOWN_TAX", "Tax from " + from.name() + " to " + to.name() + " was not observed"));
            }
        }
        if (!graph.diagnostics().isEmpty()) {
            confidence = RuleConfidence.weakest(confidence, RuleConfidence.RESEARCH_ASSUMPTION);
            diagnostics.add(new RouteDiagnostic(
                    "NORMALIZED_GRAPH",
                    graph.diagnostics().size() + " topology anomaly/anomalies were retained while normalizing the graph"));
        }
        if (!rules.algorithmConfidence().isExact()) {
            diagnostics.add(new RouteDiagnostic("UNVALIDATED_RULES", rules.basis()));
        }
        return new RouteResult(
                mode,
                rules.version(),
                path,
                steps,
                selectionCost,
                Math.multiplyExact((long) steps.size(), rules.secondsPerHop()),
                confidence,
                diagnostics);
    }

    private static RouteResult missingResult(
            RoutingMode mode, RoutingRules rules, String code, String message) {
        return new RouteResult(
                mode,
                rules.version(),
                List.of(),
                List.of(),
                0,
                0,
                RuleConfidence.UNKNOWN,
                List.of(new RouteDiagnostic(code, message)));
    }
}
