package net.syllyaddons.config;

import java.nio.file.Path;
import java.util.Optional;

public record SyllyConfigLoadResult(SyllyConfig config, String warning, Path quarantinedPath) {
    public Optional<String> warningOptional() {
        return Optional.ofNullable(warning).filter(value -> !value.isBlank());
    }

    public Optional<Path> quarantinedPathOptional() {
        return Optional.ofNullable(quarantinedPath);
    }
}
