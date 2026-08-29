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

        SpellProfileConfig loaded = new SpellProfileStore(destination).load().orElseThrow();

        assertEquals(SpellProfileConfig.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
        assertTrue(loaded.automaticSwitchingEnabled());
        assertTrue(loaded.knownCharacters().isEmpty());
    }
}
