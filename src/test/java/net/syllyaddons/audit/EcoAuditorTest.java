package net.syllyaddons.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryRating;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.economy.ProvenanceKind;
import net.syllyaddons.economy.ResourceDeficit;
import net.syllyaddons.economy.ResourceEconomySummary;
import net.syllyaddons.economy.ResourceProvenance;
import net.syllyaddons.routing.RuleConfidence;
import net.syllyaddons.snapshot.SnapshotPayload;
import org.junit.jupiter.api.Test;

class EcoAuditorTest {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "audit fixture");
    private final EcoAuditor auditor = new EcoAuditor();

    @Test
    void negativeBalanceFixtureTriggersOnlyBalanceAndUpkeepFindings() {
        TerritoryState hq = territory(
                "HQ", List.of(), Map.of(ResourceType.ORE, new ResourceBalance(5, 0, 100)), Map.of(), TerritoryRating.HIGH);
        EconomyResult economy = economy(
                Map.of(ResourceType.ORE, summary(ResourceType.ORE, 5, 10, 5, 0, 0)),
                List.of(provenance("HQ", ResourceType.ORE, 5, List.of("HQ"), 5, 0, 0)),
                List.of(new ResourceDeficit("HQ", ResourceType.ORE, 10, 5, 5)));

        AuditReport report = auditor.audit(payload(Map.of("HQ", hq), economy), 2_000);

        assertEquals(
                Set.of(AuditFindingType.NEGATIVE_NET_PRODUCTION, AuditFindingType.UNSUSTAINABLE_TOWER_UPKEEP),
                categories(report));
        assertTrue(report.findings().stream().flatMap(value -> value.calculations().stream())
                .allMatch(value -> !value.inputs().isEmpty()));
    }

    @Test
    void disconnectedFixtureTriggersOnlyUndeliveredProduction() {
        TerritoryState hq = territory("HQ", List.of(), Map.of(), Map.of(), TerritoryRating.HIGH);
        TerritoryState island = territory(
                "Island", List.of(), Map.of(ResourceType.WOOD, new ResourceBalance(4, 0, 100)), Map.of(), TerritoryRating.HIGH);
        EconomyResult economy = economy(
                Map.of(ResourceType.WOOD, summary(ResourceType.WOOD, 0, 0, 0, 0, 4)),
                List.of(provenance("Island", ResourceType.WOOD, 4, List.of(), 0, 0, 4)),
                List.of());

        AuditReport report = auditor.audit(payload(Map.of("HQ", hq, "Island", island), economy), 2_000);

        assertEquals(Set.of(AuditFindingType.PRODUCTION_NOT_REACHING_HQ), categories(report));
        assertEquals("Island", report.findings().getFirst().provenance().getFirst().sourceTerritory());
    }

    @Test
    void articulationFixtureReportsOneDeduplicatedChokepoint() {
        TerritoryState hq = territory("HQ", List.of("Middle"), Map.of(), Map.of(), TerritoryRating.HIGH);
        TerritoryState middle = territory("Middle", List.of("Source", "HQ"), Map.of(), Map.of(), TerritoryRating.HIGH);
        TerritoryState source = territory(
                "Source", List.of("Middle"), Map.of(ResourceType.FISH, new ResourceBalance(10, 0, 100)), Map.of(), TerritoryRating.HIGH);
        EconomyResult economy = economy(
                Map.of(ResourceType.FISH, summary(ResourceType.FISH, 10, 0, 0, 0, 0)),
                List.of(provenance("Source", ResourceType.FISH, 10, List.of("Source", "Middle", "HQ"), 10, 0, 0)),
                List.of());

        AuditReport report = auditor.audit(payload(Map.of("HQ", hq, "Middle", middle, "Source", source), economy), 2_000);

        assertEquals(Set.of(AuditFindingType.SINGLE_ROUTE_OR_CHOKEPOINT), categories(report));
        assertEquals(1, report.findings().size());
        assertTrue(report.findings().getFirst().affectedTerritories().contains("Middle"));
    }

    @Test
    void highTaxAlternativeFixtureReportsOnlyExpensiveRoute() {
        TerritoryState hq = territory("HQ", List.of("Source", "Alt"), Map.of(), Map.of(), TerritoryRating.HIGH);
        TerritoryState alt = territory("Alt", List.of("Source", "HQ"), Map.of(), Map.of(), TerritoryRating.HIGH);
        TerritoryState source = territory(
                "Source", List.of("HQ", "Alt"), Map.of(ResourceType.CROPS, new ResourceBalance(10, 0, 100)), Map.of(), TerritoryRating.HIGH);
        ResourceProvenance provenance = new ResourceProvenance(
                ProvenanceKind.PRODUCTION,
                "Source",
                ResourceType.CROPS,
                10,
                List.of("Source", "HQ"),
                List.of(),
                5,
                5,
                60,
                List.of(),
                0,
                0,
                0,
                RuleConfidence.RESEARCH_ASSUMPTION,
                List.of());
        EconomyResult economy = economy(
                Map.of(ResourceType.CROPS, summary(ResourceType.CROPS, 5, 0, 0, 0, 0)),
                List.of(provenance),
                List.of());

        AuditReport report = auditor.audit(payload(Map.of("HQ", hq, "Alt", alt, "Source", source), economy), 2_000);

        assertEquals(Set.of(AuditFindingType.LONG_OR_EXPENSIVE_ROUTE), categories(report));
        assertEquals(50.0, report.findings().getFirst().calculations().get(1).result(), 1.0e-9);
    }

    @Test
    void storageTreasuryAndUpgradeFixturesExposeOnlySupportedCategories() {
        TerritoryState hq = territory(
                "HQ",
                List.of(),
                Map.of(
                        ResourceType.EMERALDS, new ResourceBalance(0, 95, 100),
                        ResourceType.WOOD, new ResourceBalance(0, 100, 1_000)),
                Map.of("EFFICIENT_RESOURCES", 1, "RESOURCE_STORAGE", 1),
                TerritoryRating.LOW);
        EconomyResult economy = economy(
                Map.of(
                        ResourceType.EMERALDS, summary(ResourceType.EMERALDS, 0, 0, 0, 0, 0),
                        ResourceType.WOOD, summary(ResourceType.WOOD, 0, 0, 0, 0, 0)),
                List.of(),
                List.of());

        AuditReport report = auditor.audit(payload(Map.of("HQ", hq), economy), 2_000);

        assertEquals(
                Set.of(
                        AuditFindingType.STORAGE_OR_TREASURY_RISK,
                        AuditFindingType.DOMINATED_ECONOMIC_UPGRADE,
                        AuditFindingType.LOW_VALUE_UPGRADE,
                        AuditFindingType.POTENTIALLY_SAFE_DOWNGRADE),
                categories(report));
        AuditFinding mergedUpgrade = report.findings().stream()
                .filter(value -> value.rootCauseKey().contains("EFFICIENT_RESOURCES"))
                .findFirst().orElseThrow();
        assertEquals(
                Set.of(AuditFindingType.DOMINATED_ECONOMIC_UPGRADE, AuditFindingType.LOW_VALUE_UPGRADE),
                mergedUpgrade.categories());
    }

    @Test
    void simultaneousOverflowAndDeficitRetainsBothRootCauseCategories() {
        TerritoryState hq = territory(
                "HQ", List.of(), Map.of(ResourceType.ORE, new ResourceBalance(20, 0, 100)), Map.of(), TerritoryRating.HIGH);
        EconomyResult economy = economy(
                Map.of(ResourceType.ORE, summary(ResourceType.ORE, 20, 10, 1, 2, 0)),
                List.of(provenance("HQ", ResourceType.ORE, 20, List.of("HQ"), 20, 0, 0)),
                List.of(new ResourceDeficit("HQ", ResourceType.ORE, 1, 0, 1)));

        AuditReport report = auditor.audit(payload(Map.of("HQ", hq), economy), 2_000);

        assertEquals(
                Set.of(
                        AuditFindingType.SIMULTANEOUS_SURPLUS_AND_DEFICIT,
                        AuditFindingType.UNSUSTAINABLE_TOWER_UPKEEP,
                        AuditFindingType.STORAGE_OR_TREASURY_RISK),
                categories(report));
    }

    @Test
    void noticeGateCollapsesFindingsAndHonorsRepeatedWarningCooldown() {
        TerritoryState hq = territory(
                "HQ", List.of(), Map.of(ResourceType.ORE, new ResourceBalance(5, 0, 100)), Map.of(), TerritoryRating.HIGH);
        EconomyResult economy = economy(
                Map.of(ResourceType.ORE, summary(ResourceType.ORE, 5, 10, 5, 0, 0)),
                List.of(provenance("HQ", ResourceType.ORE, 5, List.of("HQ"), 5, 0, 0)),
                List.of(new ResourceDeficit("HQ", ResourceType.ORE, 10, 5, 5)));
        AuditReport report = auditor.audit(payload(Map.of("HQ", hq), economy), 2_000);
        EcoAuditNoticeGate gate = new EcoAuditNoticeGate();

        assertTrue(gate.next(report, 2_000, 30).orElseThrow().contains("(+1 more)"));
        assertFalse(gate.next(report, 31_999, 30).isPresent());
        assertTrue(gate.next(report, 32_000, 30).isPresent());
    }

    private static Set<AuditFindingType> categories(AuditReport report) {
        return report.findings().stream()
                .flatMap(value -> value.categories().stream())
                .collect(Collectors.toSet());
    }

    private static SnapshotPayload payload(Map<String, TerritoryState> territories, EconomyResult economy) {
        EcoSnapshot snapshot = new EcoSnapshot(
                1,
                1_000,
                7,
                ObservedValue.known(new GuildIdentity("guild", "Guild", "TAG"), EVIDENCE),
                ObservedValue.known("HQ", EVIDENCE),
                ObservedValue.known(RoutingMode.CHEAPEST, EVIDENCE),
                territories);
        return new SnapshotPayload(snapshot, economy, List.of());
    }

    private static TerritoryState territory(
            String name,
            List<String> links,
            Map<ResourceType, ResourceBalance> resources,
            Map<String, Integer> upgrades,
            TerritoryRating treasury) {
        return new TerritoryState(
                name,
                ObservedValue.known(new TerritoryOwner("guild", "Guild", "TAG"), EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(name.equals("HQ"), EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(links, EVIDENCE),
                ObservedValue.known(resources, EVIDENCE),
                ObservedValue.known(treasury, EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.known(upgrades, EVIDENCE),
                ObservedValue.known(List.of(), EVIDENCE));
    }

    private static EconomyResult economy(
            Map<ResourceType, ResourceEconomySummary> summaries,
            List<ResourceProvenance> provenance,
            List<ResourceDeficit> deficits) {
        return new EconomyResult(
                "fixture-economy",
                "fixture-routing",
                summaries,
                provenance,
                deficits,
                RuleConfidence.EXPLICIT_INPUT,
                List.of());
    }

    private static ResourceEconomySummary summary(
            ResourceType resource,
            double delivered,
            double expenses,
            double deficit,
            double overflow,
            double undelivered) {
        return new ResourceEconomySummary(
                resource, 0, delivered + undelivered, 0, delivered, expenses, Math.max(0, expenses - deficit),
                deficit, 0, overflow, undelivered);
    }

    private static ResourceProvenance provenance(
            String source,
            ResourceType resource,
            double gross,
            List<String> route,
            double delivered,
            double taxLoss,
            double undelivered) {
        return new ResourceProvenance(
                ProvenanceKind.PRODUCTION,
                source,
                resource,
                gross,
                route,
                List.of(),
                taxLoss,
                delivered,
                route.isEmpty() ? 0 : (route.size() - 1L) * 60,
                List.of(),
                0,
                0,
                undelivered,
                RuleConfidence.EXPLICIT_INPUT,
                List.of());
    }
}
