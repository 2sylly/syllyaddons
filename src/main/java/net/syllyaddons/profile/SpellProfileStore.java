package net.syllyaddons.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class SpellProfileStore {
    private final Path destination;
    private final Gson gson;

    public SpellProfileStore(Path destination) {
        this.destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Optional<SpellProfileConfig> load() throws IOException {
        if (!Files.isRegularFile(destination)) return Optional.empty();
        SpellProfileConfig config = gson.fromJson(Files.readString(destination, StandardCharsets.UTF_8), SpellProfileConfig.class);
        if (config == null) throw new IOException("Spell profile file contained no configuration");
        if (config.schemaVersion() != SpellProfileConfig.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Unsupported spell profile schema " + config.schemaVersion());
        }
        return Optional.of(config);
    }

    public void save(SpellProfileConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(config), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path destination() {
        return destination;
    }
}
