package net.syllyaddons.snapshot;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record SnapshotArchiveContent(
        int formatVersion,
        long createdAtEpochMillis,
        Map<String, String> sourceVersions,
        SnapshotPayload payload) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public SnapshotArchiveContent {
        if (formatVersion <= 0) throw new IllegalArgumentException("formatVersion must be positive");
        if (createdAtEpochMillis < 0) throw new IllegalArgumentException("createdAtEpochMillis must be non-negative");
        sourceVersions = Collections.unmodifiableMap(new TreeMap<>(
                Objects.requireNonNull(sourceVersions, "sourceVersions")));
        payload = Objects.requireNonNull(payload, "payload");
    }
}
