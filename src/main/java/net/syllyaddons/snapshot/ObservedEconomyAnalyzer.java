package net.syllyaddons.snapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyEngine;
import net.syllyaddons.economy.EconomyInput;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.economy.EconomyRules;
import net.syllyaddons.economy.TerritoryEconomyInput;
import net.syllyaddons.economy.UpgradeCatalog;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;
import net.syllyaddons.routing.OwnerTaxPolicy;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.TerritoryGraph;

/** Projects the currently observable subset into Track 4's explicitly estimated economy model. */
public final class ObservedEconomyAnalyzer {
    public static final double ASSUMED_FOREIGN_TAX_RATE = 0.70;
    private final ObservedTerritoryGraphFactory graphFactory = new ObservedTerritoryGraphFactory();
    private final EconomyEngine economyEngine = new EconomyEngine();

    public SnapshotPayload analyze(ObservedState state, long createdAtEpochMillis) {
        EcoSnapshot observed = EcoSnapshot.from(state, createdAtEpochMillis);
        List<RouteDiagnostic> diagnostics = new ArrayList<>();
        if (state.territories().isEmpty()) {
            diagnostics.add(new RouteDiagnostic("NO_TERRITORIES", "No territory observations are available"));
            return new SnapshotPayload(observed, null, diagnostics);
        }
        if (!state.hqTerritory().isKnown()) {
            diagnostics.add(new RouteDiagnostic("UNKNOWN_HQ", "Economy analysis needs an observed headquarters"));
            return new SnapshotPayload(observed, null, diagnostics);
        }
        if (!state.routingMode().isKnown()) {
            diagnostics.add(new RouteDiagnostic(
                    "UNKNOWN_ROUTING_MODE", "Economy analysis needs the currently selected HQ routing mode"));
            return new SnapshotPayload(observed, null, diagnostics);
        }
        if (!state.guild().isKnown()) {
            diagnostics.add(new RouteDiagnostic("UNKNOWN_GUILD", "Economy analysis needs the current guild identity"));
            return new SnapshotPayload(observed, null, diagnostics);
        }

        String hq = state.hqTerritory().value();
        TerritoryState hqState = state.territories().get(hq);
        if (hqState == null) {
            diagnostics.add(new RouteDiagnostic("UNKNOWN_HQ", "Observed HQ is absent from the territory map"));
            return new SnapshotPayload(observed, null, diagnostics);
        }

        GuildIdentity guild = state.guild().value();
        List<TerritoryEconomyInput> territoryInputs = new ArrayList<>();
        int missingResources = 0;
        int missingUpgrades = 0;
        int unknownUpgradeKeys = 0;
        int invalidUpgradeLevels = 0;
        for (TerritoryState territory : state.territories().values().stream()
                .sorted(java.util.Comparator.comparing(TerritoryState::name))
                .toList()) {
            if (!isOwnedBy(territory, guild)) continue;
            if (!territory.resources().isKnown()) {
                missingResources++;
            }
            Map<ResourceType, Double> production = new EnumMap<>(ResourceType.class);
            if (territory.resources().isKnown()) {
                territory.resources().value().forEach(
                        (resource, balance) -> production.put(resource, (double) balance.generationPerHour()));
            }
            Map<ResourceType, Double> expenses = Map.of();
            if (territory.upgrades().isKnown()) {
                expenses = UpgradeCatalog.expensesPerHour(territory.upgrades().value());
                unknownUpgradeKeys += (int) territory.upgrades().value().keySet().stream()
                        .filter(key -> UpgradeCatalog.find(key).isEmpty())
                        .count();
                invalidUpgradeLevels += (int) territory.upgrades().value().entrySet().stream()
                        .filter(entry -> UpgradeCatalog.find(entry.getKey())
                                .map(definition -> !definition.isValidLevel(entry.getValue()))
                                .orElse(false))
                        .count();
            } else {
                missingUpgrades++;
            }
            territoryInputs.add(new TerritoryEconomyInput(territory.name(), production, expenses));
        }

        Map<ResourceType, Double> openingStorage = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Double> storageLimits = new EnumMap<>(ResourceType.class);
        if (hqState.resources().isKnown()) {
            for (Map.Entry<ResourceType, ResourceBalance> entry : hqState.resources().value().entrySet()) {
                openingStorage.put(entry.getKey(), (double) entry.getValue().stored());
                storageLimits.put(entry.getKey(), (double) entry.getValue().storageLimit());
            }
        } else {
            diagnostics.add(new RouteDiagnostic(
                    "UNKNOWN_HQ_STORAGE", "HQ storage and capacity were not observed; zero is used for this estimate"));
        }

        java.util.Set<String> ownerIds = new java.util.HashSet<>();
        if (!guild.uuid().isBlank()) ownerIds.add(guild.uuid());
        if (!guild.name().isBlank()) ownerIds.add(guild.name());
        TerritoryGraph graph = graphFactory.create(observed);
        RoutingRules routingRules = RoutingRules.research2026_08_29();
        EconomyRules economyRules = EconomyRules.research2026_08_29();
        EconomyResult result = economyEngine.calculate(new EconomyInput(
                graph,
                hq,
                state.routingMode().value(),
                new OwnerTaxPolicy(ownerIds, ASSUMED_FOREIGN_TAX_RATE),
                routingRules,
                economyRules,
                territoryInputs,
                openingStorage,
                storageLimits));

        if (missingUpgrades > 0) {
            diagnostics.add(new RouteDiagnostic(
                    "UPGRADE_EXPENSES_INCOMPLETE",
                    missingUpgrades + " owned territory/territories have no observed upgrade levels; expense checks are withheld"));
        }
        if (unknownUpgradeKeys > 0) {
            diagnostics.add(new RouteDiagnostic(
                    "UNKNOWN_UPGRADE_KEYS",
                    unknownUpgradeKeys + " observed upgrade key(s) are absent from the Wynntils 4.2.8 catalog"));
        }
        if (invalidUpgradeLevels > 0) {
            diagnostics.add(new RouteDiagnostic(
                    "INVALID_UPGRADE_LEVELS",
                    invalidUpgradeLevels + " observed upgrade level(s) are outside the Wynntils 4.2.8 catalog"));
        }
        diagnostics.add(new RouteDiagnostic(
                "ASSUMED_FOREIGN_TAX",
                "Unobserved foreign-route tax is estimated at " + (int) (ASSUMED_FOREIGN_TAX_RATE * 100) + "%"));
        if (missingResources > 0) {
            diagnostics.add(new RouteDiagnostic(
                    "MISSING_PRODUCTION",
                    missingResources + " territory/territories have no observed production values"));
        }
        return new SnapshotPayload(observed, result, diagnostics);
    }

    private static boolean isOwnedBy(TerritoryState territory, GuildIdentity guild) {
        if (!territory.owner().isKnown()) return false;
        var owner = territory.owner().value();
        return (!guild.uuid().isBlank() && guild.uuid().equals(owner.guildUuid()))
                || (!guild.name().isBlank() && guild.name().equals(owner.guildName()));
    }
}
