package net.syllyaddons.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.nio.file.Path;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.config.SyllyConfigStore;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.observation.ObservedStateMerger;
import net.syllyaddons.observation.ObservedStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttackRoutingAdvisorTest {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "attack advisor fixture");
    private static final TerritoryOwner OWN = new TerritoryOwner("guild", "Guild", "TAG");
    private static final TerritoryOwner ENEMY = new TerritoryOwner("enemy", "Enemy", "ENY");
    private final AttackRoutingAdvisor advisor = new AttackRoutingAdvisor();

    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesObservedCheapestAgainstEstimatedFastest() {
        ObservedState state = splitState(RoutingMode.CHEAPEST);
        AttackMenuSnapshot menu = menu(4_000, 300);

        AttackRoutingAdvice advice = advisor.advise(state, menu, 2_000);

        assertTrue(advice.available());
        assertEquals(List.of("HQ", "O1", "O2", "O3", "Goal"), advice.cheapest().route().path());
        assertEquals(List.of("HQ", "F1", "F2", "Goal"), advice.fastest().route().path());
        assertEquals(300, advice.cheapest().comparisonTimerSeconds());
        assertEquals(240, advice.fastest().comparisonTimerSeconds());
        assertEquals(4_000, advice.cheapest().comparisonCostEmeralds());
        assertEquals(11_560, advice.fastest().comparisonCostEmeralds());
        assertEquals(60, advice.timeSavedSeconds());
        assertEquals(7_560, advice.additionalCostEmeralds());
        assertEquals(AttackAdviceDecision.FASTEST_FASTER, advice.decision());
        assertTrue(advice.cheapest().observedCostEmeralds().isPresent());
        assertTrue(advice.fastest().observedCostEmeralds().isEmpty());
    }

    @Test
    void oneSecondSavingAlwaysRecommendsFastest() {
        assertEquals(AttackAdviceDecision.FASTEST_FASTER, AttackRoutingAdvisor.decide(1));
        assertEquals(AttackAdviceDecision.SAME_QUEUE_TIME, AttackRoutingAdvisor.decide(0));
    }

    @Test
    void timerMismatchMakesRecommendationUnavailable() {
        AttackRoutingAdvice advice = advisor.advise(
                splitState(RoutingMode.CHEAPEST), menu(4_000, 120), 2_000);

        assertFalse(advice.available());
        assertEquals(AttackAdviceDecision.UNAVAILABLE, advice.decision());
        assertTrue(advice.diagnostics().getLast().contains("shorter than the local shortest path"));
    }

    @Test
    void completeDisplayedRouteMismatchMakesRecommendationUnavailable() {
        AttackMenuSnapshot mismatched = new AttackMenuSnapshot(
                "Goal", OptionalLong.of(4_000), OptionalInt.of(300),
                List.of("HQ", "F1", "F2", "Goal"), 1_000, List.of());

        AttackRoutingAdvice advice = advisor.advise(
                splitState(RoutingMode.CHEAPEST), mismatched, 2_000);

        assertFalse(advice.available());
        assertTrue(advice.diagnostics().getLast().contains("route length"));
    }

    @Test
    void missingDisplayedCostIsUnavailable() {
        AttackMenuSnapshot incomplete = new AttackMenuSnapshot(
                "Goal", OptionalLong.empty(), OptionalInt.of(300), List.of(), 1_000, List.of());

        AttackRoutingAdvice advice = advisor.advise(
                splitState(RoutingMode.CHEAPEST), incomplete, 2_000);

        assertFalse(advice.available());
        assertTrue(advice.diagnostics().getLast().contains("required"));
    }

    @Test
    void targetAndHeadquartersAreExemptFromEstimatedTax() {
        ObservedState state = splitState(RoutingMode.CHEAPEST);

        assertEquals(4_000, AttackRoutingAdvisor.estimatedAttackCost(List.of("HQ", "Goal"), state, 4));
        assertEquals(6_800, AttackRoutingAdvisor.estimatedAttackCost(List.of("HQ", "F1", "Goal"), state, 4));
    }

    @Test
    void unknownModeIsInferredOnlyWhenTheTimerMatchesOneCandidate() {
        AttackRoutingAdvice advice = advisor.advise(splitState(null), menu(4_000, 240), 2_000);

        assertTrue(advice.available());
        assertEquals(RoutingMode.FASTEST, advice.resolvedRoutingMode());
        assertTrue(advice.routingModeInferred());
        assertFalse(advice.routingObservationNeeded());
        assertTrue(advice.fastest().observedTimerSeconds().isPresent());
    }

    @Test
    void aQueueLongerThanFastestIdentifiesCheapestDespiteFallbackRouteMismatch() {
        AttackMenuSnapshot displayed = new AttackMenuSnapshot(
                "Goal",
                OptionalLong.of(62_616),
                OptionalInt.of(360),
                List.of("HQ", "F1", "F2", "O1", "O2", "Goal"),
                1_000,
                List.of());

        AttackRoutingAdvice advice = advisor.advise(splitState(null), displayed, 2_000);

        assertTrue(advice.available());
        assertEquals(RoutingMode.CHEAPEST, advice.resolvedRoutingMode());
        assertTrue(advice.routingModeInferred());
        assertEquals(120, advice.timeSavedSeconds());
        assertEquals(displayed.observedRoute(), advice.cheapest().route().path());
        assertTrue(advice.diagnostics().stream().anyMatch(line -> line.contains("uniquely identifying Cheapest")));
    }

    @Test
    void ambiguousTimerPromptsForHqManagementInsteadOfGuessing() {
        AttackRoutingAdvice advice = advisor.advise(singleRouteState(), menu(4_000, 180), 2_000);

        assertFalse(advice.available());
        assertTrue(advice.routingObservationNeeded());
        assertTrue(advice.diagnostics().getLast().contains("HQ management"));
    }

    @Test
    void servicePersistsAUniqueTimerInferenceAsObservedRoutingEvidence() {
        ObservedStateRepository repository = new ObservedStateRepository(
                splitState(null), new ObservedStateMerger());
        SyllyConfigService settings = SyllyConfigService.open(
                new SyllyConfigStore(temporaryDirectory.resolve("settings.json")));
        AttackAdvisorService service = new AttackAdvisorService(repository, settings);

        service.observeMenu(
                "Attacking: Goal",
                List.of(
                        new AttackMenuEntry("Attack Cost", List.of("Price: 4,000 emeralds")),
                        new AttackMenuEntry("Queue Timer", List.of("Time to Start: 4m"))),
                2_000);

        assertTrue(repository.snapshot().routingMode().isKnown());
        assertEquals(RoutingMode.FASTEST, repository.snapshot().routingMode().value());
        assertEquals(EvidenceKind.DERIVED, repository.snapshot().routingMode().evidence().kind());
    }

    @Test
    void aLaterUniqueTimerCanRefreshPreviouslyDerivedRouting() {
        ObservedState initial = splitState(null);
        Evidence derived = new Evidence(
                EvidenceKind.DERIVED, 1_500, "attack-menu-timer", "routing-rules-2026-08-29", "old inference");
        ObservedState previouslyFastest = new ObservedState(
                initial.schemaVersion(),
                initial.revision(),
                initial.assembledAtEpochMillis(),
                initial.character(),
                initial.guild(),
                initial.hqTerritory(),
                ObservedValue.known(RoutingMode.FASTEST, derived),
                initial.territories());

        AttackRoutingAdvice advice = advisor.advise(previouslyFastest, menu(4_000, 300), 2_000);

        assertTrue(advice.available());
        assertEquals(RoutingMode.CHEAPEST, advice.resolvedRoutingMode());
        assertTrue(advice.routingModeInferred());
    }

    private static AttackMenuSnapshot menu(long cost, int timer) {
        return new AttackMenuSnapshot(
                "Goal", OptionalLong.of(cost), OptionalInt.of(timer), List.of(), 1_000, List.of());
    }

    private static ObservedState splitState(RoutingMode mode) {
        Map<String, TerritoryState> territories = new LinkedHashMap<>();
        add(territories, territory("HQ", OWN, "F1", "O1"));
        add(territories, territory("F1", ENEMY, "HQ", "F2"));
        add(territories, territory("F2", ENEMY, "F1", "Goal"));
        add(territories, territory("O1", OWN, "HQ", "O2"));
        add(territories, territory("O2", OWN, "O1", "O3"));
        add(territories, territory("O3", OWN, "O2", "Goal"));
        add(territories, territory("Goal", ENEMY, "F2", "O3"));
        return new ObservedState(
                1, 1, 1_000,
                ObservedValue.unknown("unused"),
                ObservedValue.known(new GuildIdentity("guild", "Guild", "TAG"), EVIDENCE),
                ObservedValue.known("HQ", EVIDENCE),
                mode == null ? ObservedValue.unknown("not observed") : ObservedValue.known(mode, EVIDENCE),
                territories);
    }

    private static ObservedState singleRouteState() {
        Map<String, TerritoryState> territories = new LinkedHashMap<>();
        add(territories, territory("HQ", OWN, "Middle"));
        add(territories, territory("Middle", OWN, "HQ", "Goal"));
        add(territories, territory("Goal", ENEMY, "Middle"));
        return new ObservedState(
                1, 1, 1_000,
                ObservedValue.unknown("unused"),
                ObservedValue.known(new GuildIdentity("guild", "Guild", "TAG"), EVIDENCE),
                ObservedValue.known("HQ", EVIDENCE),
                ObservedValue.unknown("not observed"),
                territories);
    }

    private static TerritoryState territory(String name, TerritoryOwner owner, String... links) {
        return new TerritoryState(
                name,
                ObservedValue.known(owner, EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(name.equals("HQ"), EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.known(List.of(links), EVIDENCE),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.unknown("unused"),
                ObservedValue.known(Map.of(), EVIDENCE),
                ObservedValue.known(List.of(), EVIDENCE));
    }

    private static void add(Map<String, TerritoryState> territories, TerritoryState territory) {
        territories.put(territory.name(), territory);
    }
}
