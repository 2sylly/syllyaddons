package net.syllyaddons.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigrationBackupTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAnAdjacentImmutableCopyBeforeForwardMigration() throws Exception {
        Path source = temporaryDirectory.resolve("settings.json");
        Files.writeString(source, "{\"schemaVersion\":1}");

        Path backup = new SchemaMigrationBackup().create(source, 1, 2, 1234);
        Files.writeString(source, "{\"schemaVersion\":2}");

        assertEquals(
                temporaryDirectory.resolve("settings.json.schema-v1-to-v2-1234.bak"),
                backup);
        assertTrue(Files.isRegularFile(backup));
        assertEquals("{\"schemaVersion\":1}", Files.readString(backup));
        assertThrows(IllegalArgumentException.class, () ->
                new SchemaMigrationBackup().create(source, 2, 2, 1234));
    }
}
