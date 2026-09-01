package net.syllyaddons.diagnostics;

import java.util.EnumMap;
import java.util.Map;

/** Runtime startup registry. A failure in one listener never overwrites another subsystem's state. */
public final class SubsystemHealthRegistry {
    private final EnumMap<Subsystem, SubsystemHealth> health = new EnumMap<>(Subsystem.class);

    public SubsystemHealthRegistry() {
        for (Subsystem subsystem : Subsystem.values()) {
            health.put(subsystem, SubsystemHealth.waiting(subsystem, "Not started yet"));
        }
    }

    public synchronized void healthy(Subsystem subsystem, String summary) {
        health.put(subsystem, SubsystemHealth.healthy(subsystem, summary));
    }

    public synchronized void waiting(Subsystem subsystem, String summary) {
        health.put(subsystem, SubsystemHealth.waiting(subsystem, summary));
    }

    public synchronized void failed(
            Subsystem subsystem, DiagnosticCategory category, String summary, String diagnostic) {
        if (category != DiagnosticCategory.INTEGRATION_FAILURE && category != DiagnosticCategory.INTERNAL_FAILURE) {
            throw new IllegalArgumentException("Runtime failure must be integration or internal");
        }
        health.put(subsystem, new SubsystemHealth(
                subsystem,
                SubsystemHealthStatus.FAILED,
                category,
                summary,
                diagnostic == null || diagnostic.isBlank() ? java.util.List.of() : java.util.List.of(diagnostic)));
    }

    public synchronized SubsystemHealth get(Subsystem subsystem) {
        return health.get(subsystem);
    }

    public synchronized Map<Subsystem, SubsystemHealth> snapshot() {
        return Map.copyOf(health);
    }
}
