package net.syllyaddons.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Creates an immutable adjacent copy before a supported on-disk schema migration is written. */
public final class SchemaMigrationBackup {
    public Path create(Path source, int storedSchema, int targetSchema, long nowEpochMillis) throws IOException {
        Path normalized = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IOException("Schema source is not a regular file");
        if (storedSchema <= 0 || targetSchema <= storedSchema || nowEpochMillis < 0) {
            throw new IllegalArgumentException("Schema backup requires a positive forward migration");
        }
        String name = normalized.getFileName() + ".schema-v" + storedSchema + "-to-v" + targetSchema
                + "-" + nowEpochMillis + ".bak";
        Path backup = normalized.resolveSibling(name);
        if (!Files.exists(backup)) Files.copy(normalized, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }
}
