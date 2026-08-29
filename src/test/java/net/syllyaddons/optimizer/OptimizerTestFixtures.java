package net.syllyaddons.optimizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.economy.EconomyInput;
import net.syllyaddons.economy.EconomyRules;
import net.syllyaddons.economy.TerritoryEconomyInput;
import net.syllyaddons.economy.UpgradeCatalog;
import net.syllyaddons.routing.ExplicitTaxPolicy;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.RuleConfidence;
import net.syllyaddons.routing.TerritoryGraph;
import net.syllyaddons.routing.TerritoryNode;

final class OptimizerTestFixtures {
    private static final RoutingRules ROUTING =
            new RoutingRules("optimizer-fixture", 12_831, 60, RuleConfidence.EXPLICIT_INPUT, "fixture");
    private static final EconomyRules ECONOMY = new EconomyRules(
            "optimizer-fixture", RuleConfidence.EXPLICIT_INPUT, true, true, "fixture");

    private OptimizerTestFixtures() {}

    static OptimizationModel oneVariable() {
        UpgradeCoordinate coordinate = new UpgradeCoordinate("HQ", "EFFICIENT_RESOURCES");
        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("DAMAGE", 5);
        levels.put(coordinate.upgradeKey(), 2);
        return model(
                Map.of("HQ", Map.copyOf(levels)),
                List.of(new UpgradeVariable(coordinate, 0, 2)),
                Map.of(ResourceType.ORE, 200.0));
    }

    static OptimizationModel manyVariables() {
        Map<String, Map<String, Integer>> upgrades = new LinkedHashMap<>();
        List<UpgradeVariable> variables = new ArrayList<>();
        List<TerritoryNode> nodes = new ArrayList<>();
        List<TerritoryEconomyInput> inputs = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            String name = index == 0 ? "HQ" : "T" + index;
            String previous = index == 0 ? "" : index == 1 ? "HQ" : "T" + (index - 1);
            String next = index == 5 ? "" : "T" + (index + 1);
            List<String> links = java.util.stream.Stream.of(previous, next).filter(value -> !value.isEmpty()).toList();
            nodes.add(new TerritoryNode(name, "guild", null, links));
            Map<String, Integer> levels = Map.of("EFFICIENT_RESOURCES", 3);
            upgrades.put(name, levels);
            variables.add(new UpgradeVariable(new UpgradeCoordinate(name, "EFFICIENT_RESOURCES"), 0, 3));
            inputs.add(new TerritoryEconomyInput(
                    name,
                    Map.of(ResourceType.ORE, 250.0),
                    UpgradeCatalog.expensesPerHour(levels)));
        }
        return new OptimizationModel(
                input(TerritoryGraph.from(nodes), inputs, Map.of(ResourceType.ORE, 250.0)),
                upgrades,
                variables);
    }

    private static OptimizationModel model(
            Map<String, Map<String, Integer>> upgrades,
            List<UpgradeVariable> variables,
            Map<ResourceType, Double> production) {
        Map<String, Integer> hq = upgrades.get("HQ");
        TerritoryEconomyInput territory = new TerritoryEconomyInput(
                "HQ", production, UpgradeCatalog.expensesPerHour(hq));
        TerritoryGraph graph = TerritoryGraph.from(List.of(new TerritoryNode("HQ", "guild", null, List.of())));
        return new OptimizationModel(
                input(graph, List.of(territory), Map.of(ResourceType.ORE, 250.0)),
                upgrades,
                variables);
    }

    private static EconomyInput input(
            TerritoryGraph graph,
            List<TerritoryEconomyInput> territories,
            Map<ResourceType, Double> storageLimits) {
        EnumMap<ResourceType, Double> opening = new EnumMap<>(ResourceType.class);
        opening.put(ResourceType.EMERALDS, 1_000_000.0);
        opening.put(ResourceType.ORE, 2_400.0);
        EnumMap<ResourceType, Double> limits = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) limits.put(resource, 100_000.0);
        limits.putAll(storageLimits);
        return new EconomyInput(
                graph,
                "HQ",
                RoutingMode.CHEAPEST,
                new ExplicitTaxPolicy(Map.of(), 0.0),
                ROUTING,
                ECONOMY,
                territories,
                opening,
                limits);
    }
}
