package net.syllyaddons.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import net.syllyaddons.domain.ObservedState;

public final class HistoricalObservationStore {
    private final Path destination;
    private final ObservedStateJsonCodec codec;

    public HistoricalObservationStore(Path destination, ObservedStateJsonCodec codec) {
        this.destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public boolean saveIfUseful(ObservedState state) throws IOException {
        Objects.requireNonNull(state, "state");
        if (!isUseful(state)) return false;

        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, codec.encode(state), StandardCharsets.UTF_8);
        moveAtomicallyWhenSupported(temporary, destination);
        return true;
    }

    public Optional<HistoricalObservation> load(long nowEpochMillis) throws IOException {
        if (!Files.isRegularFile(destination)) return Optional.empty();

        String json = Files.readString(destination, StandardCharsets.UTF_8);
        return Optional.of(new HistoricalObservation(codec.decode(json), nowEpochMillis, destination));
    }

    public Path destination() {
        return destination;
    }

    static boolean isUseful(ObservedState state) {
        if (state.territories().isEmpty()) return false;
        return state.territories().values().stream()
                .anyMatch(territory -> territory.owner().isKnown()
                        || territory.links().isKnown()
                        || territory.resources().isKnown()
                        || territory.upgrades().isKnown());
    }

    private static void moveAtomicallyWhenSupported(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
