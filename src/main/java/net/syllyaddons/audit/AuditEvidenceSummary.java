package net.syllyaddons.audit;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.EvidenceKind;

public record AuditEvidenceSummary(
        EvidenceKind weakestKind,
        long oldestObservedAtEpochMillis,
        long newestObservedAtEpochMillis,
        long ageAtAuditMillis,
        List<String> sources,
        List<String> sourceVersions) {
    public AuditEvidenceSummary {
        weakestKind = Objects.requireNonNull(weakestKind, "weakestKind");
        if (oldestObservedAtEpochMillis < 0 || newestObservedAtEpochMillis < 0 || ageAtAuditMillis < 0) {
            throw new IllegalArgumentException("Evidence timestamps must be non-negative");
        }
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        sourceVersions = List.copyOf(Objects.requireNonNull(sourceVersions, "sourceVersions"));
    }
}
