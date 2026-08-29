package net.syllyaddons.optimizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyInput;
import net.syllyaddons.economy.EconomyRules;
import net.syllyaddons.economy.TerritoryEconomyInput;
import net.syllyaddons.economy.UpgradeCatalog;
import net.syllyaddons.economy.UpgradeDefinition;
import net.syllyaddons.economy.UpgradeEffect;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;
import net.syllyaddons.routing.OwnerTaxPolicy;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.snapshot.ObservedEconomyAnalyzer;

/** Builds an optimizer model only when every cost/effect input needed for a safe comparison is observed. */
public final class ObservedOptimizationModelFactory {
    private final ObservedTerritoryGraphFactory graphFactory = new ObservedTerritoryGraphFactory();

    public OptimizationModelBuild build(ObservedState state, long nowEpochMillis) {
        java.util.Objects.requireNonNull(state, "state");
        if (!state.guild().isKnown()) return OptimizationModelBuild.unavailable("Current guild is not observed.");
        if (!state.hqTerritory().isKnown()) return OptimizationModelBuild.unavailable("Guild headquarters is not observed.");
        if (!state.routingMode().isKnown()) return OptimizationModelBuild.unavailable("HQ routing mode is not observed.");
        if (state.territories().isEmpty()) return OptimizationModelBuild.unavailable("No territory topology is observed.");

        GuildIdentity guild = state.guild().value();
        String hq = state.hqTerritory().value();
        TerritoryState hqState = state.territories().get(hq);
        if (hqState == null || !ownedBy(hqState, guild)) {
            return OptimizationModelBuild.unavailable("The observed HQ is absent or is not attributed to the current guild.");
        }
        if (!hqState.resources().isKnown()) {
            return OptimizationModelBuild.unavailable("HQ storage and capacity must be observed before optimization.");
        }

        List<String> diagnostics = new ArrayList<>();
        List<TerritoryEconomyInput> territoryInputs = new ArrayList<>();
        Map<String, Map<String, Integer>> currentUpgrades = new LinkedHashMap<>();
        List<UpgradeVariable> variables = new ArrayList<>();
        for (TerritoryState territory : state.territories().values().stream()
                .sorted(java.util.Comparator.comparing(TerritoryState::name))
                .toList()) {
            if (!ownedBy(territory, guild)) continue;
            if (!territory.resources().isKnown()) {
                diagnostics.add(territory.name() + " production/storage is not observed.");
                continue;
            }
            if (!territory.upgrades().isKnown()) {
                diagnostics.add(territory.name() + " upgrade levels are not observed.");
                continue;
            }
            Map<String, Integer> levels = territory.upgrades().value();
            for (Map.Entry<String, Integer> level : levels.entrySet()) {
                UpgradeDefinition definition = UpgradeCatalog.find(level.getKey()).orElse(null);
                if (definition == null) {
                    diagnostics.add(territory.name() + " has unknown upgrade " + level.getKey() + ".");
                } else if (!definition.isValidLevel(level.getValue())) {
                    diagnostics.add(territory.name() + " has invalid " + level.getKey() + " level " + level.getValue() + ".");
                }
            }
            currentUpgrades.put(territory.name(), Map.copyOf(levels));
            Map<ResourceType, Double> production = new EnumMap<>(ResourceType.class);
            territory.resources().value().forEach(
                    (resource, balance) -> production.put(resource, (double) balance.generationPerHour()));
            territoryInputs.add(new TerritoryEconomyInput(
                    territory.name(), production, UpgradeCatalog.expensesPerHour(levels)));
            addVariables(variables, territory.name(), hq, levels);
        }
        if (!diagnostics.isEmpty()) {
            return new OptimizationModelBuild(java.util.Optional.empty(), diagnostics);
        }
        if (territoryInputs.isEmpty()) return OptimizationModelBuild.unavailable("No owned territory economy is observed.");

        Map<ResourceType, Double> openingStorage = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Double> storageLimits = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            ResourceBalance balance = hqState.resources().value().get(resource);
            openingStorage.put(resource, balance == null ? 0.0 : (double) balance.stored());
            storageLimits.put(resource, balance == null ? 0.0 : (double) balance.storageLimit());
        }
        Set<String> ownerIds = new HashSet<>();
        if (!guild.uuid().isBlank()) ownerIds.add(guild.uuid());
        if (!guild.name().isBlank()) ownerIds.add(guild.name());
        EconomyInput baselineInput = new EconomyInput(
                graphFactory.create(EcoSnapshot.from(state, nowEpochMillis)),
                hq,
                state.routingMode().value(),
                new OwnerTaxPolicy(ownerIds, ObservedEconomyAnalyzer.ASSUMED_FOREIGN_TAX_RATE),
                RoutingRules.research2026_08_29(),
                EconomyRules.research2026_08_29(),
                territoryInputs,
                openingStorage,
                storageLimits);
        return new OptimizationModelBuild(
                java.util.Optional.of(new OptimizationModel(baselineInput, currentUpgrades, variables)),
                List.of("Tower and unquantified strategic upgrades are fixed."));
    }

    private static void addVariables(
            List<UpgradeVariable> variables,
            String territory,
            String hq,
            Map<String, Integer> levels) {
        levels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            UpgradeDefinition definition = UpgradeCatalog.find(entry.getKey()).orElse(null);
            if (definition == null || !definition.hasQuantifiedEconomicEffect() || entry.getValue() <= 0) return;
            boolean production = definition.effect() == UpgradeEffect.RESOURCE_PRODUCTION
                    || definition.effect() == UpgradeEffect.EMERALD_PRODUCTION;
            boolean hqStorage = territory.equals(hq)
                    && (definition.effect() == UpgradeEffect.RESOURCE_STORAGE
                            || definition.effect() == UpgradeEffect.EMERALD_STORAGE);
            if (production || hqStorage) {
                variables.add(new UpgradeVariable(
                        new UpgradeCoordinate(territory, entry.getKey()), 0, entry.getValue()));
            }
        });
    }

    private static boolean ownedBy(TerritoryState territory, GuildIdentity guild) {
        if (!territory.owner().isKnown()) return false;
        TerritoryOwner owner = territory.owner().value();
        return (!guild.uuid().isBlank() && guild.uuid().equals(owner.guildUuid()))
                || (!guild.name().isBlank() && guild.name().equals(owner.guildName()));
    }
}
