package net.syllyaddons.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AttackAdvisorOverlayControllerTest {
    @Test
    void formatsTheConfirmationSavingInPlainWords() {
        assertEquals("1 second", AttackAdvisorOverlayController.durationWords(1));
        assertEquals("3 minutes", AttackAdvisorOverlayController.durationWords(180));
        assertEquals("3 minutes 1 second", AttackAdvisorOverlayController.durationWords(181));
    }
}
