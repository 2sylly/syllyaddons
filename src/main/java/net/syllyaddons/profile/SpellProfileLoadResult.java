package net.syllyaddons.profile;

import java.nio.file.Path;
import java.util.Optional;

public record SpellProfileLoadResult(Optional<SpellProfileConfig> config, String warning, Path quarantinedPath) {
    public SpellProfileLoadResult {
        config = config == null ? Optional.empty() : config;
    }

    public Optional<String> warningOptional() {
        return Optional.ofNullable(warning).filter(value -> !value.isBlank());
    }
}
