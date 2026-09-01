package net.syllyaddons.diagnostics;

import java.util.List;
import java.util.Objects;

public record SubsystemHealthReport(long evaluatedAtEpochMillis, long stateRevision, List<SubsystemHealth> subsystems) {
    public SubsystemHealthReport {
        if (evaluatedAtEpochMillis < 0 || stateRevision < 0) throw new IllegalArgumentException("Invalid report metadata");
        subsystems = List.copyOf(Objects.requireNonNull(subsystems, "subsystems"));
    }

    public long failedCount() {
        return subsystems.stream().filter(value -> value.status() == SubsystemHealthStatus.FAILED).count();
    }

    public long degradedCount() {
        return subsystems.stream().filter(value -> value.status() == SubsystemHealthStatus.DEGRADED).count();
    }
}
