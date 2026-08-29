package net.syllyaddons.config;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class SyllyConfigService {
    private final SyllyConfigStore store;
    private SyllyConfig config;
    private String startupWarning;
    private String lastError;

    private SyllyConfigService(SyllyConfigStore store, SyllyConfig config, String startupWarning) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.startupWarning = startupWarning;
    }

    public static SyllyConfigService open(SyllyConfigStore store) {
        Objects.requireNonNull(store, "store");
        try {
            SyllyConfigLoadResult loaded = store.loadOrCreate();
            return new SyllyConfigService(store, loaded.config(), loaded.warning());
        } catch (IOException | RuntimeException exception) {
            return new SyllyConfigService(
                    store,
                    SyllyConfig.defaults(),
                    "Settings could not be loaded or repaired; defaults are active (" + exception.getMessage() + ")");
        }
    }

    public synchronized SyllyConfig snapshot() {
        return config;
    }

    public synchronized boolean update(UnaryOperator<SyllyConfig> change) {
        Objects.requireNonNull(change, "change");
        SyllyConfig updated;
        try {
            updated = Objects.requireNonNull(change.apply(config), "updated config");
            if (updated.equals(config)) {
                lastError = null;
                return true;
            }
            store.save(updated);
        } catch (IOException | RuntimeException exception) {
            lastError = "Could not save settings: " + exception.getMessage();
            return false;
        }
        config = updated;
        lastError = null;
        return true;
    }

    public synchronized boolean reset(SyllyConfigSection section) {
        return update(current -> current.reset(section));
    }

    public synchronized Optional<String> warning() {
        if (lastError != null && !lastError.isBlank()) return Optional.of(lastError);
        return Optional.ofNullable(startupWarning).filter(value -> !value.isBlank());
    }

    public synchronized void dismissStartupWarning() {
        startupWarning = null;
    }
}
