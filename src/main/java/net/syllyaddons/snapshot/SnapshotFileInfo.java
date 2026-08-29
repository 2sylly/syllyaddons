package net.syllyaddons.snapshot;

import java.nio.file.Path;

public record SnapshotFileInfo(Path path, long sizeBytes, long modifiedAtEpochMillis) {
    public SnapshotFileInfo {
        path = path.toAbsolutePath().normalize();
    }
}
