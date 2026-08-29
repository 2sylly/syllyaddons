package net.syllyaddons.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.ExplicitTaxPolicy;
import net.syllyaddons.routing.RouteEdge;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.RuleConfidence;
import net.syllyaddons.routing.TerritoryGraph;
import net.syllyaddons.routing.TerritoryNode;
import org.junit.jupiter.api.Test;

class EconomyEngineTest {
    private static final RoutingRules ROUTING_RULES =
            new RoutingRules("route-fixture", 12_831, 60, RuleConfidence.EXPLICIT_INPUT, "fixture");
    private static final EconomyRules ECONOMY_RULES =
            new EconomyRules("economy-fixture", RuleConfidence.EXPLICIT_INPUT, true, true, "fixture");
    private final EconomyEngine engine = new EconomyEngine();

    @Test
    void tracesCompoundingTaxFromProductionThroughEveryRouteStep() {
        TerritoryGraph graph = lineGraph();
        ExplicitTaxPolicy taxes = new ExplicitTaxPolicy(
                Map.of(new RouteEdge("Mine", "Middle"), 0.1, new RouteEdge("Middle", "HQ"), 0.1),
                0.0);
        EconomyInput input = input(
                graph,
                taxes,
                List.of(economy("Mine", Map.of(ResourceType.ORE, 10.0), Map.of())),
                Map.of(),
                Map.of(ResourceType.ORE, 100.0));

        EconomyResult result = engine.calculate(input);
        ResourceProvenance ore = result.provenance().getFirst();

        assertEquals(List.of("Mine", "Middle", "HQ"), ore.route());
        assertEquals(2, ore.taxSteps().size());
        assertEquals(1.0, ore.taxSteps().get(0).taxLoss(), 1.0e-9);
        assertEquals(0.9, ore.taxSteps().get(1).taxLoss(), 1.0e-9);
        assertEquals(8.1, ore.deliveredToHq(), 1.0e-9);
        assertEquals(1.9, result.summaries().get(ResourceType.ORE).taxLoss(), 1.0e-9);
        assertTrue(result.exact());
    }

    @Test
    void spendsOpeningStorageThenProductionAndCapsEndingStorage() {
        TerritoryGraph graph = TerritoryGraph.from(List.of(node("HQ", "g")));
        EconomyInput input = input(
                graph,
                new ExplicitTaxPolicy(Map.of(), 0.0),
                List.of(economy(
                        "HQ",
                        Map.of(ResourceType.EMERALDS, 10.0),
                        Map.of(ResourceType.EMERALDS, 8.0))),
                Map.of(ResourceType.EMERALDS, 3.0),
                Map.of(ResourceType.EMERALDS, 4.0));

        EconomyResult result = engine.calculate(input);
        ResourceEconomySummary emeralds = result.summaries().get(ResourceType.EMERALDS);

        assertEquals(8.0, emeralds.spent(), 1.0e-9);
        assertEquals(4.0, emeralds.endingStorage(), 1.0e-9);
        assertEquals(1.0, emeralds.overflowLoss(), 1.0e-9);
        assertEquals(0.0, emeralds.deficit(), 1.0e-9);
        assertEquals(3.0, result.provenance().get(0).spending().getFirst().amount(), 1.0e-9);
        assertEquals(5.0, result.provenance().get(1).spending().getFirst().amount(), 1.0e-9);
    }

    @Test
    void reportsResourceDeficitAndDisconnectedProductionSeparately() {
        TerritoryGraph graph = TerritoryGraph.from(List.of(
                node("HQ", "g"),
                node("Island", "g")));
        EconomyInput input = input(
                graph,
                new ExplicitTaxPolicy(Map.of(), 0.0),
                List.of(
                        economy("HQ", Map.of(ResourceType.WOOD, 2.0), Map.of(ResourceType.WOOD, 5.0)),
                        economy("Island", Map.of(ResourceType.WOOD, 4.0), Map.of())),
                Map.of(),
                Map.of(ResourceType.WOOD, 100.0));

        EconomyResult result = engine.calculate(input);
        ResourceEconomySummary wood = result.summaries().get(ResourceType.WOOD);

        assertEquals(3.0, wood.deficit(), 1.0e-9);
        assertEquals(4.0, wood.undeliveredProduction(), 1.0e-9);
        assertFalse(result.exact());
        assertTrue(result.provenance().stream()
                .filter(value -> value.sourceTerritory().equals("Island"))
                .allMatch(value -> value.route().isEmpty() && value.undelivered() == 4.0));
    }

    @Test
    void researchEconomySemanticsAreNeverPresentedAsExact() {
        TerritoryGraph graph = TerritoryGraph.from(List.of(node("HQ", "g")));
        EconomyInput input = new EconomyInput(
                graph,
                "HQ",
                RoutingMode.CHEAPEST,
                new ExplicitTaxPolicy(Map.of(), 0.0),
                ROUTING_RULES,
                EconomyRules.research2026_08_29(),
                List.of(economy("HQ", Map.of(ResourceType.FISH, 1.0), Map.of())),
                Map.of(),
                Map.of(ResourceType.FISH, 1.0));

        EconomyResult result = engine.calculate(input);

        assertFalse(result.exact());
        assertTrue(result.diagnostics().stream()
                .anyMatch(value -> value.code().equals("UNVALIDATED_ECONOMY_RULES")));
    }

    private static EconomyInput input(
            TerritoryGraph graph,
            ExplicitTaxPolicy taxes,
            List<TerritoryEconomyInput> territories,
            Map<ResourceType, Double> storage,
            Map<ResourceType, Double> limits) {
        return new EconomyInput(
                graph,
                "HQ",
                RoutingMode.CHEAPEST,
                taxes,
                ROUTING_RULES,
                ECONOMY_RULES,
                territories,
                storage,
                limits);
    }

    private static TerritoryEconomyInput economy(
            String territory,
            Map<ResourceType, Double> production,
            Map<ResourceType, Double> expenses) {
        return new TerritoryEconomyInput(territory, production, expenses);
    }

    private static TerritoryGraph lineGraph() {
        return TerritoryGraph.from(List.of(
                node("Mine", "g", "Middle"),
                node("Middle", "g", "Mine", "HQ"),
                node("HQ", "g", "Middle")));
    }

    private static TerritoryNode node(String name, String owner, String... links) {
        return new TerritoryNode(name, owner, null, List.of(links));
    }
}
