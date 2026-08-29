package net.syllyaddons.optimizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.EconomyInput;
import net.syllyaddons.economy.TerritoryEconomyInput;
import net.syllyaddons.economy.UpgradeCatalog;
import net.syllyaddons.economy.UpgradeDefinition;
import net.syllyaddons.economy.UpgradeEffect;

/** Immutable projection from observed current levels to a candidate economy-engine input. */
public final class OptimizationModel {
    private final EconomyInput baselineInput;
    private final Map<String, Map<String, Integer>> currentUpgrades;
    private final List<UpgradeVariable> variables;
    private final Map<String, TerritoryEconomyInput> baselineTerritories;

    public OptimizationModel(
            EconomyInput baselineInput,
            Map<String, Map<String, Integer>> currentUpgrades,
            List<UpgradeVariable> variables) {
        this.baselineInput = java.util.Objects.requireNonNull(baselineInput, "baselineInput");
        java.util.Objects.requireNonNull(currentUpgrades, "currentUpgrades");
        LinkedHashMap<String, Map<String, Integer>> upgradeCopy = new LinkedHashMap<>();
        currentUpgrades.forEach((territory, levels) -> upgradeCopy.put(territory, Map.copyOf(levels)));
        this.currentUpgrades = Map.copyOf(upgradeCopy);
        this.variables = variables.stream()
                .sorted(java.util.Comparator.comparingLong(UpgradeVariable::maximumHourlySaving).reversed()
                        .thenComparing(UpgradeVariable::coordinate))
                .toList();
        LinkedHashMap<String, TerritoryEconomyInput> inputs = new LinkedHashMap<>();
        baselineInput.territories().forEach(input -> inputs.put(input.territory(), input));
        baselineTerritories = Map.copyOf(inputs);
        validateVariables();
    }

    public EconomyInput baselineInput() {
        return baselineInput;
    }

    public Map<String, Map<String, Integer>> currentUpgrades() {
        return currentUpgrades;
    }

    public List<UpgradeVariable> variables() {
        return variables;
    }

    public Map<UpgradeCoordinate, Integer> currentAssignment() {
        LinkedHashMap<UpgradeCoordinate, Integer> assignment = new LinkedHashMap<>();
        variables.forEach(variable -> assignment.put(variable.coordinate(), variable.currentLevel()));
        return Map.copyOf(assignment);
    }

    public EconomyInput project(Map<UpgradeCoordinate, Integer> candidateLevels) {
        java.util.Objects.requireNonNull(candidateLevels, "candidateLevels");
        validateAssignment(candidateLevels);
        List<TerritoryEconomyInput> territories = new ArrayList<>();
        for (TerritoryEconomyInput baseline : baselineInput.territories()) {
            Map<String, Integer> current = currentUpgrades.getOrDefault(baseline.territory(), Map.of());
            Map<String, Integer> candidate = levelsFor(baseline.territory(), current, candidateLevels);
            Map<ResourceType, Double> production = new EnumMap<>(ResourceType.class);
            baseline.productionPerHour().forEach((resource, amount) -> {
                UpgradeEffect effect = resource == ResourceType.EMERALDS
                        ? UpgradeEffect.EMERALD_PRODUCTION
                        : UpgradeEffect.RESOURCE_PRODUCTION;
                double currentFactor = factor(current, effect);
                double candidateFactor = factor(candidate, effect);
                production.put(resource, amount * candidateFactor / currentFactor);
            });
            territories.add(new TerritoryEconomyInput(
                    baseline.territory(), production, UpgradeCatalog.expensesPerHour(candidate)));
        }

        Map<ResourceType, Double> storageLimits = new EnumMap<>(ResourceType.class);
        Map<String, Integer> currentHq = currentUpgrades.getOrDefault(baselineInput.headquarters(), Map.of());
        Map<String, Integer> candidateHq = levelsFor(baselineInput.headquarters(), currentHq, candidateLevels);
        baselineInput.hqStorageLimits().forEach((resource, amount) -> {
            UpgradeEffect effect = resource == ResourceType.EMERALDS
                    ? UpgradeEffect.EMERALD_STORAGE
                    : UpgradeEffect.RESOURCE_STORAGE;
            storageLimits.put(resource, amount * factor(candidateHq, effect) / factor(currentHq, effect));
        });

        return new EconomyInput(
                baselineInput.graph(),
                baselineInput.headquarters(),
                baselineInput.routingMode(),
                baselineInput.taxPolicy(),
                baselineInput.routingRules(),
                baselineInput.economyRules(),
                territories,
                baselineInput.openingHqStorage(),
                storageLimits);
    }

    public List<UpgradeChange> changes(Map<UpgradeCoordinate, Integer> assignment) {
        List<UpgradeChange> changes = new ArrayList<>();
        for (UpgradeVariable variable : variables) {
            int after = assignment.getOrDefault(variable.coordinate(), variable.currentLevel());
            if (after == variable.currentLevel()) continue;
            UpgradeDefinition definition = UpgradeCatalog.find(variable.coordinate().upgradeKey()).orElseThrow();
            changes.add(new UpgradeChange(
                    variable.coordinate(),
                    definition.displayName(),
                    variable.currentLevel(),
                    after,
                    definition.upkeepResource(),
                    definition.costAt(variable.currentLevel()) - definition.costAt(after)));
        }
        return List.copyOf(changes);
    }

    private void validateVariables() {
        java.util.HashSet<UpgradeCoordinate> seen = new java.util.HashSet<>();
        for (UpgradeVariable variable : variables) {
            if (!seen.add(variable.coordinate())) throw new IllegalArgumentException("Duplicate variable " + variable.coordinate());
            Integer current = currentUpgrades
                    .getOrDefault(variable.coordinate().territory(), Map.of())
                    .get(variable.coordinate().upgradeKey());
            if (current == null || current != variable.currentLevel()) {
                throw new IllegalArgumentException("Variable does not match current observed level: " + variable.coordinate());
            }
            if (!baselineTerritories.containsKey(variable.coordinate().territory())) {
                throw new IllegalArgumentException("Variable territory is absent from economy input: " + variable.coordinate());
            }
        }
    }

    private void validateAssignment(Map<UpgradeCoordinate, Integer> assignment) {
        Map<UpgradeCoordinate, UpgradeVariable> allowed = new java.util.HashMap<>();
        variables.forEach(variable -> allowed.put(variable.coordinate(), variable));
        assignment.forEach((coordinate, level) -> {
            UpgradeVariable variable = allowed.get(coordinate);
            if (variable == null) throw new IllegalArgumentException("Assignment contains non-variable " + coordinate);
            if (level == null || level < variable.minimumLevel() || level > variable.currentLevel()) {
                throw new IllegalArgumentException("Assignment level is outside the allowed range for " + coordinate);
            }
        });
    }

    private static Map<String, Integer> levelsFor(
            String territory,
            Map<String, Integer> current,
            Map<UpgradeCoordinate, Integer> candidateLevels) {
        LinkedHashMap<String, Integer> levels = new LinkedHashMap<>(current);
        candidateLevels.forEach((coordinate, level) -> {
            if (coordinate.territory().equals(territory)) levels.put(coordinate.upgradeKey(), level);
        });
        return Map.copyOf(levels);
    }

    private static double factor(Map<String, Integer> levels, UpgradeEffect effect) {
        double factor = 1.0;
        for (Map.Entry<String, Integer> level : levels.entrySet()) {
            UpgradeDefinition definition = UpgradeCatalog.find(level.getKey()).orElse(null);
            if (definition == null || definition.effect() != effect || !definition.isValidLevel(level.getValue())) continue;
            factor *= definition.effectMultipliers().get(level.getValue());
        }
        return factor;
    }
}
