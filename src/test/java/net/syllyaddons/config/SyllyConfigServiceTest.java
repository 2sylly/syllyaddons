package net.syllyaddons.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyllyConfigServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void invalidUpdateLeavesMemoryAndDiskUnchanged() throws Exception {
        SyllyConfigStore store = new SyllyConfigStore(temporaryDirectory.resolve("settings.json"));
        SyllyConfigService service = SyllyConfigService.open(store);

        boolean saved = service.update(config -> config.withSnapshotRetention(0));

        assertFalse(saved);
        assertEquals(SyllyConfig.defaults(), service.snapshot());
        assertEquals(SyllyConfig.defaults(), store.loadOrCreate().config());
        assertTrue(service.warning().orElseThrow().contains("Could not save settings"));
    }

    @Test
    void sectionResetOnlyChangesThatSectionsFields() {
        SyllyConfigStore store = new SyllyConfigStore(temporaryDirectory.resolve("settings.json"));
        SyllyConfigService service = SyllyConfigService.open(store);
        service.update(config -> config.withSnapshotRetention(99).withOptimizerEnabled(false));

        assertTrue(service.reset(SyllyConfigSection.SNAPSHOTS));

        assertEquals(SyllyConfig.defaults().snapshotRetention(), service.snapshot().snapshotRetention());
        assertFalse(service.snapshot().optimizerEnabled());
    }
}
