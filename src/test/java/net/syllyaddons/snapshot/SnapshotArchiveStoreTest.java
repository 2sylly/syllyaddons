package net.syllyaddons.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotArchiveStoreTest {
    @TempDir
    Path temporaryDirectory;

    private final SnapshotArchiveValidator validator = new SnapshotArchiveValidator();
    private final SnapshotArchiveCodec codec = new SnapshotArchiveCodec(validator);
    private final SnapshotArchiveStore store = new SnapshotArchiveStore(codec);

    @Test
    void atomicExportThenReadOnlyImportPreservesTheEntirePayload() throws Exception {
        SnapshotArchiveContent content = SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000);

        Path exported = store.exportAtomically(temporaryDirectory.resolve("portable"), content);
        ImportedSnapshotContext imported = store.importReadOnly(exported, 3_000);

        assertEquals("portable.tnsreco", exported.getFileName().toString());
        assertEquals(content, imported.archive().content());
        assertTrue(imported.readOnly());
        assertTrue(imported.archive().checksumSha256().matches("[0-9a-f]{64}"));
        assertFalse(Files.exists(exported.resolveSibling(exported.getFileName() + ".tmp")));
    }

    @Test
    void checksumDetectsEditedContent() throws Exception {
        String valid = codec.encode(SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000));
        String edited = valid.replaceFirst("Fixture Guild", "Changed Guild");

        SnapshotFormatException exception = assertThrows(SnapshotFormatException.class, () -> codec.decode(edited));

        assertTrue(exception.getMessage().contains("checksum"));
    }

    @Test
    void futureVersionFailsWithAUsefulMessageBeforeMigrationExists() throws Exception {
        String valid = codec.encode(SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000));
        String future = valid.replaceFirst("\\\"formatVersion\\\": 1", "\\\"formatVersion\\\": 2");

        SnapshotFormatException exception = assertThrows(SnapshotFormatException.class, () -> codec.decode(future));

        assertEquals("Unsupported .tnsreco format version 2", exception.getMessage());
    }

    @Test
    void validatorRejectsUnknownLinksBeforeExport() {
        SnapshotArchiveContent original = SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000);
        TerritoryState mine = original.payload().observed().territories().get("Mine");
        TerritoryState invalidMine = new TerritoryState(
                mine.name(),
                mine.owner(),
                mine.acquiredAtEpochMillis(),
                mine.headquarters(),
                mine.bounds(),
                ObservedValue.known(List.of("Missing"), mine.links().evidence()),
                mine.resources(),
                mine.treasury(),
                mine.treasuryBonusPercent(),
                mine.defences(),
                mine.upgrades(),
                mine.alerts());
        var observed = original.payload().observed();
        var invalidObserved = new net.syllyaddons.domain.EcoSnapshot(
                observed.schemaVersion(),
                observed.createdAtEpochMillis(),
                observed.sourceRevision(),
                observed.guild(),
                observed.hqTerritory(),
                observed.routingMode(),
                java.util.Map.of("Mine", invalidMine, "HQ", observed.territories().get("HQ")));
        SnapshotArchiveContent invalid = new SnapshotArchiveContent(
                original.formatVersion(),
                original.createdAtEpochMillis(),
                original.sourceVersions(),
                new SnapshotPayload(invalidObserved, null, List.of()));

        SnapshotFormatException exception = assertThrows(SnapshotFormatException.class, () -> codec.encode(invalid));

        assertTrue(exception.getMessage().contains("unknown territory Missing"));
    }

    @Test
    void oversizedImportIsRejectedBeforeJsonParsing() throws Exception {
        Path oversized = temporaryDirectory.resolve("oversized.tnsreco");
        byte[] data = new byte[SnapshotArchiveValidator.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(data, (byte) ' ');
        Files.write(oversized, data);

        SnapshotFormatException exception = assertThrows(
                SnapshotFormatException.class, () -> store.importReadOnly(oversized, 3_000));

        assertTrue(exception.getMessage().contains("16 MiB"));
    }

    @Test
    void validChecksumStillCannotSmuggleAnUnknownEnum() throws Exception {
        String valid = codec.encode(SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000));
        JsonObject envelope = JsonParser.parseString(valid).getAsJsonObject();
        envelope.getAsJsonObject("payload")
                .getAsJsonObject("observed")
                .getAsJsonObject("routingMode")
                .addProperty("value", "TELEPORT");

        SnapshotFormatException exception =
                assertThrows(SnapshotFormatException.class, () -> codec.decode(resign(envelope)));

        assertTrue(exception.getMessage().contains("Invalid .tnsreco JSON"));
    }

    @Test
    void validChecksumStillCannotSmuggleAnUnboundedNumber() throws Exception {
        String valid = codec.encode(SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000));
        JsonObject envelope = JsonParser.parseString(valid).getAsJsonObject();
        envelope.getAsJsonObject("payload")
                .getAsJsonObject("observed")
                .getAsJsonObject("territories")
                .getAsJsonObject("Mine")
                .getAsJsonObject("resources")
                .getAsJsonObject("value")
                .getAsJsonObject("ORE")
                .addProperty("generationPerHour", Long.MAX_VALUE);

        SnapshotFormatException exception =
                assertThrows(SnapshotFormatException.class, () -> codec.decode(resign(envelope)));

        assertTrue(exception.getMessage().contains("generation is outside the supported range"));
    }

    @Test
    void importingNeverMutatesTheLiveRepository() throws Exception {
        ObservedState live = SnapshotTestFixtures.state(8, 25, 7);
        var repository = new net.syllyaddons.observation.ObservedStateRepository(
                live, new net.syllyaddons.observation.ObservedStateMerger());
        Path exported = store.exportAtomically(
                temporaryDirectory.resolve("old.tnsreco"),
                SnapshotTestFixtures.content(SnapshotTestFixtures.state(2, 5, 1), 2_000));

        ImportedSnapshotContext ignored = store.importReadOnly(exported, 3_000);

        assertEquals(live, repository.snapshot());
    }

    private String resign(JsonObject envelope) throws Exception {
        JsonObject body = envelope.deepCopy();
        body.remove("checksumSha256");
        var method = SnapshotArchiveCodec.class.getDeclaredMethod("checksum", JsonObject.class);
        method.setAccessible(true);
        String checksum = (String) method.invoke(codec, body);
        envelope.addProperty("checksumSha256", checksum);
        return envelope.toString();
    }
}
