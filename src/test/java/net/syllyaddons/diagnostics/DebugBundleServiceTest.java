package net.syllyaddons.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import net.syllyaddons.advisor.AttackAdvisorService;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.config.SyllyConfigStore;
import net.syllyaddons.domain.CharacterIdentity;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryRating;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.observation.ObservedStateMerger;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.optimizer.OptimizerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugBundleServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportContainsExpectedDiagnosticsButNoKnownSensitiveValues() throws Exception {
        ObservedStateRepository repository = new ObservedStateRepository(state(), new ObservedStateMerger());
        SyllyConfigService settings = SyllyConfigService.open(
                new SyllyConfigStore(temporaryDirectory.resolve("settings.json")));
        AttackAdvisorService advisor = new AttackAdvisorService(repository, settings);
        OptimizerService optimizer = new OptimizerService(repository);
        SubsystemHealthRegistry registry = new SubsystemHealthRegistry();
        registry.healthy(Subsystem.COMPATIBILITY, "Pinned version matches");
        registry.healthy(Subsystem.OBSERVATION, "attached");
        registry.healthy(Subsystem.SNAPSHOTS, "ready");
        registry.healthy(Subsystem.ECO_AUDITOR, "ready");
        registry.healthy(Subsystem.TERRITORY_IMPACT, "ready");
        registry.healthy(Subsystem.ROUTING_ADVISOR, "attached");
        registry.healthy(Subsystem.OPTIMIZER, "ready");
        TerritoryImpactCache cache = new TerritoryImpactCache();
        try {
            OperationsHealthService health = new OperationsHealthService(
                    registry, repository, settings, () -> null, advisor, cache, optimizer);
            DebugBundleService bundles = new DebugBundleService(
                    temporaryDirectory.resolve("debug-bundles"),
                    Map.of("wynntils", "4.2.9", "syllyaddons", "test"),
                    repository,
                    health,
                    advisor,
                    cache,
                    optimizer);

            DebugBundleResult result = bundles.export(9_999);
            Archive archive = readArchive(result.path());

            assertEquals(
                    List.of("manifest.json", "health.json", "observed-state-redacted.json", "calculations.json", "README.txt"),
                    archive.entries());
            assertTrue(archive.contents().contains("territory-001"));
            assertTrue(archive.contents().contains("guild-1"));
            assertTrue(archive.contents().contains("4.2.9"));
            assertFalse(archive.contents().contains("character-secret"));
            assertFalse(archive.contents().contains("guild-secret-uuid"));
            assertFalse(archive.contents().contains("Secret Guild"));
            assertFalse(archive.contents().contains("Secret Territory"));
            assertFalse(archive.contents().contains("PlayerSecret"));
        } finally {
            cache.close();
        }
    }

    private static ObservedState state() {
        Evidence evidence = new Evidence(
                EvidenceKind.LOCAL_EXACT, 9_000, "fixture", "4.2.9", "Secret Guild / PlayerSecret note");
        TerritoryState territory = new TerritoryState(
                "Secret Territory",
                ObservedValue.known(new TerritoryOwner("guild-secret-uuid", "Secret Guild", "SG"), evidence),
                ObservedValue.known(8_000L, evidence),
                ObservedValue.known(true, evidence),
                ObservedValue.known(new TerritoryBounds(1, 2, 3, 4), evidence),
                ObservedValue.known(List.of("Secret Territory"), evidence),
                ObservedValue.known(Map.of(ResourceType.ORE, new ResourceBalance(10, 20, 30)), evidence),
                ObservedValue.known(TerritoryRating.HIGH, evidence),
                ObservedValue.known(15.0, evidence),
                ObservedValue.known(TerritoryRating.MEDIUM, evidence),
                ObservedValue.known(Map.of("damage", 2), evidence),
                ObservedValue.known(List.of("PlayerSecret attacked Secret Guild"), evidence));
        return new ObservedState(
                ObservedState.CURRENT_SCHEMA_VERSION,
                7,
                9_000,
                ObservedValue.known(new CharacterIdentity("character-secret", "MAGE", false), evidence),
                ObservedValue.known(new GuildIdentity("guild-secret-uuid", "Secret Guild", "SG"), evidence),
                ObservedValue.known("Secret Territory", evidence),
                ObservedValue.known(RoutingMode.CHEAPEST, evidence),
                Map.of(territory.name(), territory));
    }

    private static Archive readArchive(Path path) throws Exception {
        ArrayList<String> entries = new ArrayList<>();
        StringBuilder contents = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(java.nio.file.Files.newInputStream(path), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                contents.append(output.toString(StandardCharsets.UTF_8));
            }
        }
        return new Archive(List.copyOf(entries), contents.toString());
    }

    private record Archive(List<String> entries, String contents) {}
}
