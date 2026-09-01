package net.syllyaddons.diagnostics;

import java.util.List;
import java.util.Objects;

public record SubsystemHealth(
        Subsystem subsystem,
        SubsystemHealthStatus status,
        DiagnosticCategory category,
        String summary,
        List<String> diagnostics) {
    public SubsystemHealth {
        Objects.requireNonNull(subsystem, "subsystem");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(category, "category");
        summary = summary == null || summary.isBlank() ? status.name() : summary.strip();
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public static SubsystemHealth waiting(Subsystem subsystem, String summary) {
        return new SubsystemHealth(
                subsystem, SubsystemHealthStatus.WAITING, DiagnosticCategory.WAITING, summary, List.of());
    }

    public static SubsystemHealth healthy(Subsystem subsystem, String summary) {
        return new SubsystemHealth(
                subsystem, SubsystemHealthStatus.HEALTHY, DiagnosticCategory.HEALTHY, summary, List.of());
    }
}
