package net.syllyaddons.audit;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AuditFinding(
        String rootCauseKey,
        Set<AuditFindingType> categories,
        AuditSeverity severity,
        String title,
        String summary,
        List<AuditCalculation> calculations,
        List<String> routeFacts,
        List<String> affectedTerritories,
        AuditEvidenceSummary evidence,
        List<String> missingInputs,
        List<AuditProvenanceReference> provenance) {
    public AuditFinding {
        rootCauseKey = Objects.requireNonNull(rootCauseKey, "rootCauseKey").strip();
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        severity = Objects.requireNonNull(severity, "severity");
        title = Objects.requireNonNull(title, "title").strip();
        summary = Objects.requireNonNull(summary, "summary").strip();
        calculations = List.copyOf(Objects.requireNonNull(calculations, "calculations"));
        routeFacts = List.copyOf(Objects.requireNonNull(routeFacts, "routeFacts"));
        affectedTerritories = List.copyOf(Objects.requireNonNull(affectedTerritories, "affectedTerritories"));
        evidence = Objects.requireNonNull(evidence, "evidence");
        missingInputs = List.copyOf(Objects.requireNonNull(missingInputs, "missingInputs"));
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
        if (rootCauseKey.isEmpty() || categories.isEmpty() || title.isEmpty() || summary.isEmpty()) {
            throw new IllegalArgumentException("Finding identity and text must not be empty");
        }
    }
}
