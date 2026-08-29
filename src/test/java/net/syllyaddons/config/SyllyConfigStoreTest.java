package net.syllyaddons.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyllyConfigStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAtomicallyAndKeepsThePreviousReadableConfig() throws Exception {
        Path destination = temporaryDirectory.resolve("settings.json");
        SyllyConfigStore store = new SyllyConfigStore(destination, () -> 1234L);
        SyllyConfig original = SyllyConfig.defaults();
        SyllyConfig updated = original.withOptimizerEnabled(false);

        store.save(original);
        store.save(updated);

        assertEquals(updated, store.loadOrCreate().config());
        assertEquals(original, new Gson().fromJson(Files.readString(store.backup()), SyllyConfig.class));
        assertFalse(Files.exists(destination.resolveSibling("settings.json.tmp")));
    }

    @Test
    void quarantinesBrokenConfigAndRestoresDefaults() throws Exception {
        Path destination = temporaryDirectory.resolve("settings.json");
        Files.writeString(destination, "{ definitely not json");
        SyllyConfigStore store = new SyllyConfigStore(destination, () -> 9876L);

        SyllyConfigLoadResult loaded = store.loadOrCreate();

        Path quarantine = temporaryDirectory.resolve("settings.corrupt-9876.json");
        assertEquals(SyllyConfig.defaults(), loaded.config());
        assertEquals(quarantine, loaded.quarantinedPath());
        assertTrue(Files.isRegularFile(quarantine));
        assertTrue(Files.isRegularFile(destination));
        assertTrue(loaded.warning().contains("defaults were restored"));
        assertEquals(SyllyConfig.defaults(), store.loadOrCreate().config());
    }

    @Test
    void rejectsOutOfRangeValuesBeforeTheyCanBePersisted() {
        assertThrows(IllegalArgumentException.class, () -> SyllyConfig.defaults().withSnapshotRetention(0));
        assertThrows(IllegalArgumentException.class, () -> SyllyConfig.defaults().withEcoWarningCooldownSeconds(601));
        assertThrows(IllegalArgumentException.class, () -> SyllyConfig.defaults().withImpactAlertDurationSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> SyllyConfig.defaults().withRoutingAdvisor(
                RoutingAdvisorConfig.defaults().withMaximumAdditionalCostEmeralds(16_777_217)));
    }

    @Test
    void fillsTrack8DefaultsWhenLoadingAReadablePreTrack8SettingsFile() throws Exception {
        Path destination = temporaryDirectory.resolve("settings.json");
        Files.writeString(destination, """
                {
                  "schemaVersion": 1,
                  "ecoAuditorEnabled": true,
                  "ecoWarningCooldownSeconds": 30,
                  "territoryImpactEnabled": true,
                  "routingAdvisorEnabled": true,
                  "optimizerEnabled": true,
                  "automaticSnapshotsEnabled": false,
                  "snapshotRetention": 20,
                  "profileSwapNotifications": true,
                  "configurationWarnings": true
                }
                """);

        SyllyConfigLoadResult loaded = new SyllyConfigStore(destination, () -> 42L).loadOrCreate();

        assertEquals(SyllyConfig.defaults(), loaded.config());
        assertEquals(null, loaded.quarantinedPath());
    }

    @Test
    void persistsTrack9AdvisorThresholds() throws Exception {
        Path destination = temporaryDirectory.resolve("settings.json");
        SyllyConfigStore store = new SyllyConfigStore(destination, () -> 123L);
        SyllyConfig updated = SyllyConfig.defaults().withRoutingAdvisor(
                RoutingAdvisorConfig.defaults()
                        .withMinimumTimeSavingSeconds(120)
                        .withMaximumAdditionalCostEmeralds(9_999)
                        .withActiveOperationsOnly(false));

        store.save(updated);

        assertEquals(updated, store.loadOrCreate().config());
    }
}
