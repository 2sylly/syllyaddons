package net.syllyaddons.snapshot;

import java.util.Objects;

public record SnapshotArchive(SnapshotArchiveContent content, String checksumSha256) {
    public SnapshotArchive {
        content = Objects.requireNonNull(content, "content");
        checksumSha256 = Objects.requireNonNull(checksumSha256, "checksumSha256");
    }
}
