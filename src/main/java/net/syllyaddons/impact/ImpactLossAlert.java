package net.syllyaddons.impact;

import java.util.Objects;

public record ImpactLossAlert(
        String territory,
        String capturedBy,
        ImpactSeverity severity,
        long detectedAtEpochMillis,
        long expiresAtEpochMillis,
        long baselineAgeMillis,
        long refreshWindowMillis,
        long baselineRevision,
        TerritoryImpactReport report) {
    public ImpactLossAlert {
        territory = Objects.requireNonNull(territory, "territory").strip();
        capturedBy = capturedBy == null || capturedBy.isBlank() ? "another guild" : capturedBy.strip();
        severity = Objects.requireNonNull(severity, "severity");
        report = Objects.requireNonNull(report, "report");
        if (territory.isEmpty() || detectedAtEpochMillis < 0 || expiresAtEpochMillis <= detectedAtEpochMillis
                || baselineAgeMillis < 0 || refreshWindowMillis < 0 || baselineRevision < 0) {
            throw new IllegalArgumentException("Invalid impact loss alert");
        }
    }

    public boolean expired(long nowEpochMillis) {
        return nowEpochMillis >= expiresAtEpochMillis;
    }
}
