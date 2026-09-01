package net.syllyaddons.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;
import org.junit.jupiter.api.Test;

class TerritoryMapFocusCalculatorTest {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "map focus fixture");
    private final TerritoryMapFocusCalculator calculator = new TerritoryMapFocusCalculator();

    @Test
    void centersCombinedOwnedBoundsAndSelectsLargestFittingZoom() {
        var focus = TerritoryMapFocusCalculator.calculate(
                        List.of(
                                new TerritoryBounds(0, 100, 100, 200),
                                new TerritoryBounds(300, -100, 400, 0)),
                        500,
                        400,
                        20,
                        level -> level)
                .orElseThrow();

        assertEquals(200, focus.centerX());
        assertEquals(50, focus.centerZ());
        assertEquals(1.15, focus.zoomLevel(), 0.0001);
        assertEquals(2, focus.territoryCount());
    }

    @Test
    void clampsToClosestAndFarthestWynntilsZoomLevels() {
        var far = TerritoryMapFocusCalculator.calculate(
                        List.of(new TerritoryBounds(0, 0, 10_000, 10_000)),
                        100,
                        100,
                        0,
                        level -> level)
                .orElseThrow();
        var close = TerritoryMapFocusCalculator.calculate(
                        List.of(new TerritoryBounds(0, 0, 1, 1)),
                        1_000,
                        1_000,
                        0,
                        level -> level)
                .orElseThrow();

        assertEquals(1, far.zoomLevel());
        assertEquals(100, close.zoomLevel());
    }

    @Test
    void includesOnlyCurrentGuildAndWaitsForEveryOwnedBound() {
        ObservedState complete = state(
                territory("West", "Sylly", new TerritoryBounds(-200, -100, -100, 0)),
                territory("East", "Sylly", new TerritoryBounds(100, 0, 300, 100)),
                territory("Enemy", "Other", new TerritoryBounds(10_000, 10_000, 20_000, 20_000)));
        var focus = calculator.calculate(complete, 600, 400, level -> level / 10.0).orElseThrow();

        assertEquals(50, focus.centerX());
        assertEquals(0, focus.centerZ());
        assertEquals(2, focus.territoryCount());

        TerritoryState missing = TerritoryState.empty("Missing");
        missing = new TerritoryState(
                missing.name(),
                ObservedValue.known(new TerritoryOwner("", "Sylly", "SYL"), EVIDENCE),
                missing.acquiredAtEpochMillis(), missing.headquarters(), missing.bounds(), missing.links(),
                missing.resources(), missing.treasury(), missing.treasuryBonusPercent(), missing.defences(),
                missing.upgrades(), missing.alerts());
        assertTrue(calculator.calculate(state(missing), 600, 400, level -> level).isEmpty());
    }

    @Test
    void returnsEmptyWhenGuildOrOwnedTerritoriesAreUnavailable() {
        ObservedState withoutGuild = new ObservedState(
                1, 1, 1_000, ObservedValue.unknown("character"), ObservedValue.unknown("guild"),
                ObservedValue.unknown("hq"), ObservedValue.unknown("routing"), Map.of());
        assertTrue(calculator.calculate(withoutGuild, 600, 400, level -> level).isEmpty());
        assertFalse(calculator.calculate(
                        state(territory("Enemy", "Other", new TerritoryBounds(0, 0, 100, 100))),
                        600, 400, level -> level)
                .isPresent());
    }

    private static ObservedState state(TerritoryState... territories) {
        var map = new java.util.LinkedHashMap<String, TerritoryState>();
        for (TerritoryState territory : territories) map.put(territory.name(), territory);
        return new ObservedState(
                1, 1, 1_000,
                ObservedValue.unknown("character"),
                ObservedValue.known(new GuildIdentity("", "Sylly", "SYL"), EVIDENCE),
                ObservedValue.unknown("no HQ"),
                ObservedValue.unknown("routing"),
                map);
    }

    private static TerritoryState territory(String name, String owner, TerritoryBounds bounds) {
        TerritoryState empty = TerritoryState.empty(name);
        return new TerritoryState(
                name,
                ObservedValue.known(new TerritoryOwner("", owner, ""), EVIDENCE),
                empty.acquiredAtEpochMillis(),
                empty.headquarters(),
                ObservedValue.known(bounds, EVIDENCE),
                empty.links(), empty.resources(), empty.treasury(), empty.treasuryBonusPercent(), empty.defences(),
                empty.upgrades(), empty.alerts());
    }
}
