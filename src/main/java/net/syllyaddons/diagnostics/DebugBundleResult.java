package net.syllyaddons.diagnostics;

import java.nio.file.Path;
import java.util.Objects;

public record DebugBundleResult(Path path, long sizeBytes) {
    public DebugBundleResult {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
    }
}
