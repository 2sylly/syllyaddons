package net.syllyaddons.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpellProfileStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsConfiguration() throws Exception {
        SpellProfile profile = new SpellProfile(
                "war",
                "War",
                List.of(new SpellBinding(new PhysicalInput(InputDevice.MOUSE, 4), 3)));
        SpellProfileConfig config = new SpellProfileConfig(
                1,
                Map.of(profile.id(), profile),
                Map.of("char", profile.id()),
                Map.of("MAGE", profile.id()),
                profile.id(),
                Map.of());
        SpellProfileStore store = new SpellProfileStore(temporaryDirectory.resolve("spell-profiles.json"));

        store.save(config);

        assertEquals(config, store.load().orElseThrow());
        assertFalse(java.nio.file.Files.exists(temporaryDirectory.resolve("spell-profiles.json.tmp")));
    }

    @Test
    void upgradesSchemaOneWithAutomaticSwitchingEnabled() throws Exception {
        Path destination = temporaryDirectory.resolve("legacy-profiles.json");
        Files.writeString(
                destination,
                """
                {
                  "schemaVersion": 1,
                  "profiles": {},
                  "characterAssignments": {},
                  "classFallbacks": {},
                  "rememberedOverrides": {}
                }
                """);

        SpellProfileConfig loaded = new SpellProfileStore(destination, () -> 777L).load().orElseThrow();

        assertEquals(SpellProfileConfig.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
        assertTrue(loaded.automaticSwitchingEnabled());
        assertTrue(loaded.knownCharacters().isEmpty());
        Path migrationBackup = temporaryDirectory.resolve(
                "legacy-profiles.json.schema-v1-to-v2-777.bak");
        assertTrue(Files.isRegularFile(migrationBackup));
        assertTrue(Files.readString(migrationBackup).contains("\"schemaVersion\": 1"));
        assertTrue(Files.readString(destination).contains("\"schemaVersion\": 2"));
    }

    @Test
    void keepsPreviousReadableConfigAsBackup() throws Exception {
        Path destination = temporaryDirectory.resolve("spell-profiles.json");
        SpellProfileStore store = new SpellProfileStore(destination, () -> 123L);
        SpellProfileConfig original = SpellProfileConfig.empty();
        SpellProfile profile = new SpellProfile("mage", "Mage", List.of());
        SpellProfileConfig updated = new SpellProfileConfig(
                SpellProfileConfig.CURRENT_SCHEMA_VERSION,
                Map.of(profile.id(), profile),
                Map.of(),
                Map.of(),
                profile.id(),
                Map.of());

        store.save(original);
        store.save(updated);

        assertTrue(Files.isRegularFile(store.backup()));
        assertEquals(original, new SpellProfileStore(store.backup()).load().orElseThrow());
        assertEquals(updated, store.load().orElseThrow());
    }

    @Test
    void quarantinesUnreadableProfiles() throws Exception {
        Path destination = temporaryDirectory.resolve("spell-profiles.json");
        Files.writeString(destination, "not json");
        SpellProfileStore store = new SpellProfileStore(destination, () -> 456L);

        SpellProfileLoadResult result = store.loadSafely();

        assertTrue(result.config().isEmpty());
        assertEquals(temporaryDirectory.resolve("spell-profiles.corrupt-456.json"), result.quarantinedPath());
        assertTrue(Files.isRegularFile(result.quarantinedPath()));
        assertTrue(result.warning().contains("fresh profile configuration"));
    }
}
