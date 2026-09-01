package net.syllyaddons.compat.wynntils.v4_2_9;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldTransitionPlanTest {
    @Test
    void leavingWorldClearsSession() {
        WorldTransitionPlan plan = WorldTransitionPlan.between(true, false);

        assertTrue(plan.clearSession());
        assertFalse(plan.recaptureSession());
        assertFalse(plan.refreshTerritories());
    }

    @Test
    void enteringWorldRecapturesSameCharacterAndRefreshesTerritories() {
        WorldTransitionPlan plan = WorldTransitionPlan.between(false, true);

        assertFalse(plan.clearSession());
        assertTrue(plan.recaptureSession());
        assertTrue(plan.refreshTerritories());
    }

    @Test
    void unchangedWorldStateDoesNothing() {
        WorldTransitionPlan plan = WorldTransitionPlan.between(true, true);

        assertFalse(plan.clearSession());
        assertFalse(plan.recaptureSession());
        assertFalse(plan.refreshTerritories());
    }
}
