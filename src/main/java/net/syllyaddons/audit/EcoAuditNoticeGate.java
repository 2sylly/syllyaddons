package net.syllyaddons.audit;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Collapses a report into one cooldown-controlled chat notice. */
public final class EcoAuditNoticeGate {
    private String lastFingerprint = "";
    private long lastNoticeAtEpochMillis;

    public synchronized Optional<String> next(AuditReport report, long nowEpochMillis, int cooldownSeconds) {
        List<AuditFinding> notable = report.findings().stream()
                .filter(value -> value.severity() != AuditSeverity.INFO)
                .sorted(Comparator.comparing(AuditFinding::severity).reversed().thenComparing(AuditFinding::title))
                .toList();
        if (notable.isEmpty()) {
            lastFingerprint = "";
            return Optional.empty();
        }
        String fingerprint = notable.stream()
                .map(value -> value.rootCauseKey() + ":" + value.severity())
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
        long cooldownMillis = Math.max(0L, cooldownSeconds * 1_000L);
        if (fingerprint.equals(lastFingerprint) && nowEpochMillis - lastNoticeAtEpochMillis < cooldownMillis) {
            return Optional.empty();
        }
        lastFingerprint = fingerprint;
        lastNoticeAtEpochMillis = nowEpochMillis;
        AuditFinding first = notable.getFirst();
        String more = notable.size() == 1 ? "" : " (+" + (notable.size() - 1) + " more)";
        return Optional.of("Eco audit: " + first.title() + more + ". F6 → Eco Auditor.");
    }
}
