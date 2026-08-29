package net.syllyaddons.domain;

import java.util.Objects;

public record Evidence(
        EvidenceKind kind, long observedAtEpochMillis, String source, String sourceVersion, String note) {
    public Evidence {
        Objects.requireNonNull(kind, "kind");
        if (observedAtEpochMillis < 0) {
            throw new IllegalArgumentException("observedAtEpochMillis must be non-negative");
        }
        source = normalize(source, "unknown");
        sourceVersion = normalize(sourceVersion, "unknown");
        note = note == null ? "" : note.strip();
    }

    public static Evidence unknown(String note) {
        return new Evidence(EvidenceKind.UNKNOWN, 0, "not-observed", "none", note);
    }

    public boolean isAtLeastAsReliableAs(Evidence other) {
        return kind.reliability() >= other.kind.reliability();
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.strip();
    }
}
