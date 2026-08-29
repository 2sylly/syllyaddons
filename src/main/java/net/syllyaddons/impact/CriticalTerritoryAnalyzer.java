package net.syllyaddons.impact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.syllyaddons.routing.TerritoryGraph;

/** Computes territory separators between every source and HQ in one undirected low-link traversal. */
final class CriticalTerritoryAnalyzer {
    Map<String, Set<String>> analyze(TerritoryGraph graph, String headquarters, List<String> sources) {
        if (graph.node(headquarters) == null) return empty(sources);
        Map<String, Integer> discovered = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        int[] sequence = {0};
        dfs(graph, headquarters, discovered, low, parent, sequence);

        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String source : sources) {
            Set<String> separators = new LinkedHashSet<>();
            String child = source;
            while (parent.containsKey(child)) {
                String candidate = parent.get(child);
                if (!candidate.equals(headquarters)
                        && low.getOrDefault(child, Integer.MIN_VALUE)
                                >= discovered.getOrDefault(candidate, Integer.MAX_VALUE)) {
                    separators.add(candidate);
                }
                child = candidate;
            }
            result.put(source, Set.copyOf(separators));
        }
        return Map.copyOf(result);
    }

    private static void dfs(
            TerritoryGraph graph,
            String current,
            Map<String, Integer> discovered,
            Map<String, Integer> low,
            Map<String, String> parent,
            int[] sequence) {
        int discovery = sequence[0]++;
        discovered.put(current, discovery);
        low.put(current, discovery);
        for (String neighbor : graph.neighbors(current)) {
            if (!discovered.containsKey(neighbor)) {
                parent.put(neighbor, current);
                dfs(graph, neighbor, discovered, low, parent, sequence);
                low.put(current, Math.min(low.get(current), low.get(neighbor)));
            } else if (!neighbor.equals(parent.get(current))) {
                low.put(current, Math.min(low.get(current), discovered.get(neighbor)));
            }
        }
    }

    private static Map<String, Set<String>> empty(List<String> sources) {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        for (String source : new ArrayList<>(sources)) values.put(source, Set.of());
        return Map.copyOf(values);
    }
}
