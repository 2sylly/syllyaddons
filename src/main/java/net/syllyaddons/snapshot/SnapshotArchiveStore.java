package net.syllyaddons.snapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class SnapshotArchiveStore {
    public static final String EXTENSION = ".tnsreco";
    private final SnapshotArchiveCodec codec;

    public SnapshotArchiveStore(SnapshotArchiveCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public Path exportAtomically(Path requestedDestination, SnapshotArchiveContent content)
            throws IOException, SnapshotFormatException {
        Path destination = withExtension(requestedDestination).toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        String encoded = codec.encode(content);
        Path temporary = Files.createTempFile(parent, "." + destination.getFileName(), ".tmp");
        try {
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8);
            moveAtomicallyWhenSupported(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return destination;
    }

    public ImportedSnapshotContext importReadOnly(Path source, long nowEpochMillis)
            throws IOException, SnapshotFormatException {
        Path normalized = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Snapshot file does not exist or is not a regular file: " + normalized);
        }
        long size = Files.size(normalized);
        if (size > SnapshotArchiveValidator.MAX_FILE_BYTES) {
            throw new SnapshotFormatException(".tnsreco file exceeds the 16 MiB limit");
        }
        SnapshotArchive archive = codec.decode(Files.readString(normalized, StandardCharsets.UTF_8));
        return new ImportedSnapshotContext(archive, normalized, nowEpochMillis);
    }

    static Path withExtension(Path requested) {
        Objects.requireNonNull(requested, "requested");
        String name = requested.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(EXTENSION)) return requested;
        return requested.resolveSibling(name + EXTENSION);
    }

    private static void moveAtomicallyWhenSupported(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
