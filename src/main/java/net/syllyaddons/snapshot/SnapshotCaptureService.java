package net.syllyaddons.snapshot;

import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ObservedState;

public final class SnapshotCaptureService {
    private final ObservedEconomyAnalyzer analyzer;

    public SnapshotCaptureService(ObservedEconomyAnalyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
    }

    public SnapshotArchiveContent capture(
            ObservedState state, long createdAtEpochMillis, Map<String, String> sourceVersions) {
        Objects.requireNonNull(state, "state");
        return new SnapshotArchiveContent(
                SnapshotArchiveContent.CURRENT_FORMAT_VERSION,
                createdAtEpochMillis,
                sourceVersions,
                analyzer.analyze(state, createdAtEpochMillis));
    }
}
