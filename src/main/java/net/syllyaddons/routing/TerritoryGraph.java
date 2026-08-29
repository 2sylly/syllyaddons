package net.syllyaddons.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable normalized territory graph that preserves source connection order. */
public final class TerritoryGraph {
    private final Map<String, TerritoryNode> nodes;
    private final Map<String, List<String>> adjacency;
    private final List<GraphDiagnostic> diagnostics;

    private TerritoryGraph(
            Map<String, TerritoryNode> nodes,
            Map<String, List<String>> adjacency,
            List<GraphDiagnostic> diagnostics) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.adjacency = Collections.unmodifiableMap(new LinkedHashMap<>(adjacency));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static TerritoryGraph from(List<TerritoryNode> inputNodes) {
        Objects.requireNonNull(inputNodes, "inputNodes");
        Map<String, TerritoryNode> nodes = new LinkedHashMap<>();
        for (TerritoryNode node : inputNodes) {
            Objects.requireNonNull(node, "inputNodes contains null");
            if (nodes.putIfAbsent(node.name(), node) != null) {
                throw new IllegalArgumentException("Duplicate territory: " + node.name());
            }
        }

        Map<String, List<String>> mutableAdjacency = new LinkedHashMap<>();
        List<GraphDiagnostic> diagnostics = new ArrayList<>();
        for (TerritoryNode node : nodes.values()) {
            List<String> normalized = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (!node.linksKnown()) {
                diagnostics.add(new GraphDiagnostic(
                        GraphDiagnosticType.MISSING_LINK_DATA,
                        node.name(),
                        "",
                        "No connection list was observed for this territory"));
            }
            for (String rawLink : node.declaredLinks()) {
                if (rawLink == null || rawLink.isBlank()) continue;
                String link = rawLink.strip();
                if (!seen.add(link)) {
                    diagnostics.add(new GraphDiagnostic(
                            GraphDiagnosticType.DUPLICATE_LINK,
                            node.name(),
                            link,
                            "Duplicate connection was ignored after its first occurrence"));
                    continue;
                }
                if (link.equals(node.name())) {
                    diagnostics.add(new GraphDiagnostic(
                            GraphDiagnosticType.SELF_LINK,
                            node.name(),
                            link,
                            "Self-connection was excluded from routing"));
                    continue;
                }
                if (!nodes.containsKey(link)) {
                    diagnostics.add(new GraphDiagnostic(
                            GraphDiagnosticType.UNKNOWN_LINK,
                            node.name(),
                            link,
                            "Connection points to a territory absent from this snapshot"));
                    continue;
                }
                normalized.add(link);
            }
            mutableAdjacency.put(node.name(), normalized);
        }

        // The game topology is undirected. Append a missing reverse link only after every
        // source-declared connection, retaining both the anomaly and deterministic order.
        for (TerritoryNode node : nodes.values()) {
            for (String linked : List.copyOf(mutableAdjacency.get(node.name()))) {
                List<String> reverse = mutableAdjacency.get(linked);
                if (!reverse.contains(node.name())) {
                    diagnostics.add(new GraphDiagnostic(
                            GraphDiagnosticType.ASYMMETRIC_LINK,
                            node.name(),
                            linked,
                            "Missing reverse connection was inferred for normalized routing"));
                    reverse.add(node.name());
                }
            }
        }

        Map<String, List<String>> immutableAdjacency = new LinkedHashMap<>();
        mutableAdjacency.forEach((name, links) -> immutableAdjacency.put(name, List.copyOf(links)));
        return new TerritoryGraph(nodes, immutableAdjacency, diagnostics);
    }

    public Map<String, TerritoryNode> nodes() {
        return nodes;
    }

    public TerritoryNode node(String name) {
        return nodes.get(name);
    }

    public List<String> neighbors(String name) {
        return adjacency.getOrDefault(name, List.of());
    }

    public Map<String, List<String>> adjacency() {
        return adjacency;
    }

    public List<GraphDiagnostic> diagnostics() {
        return diagnostics;
    }
}
