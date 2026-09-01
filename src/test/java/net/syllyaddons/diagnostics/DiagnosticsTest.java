package net.syllyaddons.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {
    @Test
    void structuredLoggerOnlyAcceptsNonIdentifyingFields() {
        String line = StructuredDiagnosticLogger.format(
                "subsystem_started",
                Map.of("subsystem", Subsystem.OBSERVATION, "status", "healthy", "revision", 42));

        assertEquals(
                "{\"event\":\"subsystem_started\",\"revision\":42,\"status\":\"healthy\",\"subsystem\":\"OBSERVATION\"}",
                line);
        assertThrows(IllegalArgumentException.class, () -> StructuredDiagnosticLogger.format(
                "unsafe_event", Map.of("playerName", "2sylly")));
    }

    @Test
    void calculationDisagreementIsDistinctFromOrdinaryMissingInput() {
        assertTrue(OperationsHealthService.containsDisagreement(List.of("Queued timer mismatch")));
        assertTrue(OperationsHealthService.containsDisagreement(List.of("Independent revalidation disagreed")));
        assertFalse(OperationsHealthService.containsDisagreement(List.of("HQ storage has not been observed")));
    }

    @Test
    void oneSubsystemFailureDoesNotChangeAnyOtherStatus() {
        SubsystemHealthRegistry registry = new SubsystemHealthRegistry();
        registry.healthy(Subsystem.OBSERVATION, "attached");
        registry.healthy(Subsystem.ROUTING_ADVISOR, "attached");
        registry.failed(
                Subsystem.SPELL_PROFILES,
                DiagnosticCategory.INTEGRATION_FAILURE,
                "failed closed",
                "NoSuchMethodError");

        assertEquals(SubsystemHealthStatus.FAILED, registry.get(Subsystem.SPELL_PROFILES).status());
        assertEquals(SubsystemHealthStatus.HEALTHY, registry.get(Subsystem.OBSERVATION).status());
        assertEquals(SubsystemHealthStatus.HEALTHY, registry.get(Subsystem.ROUTING_ADVISOR).status());
    }
}
