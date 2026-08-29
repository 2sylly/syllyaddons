package net.syllyaddons.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.observation.ObservedStateRepository;

public final class SnapshotManagerService {
    private static final long AUTOMATIC_INTERVAL_MILLIS = 5 * 60 * 1_000L;
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final Path snapshotDirectory;
    private final ObservedStateRepository repository;
    private final SnapshotArchiveStore store;
    private final SnapshotCaptureService captureService;
    private final SnapshotComparisonService comparisonService;
    private final Map<String, String> sourceVersions;
    private final AtomicLong lastAutomaticSnapshot = new AtomicLong();

    public SnapshotManagerService(
            Path snapshotDirectory,
            ObservedStateRepository repository,
            SnapshotArchiveStore store,
            SnapshotCaptureService captureService,
            SnapshotComparisonService comparisonService,
            Map<String, String> sourceVersions) {
        this.snapshotDirectory = Objects.requireNonNull(snapshotDirectory, "snapshotDirectory")
                .toAbsolutePath()
                .normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.store = Objects.requireNonNull(store, "store");
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService");
        this.sourceVersions = Map.copyOf(Objects.requireNonNull(sourceVersions, "sourceVersions"));
    }

    public Path exportCurrent(long nowEpochMillis) throws IOException, SnapshotFormatException {
        ObservedState state = repository.snapshot();
        Path destination = snapshotDirectory.resolve(
                "snapshot-" + FILE_TIME.format(Instant.ofEpochMilli(nowEpochMillis)) + "-r" + state.revision());
        return exportCurrent(destination, nowEpochMillis);
    }

    public Path exportCurrent(Path destination, long nowEpochMillis) throws IOException, SnapshotFormatException {
        SnapshotArchiveContent content =
                captureService.capture(repository.snapshot(), nowEpochMillis, sourceVersions);
        return store.exportAtomically(destination, content);
    }

    public Optional<Path> exportAutomaticIfDue(long nowEpochMillis, int retention)
            throws IOException, SnapshotFormatException {
        ObservedState state = repository.snapshot();
        if (state.territories().isEmpty()) return Optional.empty();
        long previous = lastAutomaticSnapshot.get();
        if (nowEpochMillis - previous < AUTOMATIC_INTERVAL_MILLIS
                || !lastAutomaticSnapshot.compareAndSet(previous, nowEpochMillis)) {
            return Optional.empty();
        }
        try {
            Path destination = snapshotDirectory.resolve(
                    "auto-" + FILE_TIME.format(Instant.ofEpochMilli(nowEpochMillis)) + "-r" + state.revision());
            Path saved = store.exportAtomically(
                    destination, captureService.capture(state, nowEpochMillis, sourceVersions));
            pruneAutomatic(retention);
            return Optional.of(saved);
        } catch (IOException | SnapshotFormatException exception) {
            lastAutomaticSnapshot.compareAndSet(nowEpochMillis, previous);
            throw exception;
        }
    }

    public ImportedSnapshotContext importReadOnly(Path source, long nowEpochMillis)
            throws IOException, SnapshotFormatException {
        return store.importReadOnly(source, nowEpochMillis);
    }

    public SnapshotComparison compareWithCurrent(ImportedSnapshotContext imported, long nowEpochMillis) {
        SnapshotPayload current = captureService
                .capture(repository.snapshot(), nowEpochMillis, sourceVersions)
                .payload();
        return comparisonService.compare(imported.payload(), current);
    }

    public List<SnapshotFileInfo> listSnapshots() throws IOException {
        if (!Files.isDirectory(snapshotDirectory)) return List.of();
        try (var paths = Files.list(snapshotDirectory)) {
            return paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(
                                    SnapshotArchiveStore.EXTENSION))
                    .map(path -> {
                        try {
                            return new SnapshotFileInfo(
                                    path, Files.size(path), Files.getLastModifiedTime(path).toMillis());
                        } catch (IOException exception) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(SnapshotFileInfo::modifiedAtEpochMillis).reversed())
                    .limit(1_000)
                    .toList();
        }
    }

    public Path snapshotDirectory() {
        return snapshotDirectory;
    }

    private void pruneAutomatic(int retention) throws IOException {
        int safeRetention = Math.max(1, Math.min(250, retention));
        List<SnapshotFileInfo> automatic = listSnapshots().stream()
                .filter(info -> info.path().getFileName().toString().startsWith("auto-"))
                .toList();
        for (int index = safeRetention; index < automatic.size(); index++) {
            Files.deleteIfExists(automatic.get(index).path());
        }
    }
}
