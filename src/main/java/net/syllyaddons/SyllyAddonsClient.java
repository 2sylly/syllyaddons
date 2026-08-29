package net.syllyaddons;

import com.wynntils.core.WynntilsMod;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.syllyaddons.compat.wynntils.v4_2_8.CompatibilityResult;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsCompatibilityGuard;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsCharacterCatalog;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsObservationListener;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsRoutingModeAdapter;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsSessionAdapter;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsSpellAdapter;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsSpellInputListener;
import net.syllyaddons.compat.wynntils.v4_2_8.WynntilsTerritoryAdapter;
import net.syllyaddons.client.gui.DebugScreenController;
import net.syllyaddons.client.gui.RouteHighlightController;
import net.syllyaddons.client.gui.SpellProfileController;
import net.syllyaddons.client.gui.SyllySettingsController;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.config.SyllyConfigStore;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.observation.ObservedStateMerger;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.observation.api.WynncraftTerritoryApiClient;
import net.syllyaddons.persistence.HistoricalObservationStore;
import net.syllyaddons.persistence.ObservedStateJsonCodec;
import net.syllyaddons.profile.SpellProfileService;
import net.syllyaddons.profile.SpellProfileStore;
import net.syllyaddons.snapshot.ObservedEconomyAnalyzer;
import net.syllyaddons.snapshot.SnapshotArchiveCodec;
import net.syllyaddons.snapshot.SnapshotArchiveStore;
import net.syllyaddons.snapshot.SnapshotArchiveValidator;
import net.syllyaddons.snapshot.SnapshotCaptureService;
import net.syllyaddons.snapshot.SnapshotComparisonService;
import net.syllyaddons.snapshot.SnapshotManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SyllyAddonsClient implements ClientModInitializer {
    public static final String MOD_ID = "syllyaddons";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final long PERSIST_INTERVAL_MILLIS = 30_000;

    private static ObservedStateRepository repository;
    private WynntilsObservationListener observationListener;
    private WynntilsSpellInputListener spellInputListener;
    private SpellProfileService spellProfileService;
    private SyllyConfigService settingsService;
    private SnapshotManagerService snapshotManagerService;
    private boolean observationPipelineStarted;

    @Override
    public void onInitializeClient() {
        CompatibilityResult compatibility = new WynntilsCompatibilityGuard().validate();
        if (!compatibility.compatible()) {
            LOGGER.error("Sylly Addons disabled: {}", compatibility.message());
            return;
        }
        LOGGER.info(compatibility.message());

        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        settingsService = SyllyConfigService.open(new SyllyConfigStore(configDirectory.resolve("settings.json")));
        settingsService.warning().ifPresent(warning -> LOGGER.warn("Sylly Addons settings: {}", warning));

        Path historicalPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MOD_ID)
                .resolve("latest-observed-state.json");
        HistoricalObservationStore historicalStore =
                new HistoricalObservationStore(historicalPath, new ObservedStateJsonCodec());

        ObservedState initialState = loadHistoricalState(historicalStore);
        repository = new ObservedStateRepository(initialState, new ObservedStateMerger());
        if (initialState.revision() > 0) {
            repository.clearSession(System.currentTimeMillis(), "Loaded historical data; waiting for a live session");
        }
        installPersistence(repository, historicalStore);
        snapshotManagerService = createSnapshotManager(configDirectory, repository);
        installAutomaticSnapshots(repository, snapshotManagerService);
        DebugScreenController.register(repository);
        RouteHighlightController.register();
        SpellProfileController.register(() -> spellProfileService);
        SyllySettingsController.register(
                () -> settingsService,
                () -> spellProfileService,
                () -> repository,
                () -> snapshotManagerService);

        observationListener = new WynntilsObservationListener(
                repository,
                new WynntilsSessionAdapter(),
                new WynntilsTerritoryAdapter(),
                new WynntilsRoutingModeAdapter(),
                WynncraftTerritoryApiClient.createDefault(),
                LOGGER);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> startObservationPipeline());
        LOGGER.info("Sylly Addons initialized; observation will attach after Wynntils startup");
    }

    public static ObservedStateRepository stateRepository() {
        if (repository == null) throw new IllegalStateException("Sylly Addons observation pipeline is not initialized");
        return repository;
    }

    private void startObservationPipeline() {
        if (observationPipelineStarted) return;
        try {
            WynntilsMod.registerEventListener(observationListener);
            observationPipelineStarted = true;
            observationListener.captureInitialState();
            LOGGER.info("Track 1 observation pipeline initialized");

            WynntilsSpellAdapter spellAdapter = new WynntilsSpellAdapter();
            Path profilePath = FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve(MOD_ID)
                    .resolve("spell-profiles.json");
            WynntilsCharacterCatalog characterCatalog = new WynntilsCharacterCatalog();
            spellProfileService = new SpellProfileService(
                    new SpellProfileStore(profilePath),
                    spellAdapter,
                    spellAdapter,
                    characterCatalog,
                    message -> LOGGER.error("Spell profiles: {}", message),
                    profileName -> {
                        if (settingsService.snapshot().profileSwapNotifications()) {
                            showProfileChangeMessage(profileName);
                        }
                    });
            characterCatalog.attach(spellProfileService);
            spellProfileService.initialize(repository.snapshot());
            repository.addListener(spellProfileService::onObservedState);
            WynntilsMod.registerEventListener(characterCatalog);
            spellInputListener = new WynntilsSpellInputListener(spellProfileService);
            WynntilsMod.registerEventListener(spellInputListener);
            LOGGER.info("Track 2 spell profile pipeline initialized");
        } catch (RuntimeException exception) {
            LOGGER.error("Could not attach Sylly Addons pipelines to Wynntils", exception);
        }
    }

    private static ObservedState loadHistoricalState(HistoricalObservationStore store) {
        try {
            return store.load(System.currentTimeMillis())
                    .map(observation -> {
                        LOGGER.info(
                                "Loaded historical observation revision {} from {}",
                                observation.state().revision(),
                                observation.sourcePath());
                        return observation.state();
                    })
                    .orElseGet(ObservedState::empty);
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not load historical observation data; starting empty", exception);
            return ObservedState.empty();
        }
    }

    private static void showProfileChangeMessage(String profileName) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("[SyllyAddons] Swapped to Profile " + profileName + "!"), false);
            }
        });
    }

    private static void installPersistence(
            ObservedStateRepository stateRepository, HistoricalObservationStore historicalStore) {
        AtomicLong lastSave = new AtomicLong();
        stateRepository.addListener(state -> {
            long now = System.currentTimeMillis();
            long previous = lastSave.get();
            if (now - previous < PERSIST_INTERVAL_MILLIS || !lastSave.compareAndSet(previous, now)) return;
            try {
                historicalStore.saveIfUseful(state);
            } catch (IOException exception) {
                LOGGER.warn("Could not persist the latest observed state", exception);
            }
        });
    }

    private SnapshotManagerService createSnapshotManager(
            Path configDirectory, ObservedStateRepository stateRepository) {
        SnapshotArchiveValidator validator = new SnapshotArchiveValidator();
        return new SnapshotManagerService(
                configDirectory.resolve("snapshots"),
                stateRepository,
                new SnapshotArchiveStore(new SnapshotArchiveCodec(validator)),
                new SnapshotCaptureService(new ObservedEconomyAnalyzer()),
                new SnapshotComparisonService(),
                Map.of(
                        "minecraft", installedVersion("minecraft"),
                        "wynntils", installedVersion("wynntils"),
                        "syllyaddons", installedVersion(MOD_ID),
                        "snapshotFormat", "1",
                        "routingRules", "routing-research-2026-08-29.1",
                        "economyRules", "economy-research-2026-08-29.1"));
    }

    private void installAutomaticSnapshots(
            ObservedStateRepository stateRepository, SnapshotManagerService manager) {
        stateRepository.addListener(state -> {
            if (!settingsService.snapshot().automaticSnapshotsEnabled()) return;
            long now = System.currentTimeMillis();
            int retention = settingsService.snapshot().snapshotRetention();
            CompletableFuture.runAsync(() -> {
                try {
                    manager.exportAutomaticIfDue(now, retention).ifPresent(path ->
                            LOGGER.info("Saved automatic Track 5 snapshot {}", path.getFileName()));
                } catch (Exception exception) {
                    LOGGER.warn("Could not save automatic Track 5 snapshot", exception);
                }
            });
        });
    }

    private static String installedVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
