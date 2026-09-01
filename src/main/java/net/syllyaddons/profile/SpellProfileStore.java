package net.syllyaddons.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import net.syllyaddons.persistence.SchemaMigrationBackup;

public final class SpellProfileStore {
    private final Path destination;
    private final Path backup;
    private final Gson gson;
    private final LongSupplier epochMillis;
    private final SchemaMigrationBackup schemaMigrationBackup = new SchemaMigrationBackup();

    public SpellProfileStore(Path destination) {
        this(destination, System::currentTimeMillis);
    }

    SpellProfileStore(Path destination, LongSupplier epochMillis) {
        this.destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        this.backup = this.destination.resolveSibling(this.destination.getFileName() + ".bak");
        this.epochMillis = Objects.requireNonNull(epochMillis, "epochMillis");
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public synchronized Optional<SpellProfileConfig> load() throws IOException {
        if (!Files.isRegularFile(destination)) return Optional.empty();
        String json = Files.readString(destination, StandardCharsets.UTF_8);
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        int storedSchema = document.has("schemaVersion") ? document.get("schemaVersion").getAsInt() : 0;
        if (storedSchema > SpellProfileConfig.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Unsupported spell profile schema " + storedSchema);
        }
        SpellProfileConfig config = gson.fromJson(document, SpellProfileConfig.class);
        if (config == null) throw new IOException("Spell profile file contained no configuration");
        if (config.schemaVersion() != SpellProfileConfig.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Unsupported spell profile schema " + config.schemaVersion());
        }
        if (storedSchema > 0 && storedSchema < SpellProfileConfig.CURRENT_SCHEMA_VERSION) {
            schemaMigrationBackup.create(
                    destination, storedSchema, SpellProfileConfig.CURRENT_SCHEMA_VERSION, epochMillis.getAsLong());
            write(config);
        }
        return Optional.of(config);
    }

    public SpellProfileLoadResult loadSafely() throws IOException {
        try {
            return new SpellProfileLoadResult(load(), null, null);
        } catch (IOException | RuntimeException exception) {
            Path quarantined = quarantine();
            String warning = "Broken spell profiles were moved to " + quarantined.getFileName()
                    + "; a fresh profile configuration was created (" + exception.getMessage() + ")";
            return new SpellProfileLoadResult(Optional.empty(), warning, quarantined);
        }
    }

    public synchronized void save(SpellProfileConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        if (Files.isRegularFile(destination) && isReadableConfig()) {
            Path backupTemporary = backup.resolveSibling(backup.getFileName() + ".tmp");
            Files.copy(destination, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(backupTemporary, backup);
        }

        write(config);
    }

    private boolean isReadableConfig() {
        try {
            load();
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private void write(SpellProfileConfig config) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(config), StandardCharsets.UTF_8);
        moveAtomically(temporary, destination);
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

    public Path destination() {
        return destination;
    }

    public Path backup() {
        return backup;
    }
}
