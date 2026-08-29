package net.syllyaddons.audit;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.routing.RouteDiagnostic;

public record AuditReport(
        String rulesVersion,
        long auditedAtEpochMillis,
        long sourceRevision,
        List<AuditFinding> findings,
        List<RouteDiagnostic> diagnostics) {
    public AuditReport {
        rulesVersion = Objects.requireNonNull(rulesVersion, "rulesVersion").strip();
        if (auditedAtEpochMillis < 0 || sourceRevision < 0 || rulesVersion.isEmpty()) {
            throw new IllegalArgumentException("Invalid audit report metadata");
        }
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
