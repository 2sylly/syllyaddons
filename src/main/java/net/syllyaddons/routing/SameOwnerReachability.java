package net.syllyaddons.routing;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Connectivity diagnostic only; routing may legally cross other owners. */
public final class SameOwnerReachability {
    private SameOwnerReachability() {}

    public static ReachabilityResult analyze(TerritoryGraph graph, String start) {
        Objects.requireNonNull(graph, "graph");
        TerritoryNode startNode = graph.node(start);
        if (startNode == null) throw new IllegalArgumentException("Unknown start territory: " + start);

        String owner = startNode.ownerId();
        Set<String> owned = new LinkedHashSet<>();
        graph.nodes().values().stream()
                .filter(node -> !owner.isBlank() && owner.equals(node.ownerId()))
                .forEach(node -> owned.add(node.name()));

        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (!owner.isBlank()) {
            reachable.add(start);
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : graph.neighbors(current)) {
                if (owned.contains(next) && reachable.add(next)) queue.addLast(next);
            }
        }

        Set<String> unreachable = new LinkedHashSet<>(owned);
        unreachable.removeAll(reachable);
        return new ReachabilityResult(owner, reachable, unreachable);
    }
}
