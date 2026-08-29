package net.syllyaddons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class SyllyConfigStore {
    private final Path destination;
    private final Path backup;
    private final Gson gson;
    private final LongSupplier epochMillis;

    public SyllyConfigStore(Path destination) {
        this(destination, System::currentTimeMillis);
    }

    SyllyConfigStore(Path destination, LongSupplier epochMillis) {
        this.destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        this.backup = this.destination.resolveSibling(this.destination.getFileName() + ".bak");
        this.epochMillis = Objects.requireNonNull(epochMillis, "epochMillis");
        this.gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    }

    public synchronized SyllyConfigLoadResult loadOrCreate() throws IOException {
        if (!Files.isRegularFile(destination)) {
            SyllyConfig defaults = SyllyConfig.defaults();
            save(defaults);
            return new SyllyConfigLoadResult(defaults, null, null);
        }

        try {
            return new SyllyConfigLoadResult(read(destination), null, null);
        } catch (IOException | RuntimeException exception) {
            Path quarantined = quarantine();
            SyllyConfig defaults = SyllyConfig.defaults();
            save(defaults);
            String warning = "Broken settings were moved to " + quarantined.getFileName()
                    + "; defaults were restored (" + exception.getMessage() + ")";
            return new SyllyConfigLoadResult(defaults, warning, quarantined);
        }
    }

    public synchronized void save(SyllyConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        if (Files.isRegularFile(destination) && isReadableConfig(destination)) {
            Path backupTemporary = backup.resolveSibling(backup.getFileName() + ".tmp");
            Files.copy(destination, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(backupTemporary, backup);
        }

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(config), StandardCharsets.UTF_8);
        moveAtomically(temporary, destination);
    }

    public Path destination() {
        return destination;
    }

    public Path backup() {
        return backup;
    }

    private SyllyConfig read(Path source) throws IOException {
        String json = Files.readString(source, StandardCharsets.UTF_8);
        if (json.isBlank()) throw new JsonParseException("settings file is empty");
        SyllyConfig config = gson.fromJson(json, SyllyConfig.class);
        if (config == null) throw new JsonParseException("settings file contains null");
        return config;
    }

    private boolean isReadableConfig(Path source) {
        try {
            read(source);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private Path quarantine() throws IOException {
        String fileName = destination.getFileName().toString();
        String baseName = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - ".json".length())
                : fileName;
        Path quarantined = destination.resolveSibling(baseName + ".corrupt-" + epochMillis.getAsLong() + ".json");
        moveAtomically(destination, quarantined);
        return quarantined;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
