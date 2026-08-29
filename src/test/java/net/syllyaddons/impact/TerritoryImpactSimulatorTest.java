package net.syllyaddons.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class TerritoryImpactSimulatorTest {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "impact fixture");
    private final TerritoryImpactSimulator simulator = new TerritoryImpactSimulator();

    @Test
    void removingLineMiddleDisconnectsSourceAndLosesItsHqDelivery() {
        ObservedState state = state(
                RoutingMode.CHEAPEST,
                territory("Source", owner(), List.of("Middle"), 10),
                territory("Middle", owner(), List.of("Source", "HQ"), 0),
                territory("HQ", owner(), List.of("Middle"), 0));
        ImpactBaseline baseline = simulator.buildBaseline(state, 2_000);

        TerritoryImpactReport report = simulator.simulate(baseline, "Middle");
        RoutingModeImpact mode = report.modes().get(RoutingMode.CHEAPEST);
        TerritoryRouteImpact source = route(mode, "Source");

        assertTrue(source.changes().contains(RouteChangeKind.DISCONNECTED));
        assertEquals(List.of("Source", "Middle", "HQ"), source.baselineRoute());
        assertEquals(List.of(), source.simulatedRoute());
        assertEquals(-10, mode.resourceDeltas().get(ResourceType.ORE).deliveredDeltaPerHour(), 1.0e-9);
        assertEquals(ImpactCertainty.EXACT_OBSERVATION, mode.topologyCertainty());
        assertEquals(ImpactCertainty.ESTIMATED, mode.economyCertainty());
    }

    @Test
    void removingOneDiamondBranchReroutesAndMakesOtherBranchNewlyCritical() {
        ObservedState state = state(
                RoutingMode.CHEAPEST,
                territory("Source", owner(), List.of("A", "B"), 10),
                territory("A", owner(), List.of("Source", "HQ"), 0),
                territory("B", owner(), List.of("Source", "HQ"), 0),
                territory("HQ", owner(), List.of("A", "B"), 0));

        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(state, 2_000), "A");
        TerritoryRouteImpact source = route(report.modes().get(RoutingMode.CHEAPEST), "Source");

        assertTrue(source.changes().contains(RouteChangeKind.REROUTED));
        assertTrue(source.changes().contains(RouteChangeKind.NEWLY_CRITICAL));
        assertEquals(List.of("B"), source.newlyCriticalTerritories());
        assertEquals(List.of("Source", "B", "HQ"), source.simulatedRoute());
    }

    @Test
    void irrelevantLeafLeavesExistingRoutesClassifiedUnchanged() {
        ObservedState state = state(
                RoutingMode.FASTEST,
                territory("Source", owner(), List.of("A"), 10),
                territory("A", owner(), List.of("Source", "HQ"), 0),
                territory("Leaf", owner(), List.of("HQ"), 0),
                territory("HQ", owner(), List.of("A", "Leaf"), 0));

        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(state, 2_000), "Leaf");
        TerritoryRouteImpact source = route(report.modes().get(RoutingMode.FASTEST), "Source");

        assertEquals(java.util.Set.of(RouteChangeKind.UNCHANGED), source.changes());
        assertEquals(source.baselineRoute(), source.simulatedRoute());
        assertEquals(ImpactSeverity.MINOR, report.modes().get(RoutingMode.FASTEST).defensiveScore().severity());
    }

    @Test
    void unknownRoutingModeProducesBothIndependentBranches() {
        ObservedState state = state(
                null,
                territory("Source", owner(), List.of("HQ"), 10),
                territory("HQ", owner(), List.of("Source"), 0));

        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(state, 2_000), "Source");

        assertEquals(java.util.Set.of(RoutingMode.CHEAPEST, RoutingMode.FASTEST), report.modes().keySet());
    }

    @Test
    void foreignTargetNeverClaimsEnemyEconomyIsExact() {
        ObservedState state = state(
                RoutingMode.CHEAPEST,
                territory("Source", owner(), List.of("Transit"), 10),
                territory("Transit", new TerritoryOwner("enemy", "Enemy", "ENY"), List.of("Source", "HQ"), 500),
                territory("HQ", owner(), List.of("Transit"), 0));

        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(state, 2_000), "Transit");
        RoutingModeImpact mode = report.modes().get(RoutingMode.CHEAPEST);

        assertEquals(OwnerRelation.FOREIGN_GUILD, report.ownerRelation());
        assertEquals(ImpactCertainty.ESTIMATED, mode.economyCertainty());
        assertEquals(ImpactCertainty.ESTIMATED, mode.offensiveScore().certainty());
        assertFalse(report.missingInputs().isEmpty());
        assertTrue(mode.offensiveScore().missingInputs().stream().anyMatch(value -> value.contains("Enemy HQ")));
    }

    @Test
    void removingHeadquartersIsCatastrophicButStillAdvisory() {
        ObservedState state = state(
                RoutingMode.CHEAPEST,
                territory("Source", owner(), List.of("HQ"), 10),
                territory("HQ", owner(), List.of("Source"), 0));

        RoutingModeImpact mode = simulator.simulate(simulator.buildBaseline(state, 2_000), "HQ")
                .modes().get(RoutingMode.CHEAPEST);

        assertEquals(ImpactSeverity.CATASTROPHIC, mode.defensiveScore().severity());
        assertEquals(ImpactCertainty.ESTIMATED, mode.defensiveScore().certainty());
        assertTrue(mode.routeImpacts().stream()
                .filter(value -> value.sourceTerritory().equals("Source"))
                .allMatch(value -> value.changes().contains(RouteChangeKind.DISCONNECTED)));
    }

    private static TerritoryRouteImpact route(RoutingModeImpact impact, String source) {
        return impact.routeImpacts().stream()
                .filter(value -> value.sourceTerritory().equals(source))
                .findFirst().orElseThrow();
    }

    static ObservedState state(RoutingMode mode, TerritoryState... territories) {
        Map<String, TerritoryState> values = new LinkedHashMap<>();
        for (TerritoryState territory : territories) values.put(territory.name(), territory);
        return new ObservedState(
                1,
                1,
                1_000,
                ObservedValue.unknown("unused"),
                ObservedValue.known(new GuildIdentity("guild", "Guild", "TAG"), EVIDENCE),
                ObservedValue.known("HQ", EVIDENCE),
                mode == null ? ObservedValue.unknown("fixture unknown mode") : ObservedValue.known(mode, EVIDENCE),
                values);
    }

    static TerritoryState territory(
            String name, TerritoryOwner owner, List<String> links, long oreGeneration) {
        Map<ResourceType, ResourceBalance> resources = oreGeneration == 0
                ? Map.of()
                : Map.of(ResourceType.ORE, new ResourceBalance(oreGeneration, 0, 1_000));
        return new TerritoryState(
                name,
                ObservedValue.known(owner, EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(name.equals("HQ"), EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(links, EVIDENCE),
                ObservedValue.known(resources, EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.known(Map.of(), EVIDENCE),
                ObservedValue.known(List.of(), EVIDENCE));
    }

    static TerritoryOwner owner() {
        return new TerritoryOwner("guild", "Guild", "TAG");
    }
}
