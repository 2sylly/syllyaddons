package net.syllyaddons.advisor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AttackButtonDetectorTest {
    private final AttackButtonDetector detector = new AttackButtonDetector();

    @Test
    void recognizesTheLiveAttackItemWithoutDependingOnItsSlot() {
        assertTrue(detector.matches(new AttackMenuEntry(
                "Attack with your Guild's emeralds",
                List.of("Time to Start: 20m"))));
    }

    @Test
    void rejectsInformationAndUnrelatedInventoryItems() {
        assertFalse(detector.matches(new AttackMenuEntry("Attack Route", List.of("Time to Start: 20m"))));
        assertFalse(detector.matches(new AttackMenuEntry("Emerald Treasury", List.of("Price: 62616"))));
    }

    @Test
    void exposesQueueTimerRecognitionForThePinnedLiveSlotFallback() {
        assertTrue(detector.hasQueueTimer(new AttackMenuEntry("Rewritten action", List.of("Time to Start: 6m"))));
        assertFalse(detector.hasQueueTimer(new AttackMenuEntry("Rewritten action", List.of("Price: 4000"))));
    }
}
