package net.syllyaddons.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;

public record TerritoryEconomyInput(
        String territory,
        Map<ResourceType, Double> productionPerHour,
        Map<ResourceType, Double> expensesPerHour) {
    public TerritoryEconomyInput {
        Objects.requireNonNull(territory, "territory");
        territory = territory.strip();
        if (territory.isEmpty()) throw new IllegalArgumentException("territory must not be blank");
        productionPerHour = validatedCopy(productionPerHour, "productionPerHour");
        expensesPerHour = validatedCopy(expensesPerHour, "expensesPerHour");
    }

    private static Map<ResourceType, Double> validatedCopy(Map<ResourceType, Double> values, String field) {
        Objects.requireNonNull(values, field);
        Map<ResourceType, Double> copy = new EnumMap<>(ResourceType.class);
        values.forEach((type, value) -> {
            Objects.requireNonNull(type, field + " contains null key");
            if (value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException(field + " values must be finite and non-negative");
            }
            if (value > 0) copy.put(type, value);
        });
        return Map.copyOf(copy);
    }
}
