package net.syllyaddons.persistence;

import java.nio.file.Path;
import java.util.Objects;
import net.syllyaddons.domain.ObservedState;

public record HistoricalObservation(ObservedState state, long loadedAtEpochMillis, Path sourcePath) {
    public HistoricalObservation {
        Objects.requireNonNull(state, "state");
        if (loadedAtEpochMillis < 0) throw new IllegalArgumentException("loadedAtEpochMillis must be non-negative");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").toAbsolutePath().normalize();
    }
}
