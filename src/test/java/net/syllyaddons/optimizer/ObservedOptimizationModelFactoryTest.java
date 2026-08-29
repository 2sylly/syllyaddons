package net.syllyaddons.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;
import org.junit.jupiter.api.Test;

class ObservedOptimizationModelFactoryTest {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "optimizer factory fixture");
    private static final TerritoryOwner OWNER = new TerritoryOwner("guild", "Guild", "TAG");
    private final ObservedOptimizationModelFactory factory = new ObservedOptimizationModelFactory();

    @Test
    void exposesOnlyQuantifiedProductionAndHqStorageVariables() {
        ObservedState state = state(
                territory("HQ", List.of("Mine"), Map.of(
                        "DAMAGE", 5,
                        "EFFICIENT_RESOURCES", 2,
                        "RESOURCE_STORAGE", 3,
                        "XP_SEEKING", 4)),
                territory("Mine", List.of("HQ"), Map.of(
                        "EFFICIENT_EMERALDS", 2,
                        "EMERALD_STORAGE", 2,
                        "TOWER_AURA", 1)));

        OptimizationModel model = factory.build(state, 2_000).model().orElseThrow();

        assertEquals(
                java.util.Set.of(
                        new UpgradeCoordinate("HQ", "EFFICIENT_RESOURCES"),
                        new UpgradeCoordinate("HQ", "RESOURCE_STORAGE"),
                        new UpgradeCoordinate("Mine", "EFFICIENT_EMERALDS")),
                model.variables().stream().map(UpgradeVariable::coordinate).collect(java.util.stream.Collectors.toSet()));
        assertTrue(model.variables().stream().noneMatch(variable ->
                variable.coordinate().upgradeKey().equals("DAMAGE")
                        || variable.coordinate().upgradeKey().equals("TOWER_AURA")
                        || variable.coordinate().upgradeKey().equals("XP_SEEKING")
                        || variable.coordinate().upgradeKey().equals("EMERALD_STORAGE")));
    }

    @Test
    void refusesUnknownUpgradeCostsInsteadOfTreatingThemAsZero() {
        ObservedState state = state(territory("HQ", List.of(), Map.of("FUTURE_UNKNOWN_UPGRADE", 1)));

        OptimizationModelBuild result = factory.build(state, 2_000);

        assertTrue(result.model().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("unknown upgrade")));
    }

    @Test
    void refusesMissingOwnedTerritoryUpgradeObservation() {
        TerritoryState missing = territory("HQ", List.of(), Map.of());
        missing = new TerritoryState(
                missing.name(), missing.owner(), missing.acquiredAtEpochMillis(), missing.headquarters(), missing.bounds(),
                missing.links(), missing.resources(), missing.treasury(), missing.treasuryBonusPercent(), missing.defences(),
                ObservedValue.unknown("not scanned"), missing.alerts());

        OptimizationModelBuild result = factory.build(state(missing), 2_000);

        assertTrue(result.model().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("upgrade levels are not observed")));
    }

    private static ObservedState state(TerritoryState... territories) {
        Map<String, TerritoryState> values = new LinkedHashMap<>();
        for (TerritoryState territory : territories) values.put(territory.name(), territory);
        return new ObservedState(
                1, 1, 1_000,
                ObservedValue.unknown("unused"),
                ObservedValue.known(new GuildIdentity("guild", "Guild", "TAG"), EVIDENCE),
                ObservedValue.known("HQ", EVIDENCE),
                ObservedValue.known(RoutingMode.CHEAPEST, EVIDENCE),
                values);
    }

    private static TerritoryState territory(String name, List<String> links, Map<String, Integer> upgrades) {
        Map<ResourceType, ResourceBalance> resources = new java.util.EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            resources.put(resource, new ResourceBalance(1_000, 5_000, 10_000));
        }
        return new TerritoryState(
                name,
                ObservedValue.known(OWNER, EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(name.equals("HQ"), EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(links, EVIDENCE),
                ObservedValue.known(resources, EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.known(upgrades, EVIDENCE),
                ObservedValue.known(List.of(), EVIDENCE));
    }
}
