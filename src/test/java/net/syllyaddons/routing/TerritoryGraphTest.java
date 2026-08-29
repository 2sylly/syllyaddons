package net.syllyaddons.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerritoryGraphTest {
    @Test
    void preservesDeclaredOrderAndReportsEveryNormalization() {
        TerritoryGraph graph = TerritoryGraph.from(List.of(
                node("A", "guild", "C", "B", "B", "A", "Missing"),
                node("B", "guild", "A"),
                node("C", "guild")));

        assertEquals(List.of("C", "B"), graph.neighbors("A"));
        assertEquals(List.of("A"), graph.neighbors("B"));
        assertEquals(List.of("A"), graph.neighbors("C"));
        assertTrue(graph.diagnostics().stream()
                .anyMatch(value -> value.type() == GraphDiagnosticType.DUPLICATE_LINK));
        assertTrue(graph.diagnostics().stream()
                .anyMatch(value -> value.type() == GraphDiagnosticType.SELF_LINK));
        assertTrue(graph.diagnostics().stream()
                .anyMatch(value -> value.type() == GraphDiagnosticType.UNKNOWN_LINK));
        assertTrue(graph.diagnostics().stream()
                .anyMatch(value -> value.type() == GraphDiagnosticType.ASYMMETRIC_LINK));
        assertThrows(UnsupportedOperationException.class, () -> graph.neighbors("A").add("D"));
    }

    @Test
    void sameOwnerConnectivityDoesNotTraverseForeignTerritories() {
        TerritoryGraph graph = TerritoryGraph.from(List.of(
                node("HQ", "ours", "Own A", "Enemy"),
                node("Own A", "ours", "HQ"),
                node("Enemy", "theirs", "HQ", "Own B"),
                node("Own B", "ours", "Enemy")));

        ReachabilityResult result = SameOwnerReachability.analyze(graph, "HQ");

        assertEquals("ours", result.ownerId());
        assertEquals(java.util.Set.of("HQ", "Own A"), result.reachable());
        assertEquals(java.util.Set.of("Own B"), result.unreachable());
        assertFalse(result.fullyConnected());
    }

    private static TerritoryNode node(String name, String owner, String... links) {
        return new TerritoryNode(name, owner, null, List.of(links));
    }
}
