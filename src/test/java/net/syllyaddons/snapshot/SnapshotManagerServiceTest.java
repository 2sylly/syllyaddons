package net.syllyaddons.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import net.syllyaddons.observation.ObservedStateMerger;
import net.syllyaddons.observation.ObservedStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotManagerServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void automaticSnapshotsAreThrottledAndPrunedToRetention() throws Exception {
        SnapshotManagerService manager = manager();

        assertTrue(manager.exportAutomaticIfDue(1_000_000, 2).isPresent());
        assertTrue(manager.exportAutomaticIfDue(1_001_000, 2).isEmpty());
        assertTrue(manager.exportAutomaticIfDue(1_400_000, 2).isPresent());
        assertTrue(manager.exportAutomaticIfDue(1_800_000, 2).isPresent());

        assertEquals(2, manager.listSnapshots().size());
        assertTrue(manager.listSnapshots().stream()
                .allMatch(file -> file.path().getFileName().toString().startsWith("auto-")));
    }

    @Test
    void managerComparesImportedDataWithoutReplacingRepositoryState() throws Exception {
        ObservedStateRepository repository = new ObservedStateRepository(
                SnapshotTestFixtures.state(8, 20, 6), new ObservedStateMerger());
        SnapshotManagerService manager = manager(repository);
        Path old = new SnapshotArchiveStore(new SnapshotArchiveCodec(new SnapshotArchiveValidator()))
                .exportAtomically(
                        temporaryDirectory.resolve("old"),
                        SnapshotTestFixtures.content(SnapshotTestFixtures.state(2, 5, 1), 900_000));

        ImportedSnapshotContext imported = manager.importReadOnly(old, 1_000_000);
        SnapshotComparison comparison = manager.compareWithCurrent(imported, 1_000_000);

        assertEquals(2, comparison.baselineRevision());
        assertEquals(8, comparison.currentRevision());
        assertEquals(8, repository.snapshot().revision());
    }

    private SnapshotManagerService manager() {
        return manager(new ObservedStateRepository(
                SnapshotTestFixtures.state(4, 10, 3), new ObservedStateMerger()));
    }

    private SnapshotManagerService manager(ObservedStateRepository repository) {
        return new SnapshotManagerService(
                temporaryDirectory.resolve("snapshots"),
                repository,
                new SnapshotArchiveStore(new SnapshotArchiveCodec(new SnapshotArchiveValidator())),
                new SnapshotCaptureService(new ObservedEconomyAnalyzer()),
                new SnapshotComparisonService(),
                Map.of("test", "1"));
    }
}
