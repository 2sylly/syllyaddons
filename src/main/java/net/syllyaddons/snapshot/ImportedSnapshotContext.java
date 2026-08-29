package net.syllyaddons.snapshot;

import java.nio.file.Path;
import java.util.Objects;

/** Deliberately exposes data only; it has no reference to the live observation repository. */
public record ImportedSnapshotContext(SnapshotArchive archive, Path sourcePath, long importedAtEpochMillis) {
    public ImportedSnapshotContext {
        archive = Objects.requireNonNull(archive, "archive");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").toAbsolutePath().normalize();
        if (importedAtEpochMillis < 0) throw new IllegalArgumentException("importedAtEpochMillis must be non-negative");
    }

    public boolean readOnly() {
        return true;
    }

    public SnapshotPayload payload() {
        return archive.content().payload();
    }
}
