package net.syllyaddons.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AttackAdvisorOverlayControllerTest {
    @Test
    void formatsTheConfirmationSavingInPlainWords() {
        assertEquals("1 second", AttackAdvisorOverlayController.durationWords(1));
        assertEquals("3 minutes", AttackAdvisorOverlayController.durationWords(180));
        assertEquals("3 minutes 1 second", AttackAdvisorOverlayController.durationWords(181));
    }

    @Test
    void oneShotTestGuardBlocksExactlyOneClickBeforeItExpires() {
        OneShotClickGuard guard = new OneShotClickGuard(60_000);

        assertFalse(guard.isArmed(1_000));
        guard.arm(1_000);
        assertTrue(guard.isArmed(60_999));
        assertTrue(guard.consume(60_999));
        assertFalse(guard.consume(60_999));
    }

    @Test
    void oneShotTestGuardFailsOpenAfterItsTimeout() {
        OneShotClickGuard guard = new OneShotClickGuard(60_000);

        guard.arm(1_000);
        assertFalse(guard.consume(61_000));
        assertFalse(guard.isArmed(61_000));
    }
}
