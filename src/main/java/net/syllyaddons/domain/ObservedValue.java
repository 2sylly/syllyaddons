package net.syllyaddons.domain;

import java.util.Objects;

public record ObservedValue<T>(T value, Evidence evidence) {
    public ObservedValue {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.kind() == EvidenceKind.UNKNOWN && value != null) {
            throw new IllegalArgumentException("Unknown evidence cannot carry a value");
        }
        if (evidence.kind() != EvidenceKind.UNKNOWN && value == null) {
            throw new IllegalArgumentException("Known evidence must carry a value");
        }
    }

    public static <T> ObservedValue<T> known(T value, Evidence evidence) {
        return new ObservedValue<>(Objects.requireNonNull(value, "value"), evidence);
    }

    public static <T> ObservedValue<T> unknown(String note) {
        return new ObservedValue<>(null, Evidence.unknown(note));
    }

    public boolean isKnown() {
        return value != null;
    }

    /**
     * Merges two observations conservatively. An incoming value may not replace a value that is newer or backed by
     * stronger evidence. This deliberately retains an older local scan instead of silently replacing it with a newer
     * estimate; freshness reporting is responsible for marking the retained scan stale.
     */
    public ObservedValue<T> merge(ObservedValue<T> incoming) {
        Objects.requireNonNull(incoming, "incoming");

        if (!incoming.isKnown()) return this;
        if (!isKnown()) return incoming;
        if (!incoming.evidence().isAtLeastAsReliableAs(evidence)) return this;
        if (incoming.evidence().observedAtEpochMillis() < evidence.observedAtEpochMillis()) return this;

        return incoming;
    }
}
