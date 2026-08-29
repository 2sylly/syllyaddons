package net.syllyaddons.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.domain.CharacterIdentity;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;
import org.junit.jupiter.api.Test;

class ImpactLossAlertMatcherTest {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "track 8 fixture");

    @Test
    void ownershipLossSelectsOnlyTheExactCompletedPreLossEntry() {
        ObservedState before = live(TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory(
                        "Source", TerritoryImpactSimulatorTest.owner(), List.of("HQ"), 10),
                TerritoryImpactSimulatorTest.territory(
                        "HQ", TerritoryImpactSimulatorTest.owner(), List.of("Source"), 0)), 1, 1_000);
        TerritoryImpactSimulator simulator = new TerritoryImpactSimulator();
        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(before, 2_000), "HQ");
        ObservedState after = replaceOwner(before, "HQ", new TerritoryOwner("enemy", "Enemy", "ENY"), 2, 15_000);
        ImpactCacheView matching = view(report, 12_000);

        List<ImpactLossAlert> alerts = new ImpactLossAlertMatcher()
                .match(before, after, matching, SyllyConfig.defaults(), 20_000);

        assertEquals(1, alerts.size());
        ImpactLossAlert alert = alerts.getFirst();
        assertEquals("HQ", alert.territory());
        assertEquals(ImpactSeverity.CATASTROPHIC, alert.severity());
        assertEquals(8_000, alert.baselineAgeMillis());
        assertEquals(14_000, alert.refreshWindowMillis());
        assertEquals(1, alert.baselineRevision());

        TerritoryImpactReport wrongRevision = new TerritoryImpactReport(
                report.removedTerritory(), report.ownerRelation(), report.headquartersRemoved(), 99,
                report.cacheKey(), report.modes(), report.missingInputs());
        assertTrue(new ImpactLossAlertMatcher()
                .match(before, after, view(wrongRevision, 12_000), SyllyConfig.defaults(), 20_000)
                .isEmpty());
        TerritoryImpactReport wrongKey = new TerritoryImpactReport(
                report.removedTerritory(), report.ownerRelation(), report.headquartersRemoved(), report.sourceRevision(),
                "not-the-pre-loss-key", report.modes(), report.missingInputs());
        assertTrue(new ImpactLossAlertMatcher()
                .match(before, after, view(wrongKey, 12_000), SyllyConfig.defaults(), 20_000)
                .isEmpty());
    }

    @Test
    void unknownOrChangedSessionExpiresTheMatchBoundary() {
        ObservedState before = live(TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory(
                        "HQ", TerritoryImpactSimulatorTest.owner(), List.of(), 0)), 1, 1_000);
        ObservedState changedCharacter = new ObservedState(
                before.schemaVersion(), 2, 2_000,
                ObservedValue.known(new CharacterIdentity("other", "MAGE", false), EVIDENCE),
                before.guild(), before.hqTerritory(), before.routingMode(), before.territories());

        assertTrue(new ImpactLossAlertMatcher().match(
                before, changedCharacter, ImpactCacheView.empty(), SyllyConfig.defaults(), 3_000).isEmpty());
        assertTrue(!OwnershipTransitionDetector.sameSession(before, changedCharacter));
    }

    private static ImpactCacheView view(TerritoryImpactReport report, long builtAt) {
        return new ImpactCacheView(
                ImpactCacheStatus.BUILDING, 2, 2, "new-key", 0, 2, builtAt, 4,
                Map.of(report.removedTerritory(), report), "new cache building", true);
    }

    private static ObservedState live(ObservedState state, long revision, long assembledAt) {
        return new ObservedState(
                state.schemaVersion(), revision, assembledAt,
                ObservedValue.known(new CharacterIdentity("character", "WARRIOR", false), EVIDENCE),
                state.guild(), state.hqTerritory(), state.routingMode(), state.territories());
    }

    private static ObservedState replaceOwner(
            ObservedState state,
            String territory,
            TerritoryOwner owner,
            long revision,
            long assembledAt) {
        Map<String, TerritoryState> territories = new LinkedHashMap<>(state.territories());
        TerritoryState old = territories.get(territory);
        Evidence ownershipEvidence = new Evidence(
                EvidenceKind.LOCAL_EXACT, assembledAt, "fixture", "2", "changed owner fixture");
        territories.put(territory, new TerritoryState(
                old.name(), ObservedValue.known(owner, ownershipEvidence), old.acquiredAtEpochMillis(), old.headquarters(),
                old.bounds(), old.links(), old.resources(), old.treasury(), old.treasuryBonusPercent(),
                old.defences(), old.upgrades(), old.alerts()));
        return new ObservedState(
                state.schemaVersion(), revision, assembledAt, state.character(), state.guild(), state.hqTerritory(),
                state.routingMode(), territories);
    }
}
