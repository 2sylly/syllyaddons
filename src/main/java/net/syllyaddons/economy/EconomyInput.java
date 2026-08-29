package net.syllyaddons.economy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.routing.RouteTaxPolicy;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.TerritoryGraph;

public record EconomyInput(
        TerritoryGraph graph,
        String headquarters,
        RoutingMode routingMode,
        RouteTaxPolicy taxPolicy,
        RoutingRules routingRules,
        EconomyRules economyRules,
        List<TerritoryEconomyInput> territories,
        Map<ResourceType, Double> openingHqStorage,
        Map<ResourceType, Double> hqStorageLimits) {
    public EconomyInput {
        graph = Objects.requireNonNull(graph, "graph");
        headquarters = Objects.requireNonNull(headquarters, "headquarters").strip();
        if (headquarters.isEmpty()) throw new IllegalArgumentException("headquarters must not be blank");
        routingMode = Objects.requireNonNull(routingMode, "routingMode");
        taxPolicy = Objects.requireNonNull(taxPolicy, "taxPolicy");
        routingRules = Objects.requireNonNull(routingRules, "routingRules");
        economyRules = Objects.requireNonNull(economyRules, "economyRules");
        territories = List.copyOf(territories);
        openingHqStorage = validatedCopy(openingHqStorage, "openingHqStorage");
        hqStorageLimits = validatedCopy(hqStorageLimits, "hqStorageLimits");
    }

    private static Map<ResourceType, Double> validatedCopy(Map<ResourceType, Double> values, String field) {
        Objects.requireNonNull(values, field);
        Map<ResourceType, Double> copy = new EnumMap<>(ResourceType.class);
        values.forEach((type, value) -> {
            if (type == null || value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException(field + " values must be finite and non-negative");
            }
            copy.put(type, value);
        });
        return Map.copyOf(copy);
    }
}
