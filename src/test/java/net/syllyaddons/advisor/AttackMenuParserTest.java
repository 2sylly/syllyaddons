package net.syllyaddons.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttackMenuParserTest {
    private final AttackMenuParser parser = new AttackMenuParser();

    @Test
    void parsesTargetTimerAndOrderedRouteWithoutClickingAnything() {
        AttackMenuSnapshot snapshot = parser.parse(
                "Attacking: Goal",
                List.of(
                        new AttackMenuEntry("Attack Cost", List.of("Price: 2 EBs")),
                        new AttackMenuEntry("Queue Timer", List.of("Time: 2m 30s")),
                        new AttackMenuEntry("Attack Route", List.of("HQ → Middle → Goal"))),
                Set.of("HQ", "Middle", "Goal"),
                1_000);

        assertEquals("Goal", snapshot.target());
        assertEquals(150, snapshot.observedTimerSeconds().orElseThrow());
        assertEquals(List.of("HQ", "Middle", "Goal"), snapshot.observedRoute());
        assertTrue(snapshot.hasRequiredInputs());
    }

    @Test
    void supportsClockTimers() {
        AttackMenuSnapshot snapshot = parser.parse(
                "Attacking: goal",
                List.of(
                        new AttackMenuEntry("Cost", List.of("3 Liquid Emeralds")),
                        new AttackMenuEntry("Duration", List.of("03:07"))),
                Set.of("Goal"),
                1_000);

        assertEquals("Goal", snapshot.target());
        assertEquals(187, snapshot.observedTimerSeconds().orElseThrow());
    }

    @Test
    void reportsMissingInputsInsteadOfInventingThem() {
        AttackMenuSnapshot snapshot = parser.parse(
                "Attacking: Goal", List.of(new AttackMenuEntry("Attack", List.of("Left-click to queue"))),
                Set.of("Goal"), 1_000);

        assertFalse(snapshot.hasRequiredInputs());
        assertEquals(1, snapshot.diagnostics().size());
    }

    @Test
    void overlappingTerritoryNamesDoNotCreatePhantomRouteSteps() {
        AttackMenuSnapshot snapshot = parser.parse(
                "Attacking: Cinfras Entrance",
                List.of(
                        new AttackMenuEntry("Cost", List.of("4,000 Emeralds")),
                        new AttackMenuEntry("Timer", List.of("3 minutes")),
                        new AttackMenuEntry("Route", List.of("HQ → Cinfras Entrance"))),
                Set.of("HQ", "Cinfras", "Cinfras Entrance"),
                1_000);

        assertEquals(List.of("HQ", "Cinfras Entrance"), snapshot.observedRoute());
    }

    @Test
    void parsesTheLiveAttackItemLayoutAndRouteGlyphs() {
        AttackMenuSnapshot snapshot = parser.parse(
                "Attacking: Illuminant Path",
                List.of(new AttackMenuEntry(
                        "Attack with your Guild's emeralds",
                        List.of(
                                "Territory Defences: Very High",
                                "➜ Unicorn Trail",
                                "✖ Waterfall Cave",
                                "✔ Illuminant Path",
                                "Price: ✖ 62616",
                                "Time to Start: 20m"))),
                Set.of("Unicorn Trail", "Waterfall Cave", "Illuminant Path"),
                1_000);

        assertEquals(1_200, snapshot.observedTimerSeconds().orElseThrow());
        assertEquals(List.of("Unicorn Trail", "Waterfall Cave", "Illuminant Path"), snapshot.observedRoute());
    }
}
