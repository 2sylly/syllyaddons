package net.syllyaddons.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ObservedValueTest {
    @Test
    void newerWeakerEvidenceDoesNotReplaceLocalExactValue() {
        ObservedValue<String> local = ObservedValue.known(
                "local", new Evidence(EvidenceKind.LOCAL_EXACT, 100, "menu", "1", ""));
        ObservedValue<String> estimate = ObservedValue.known(
                "estimate", new Evidence(EvidenceKind.ESTIMATED, 200, "estimate", "1", ""));

        assertEquals(local, local.merge(estimate));
    }

    @Test
    void newerEqualEvidenceReplacesValue() {
        ObservedValue<String> oldValue = ObservedValue.known(
                "old", new Evidence(EvidenceKind.PUBLIC_EXACT, 100, "api", "1", ""));
        ObservedValue<String> newValue = ObservedValue.known(
                "new", new Evidence(EvidenceKind.PUBLIC_EXACT, 200, "api", "1", ""));

        assertEquals(newValue, oldValue.merge(newValue));
    }

    @Test
    void unknownNeverClearsKnownValueDuringNormalMerge() {
        ObservedValue<String> known = ObservedValue.known(
                "value", new Evidence(EvidenceKind.PUBLIC_EXACT, 100, "api", "1", ""));

        assertEquals(known, known.merge(ObservedValue.unknown("missing")));
    }
}
