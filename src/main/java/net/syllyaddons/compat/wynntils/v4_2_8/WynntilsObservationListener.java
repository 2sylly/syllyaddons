package net.syllyaddons.compat.wynntils.v4_2_8;

import com.wynntils.core.components.Models;
import com.wynntils.mc.event.AdvancementUpdateEvent;
import com.wynntils.mc.event.ContainerSetContentEvent;
import com.wynntils.mc.event.ContainerSetSlotEvent;
import com.wynntils.mc.event.TickEvent;
import com.wynntils.models.character.event.CharacterUpdateEvent;
import com.wynntils.models.guild.event.GuildEvent;
import com.wynntils.models.worlds.event.WorldStateEvent;
import com.wynntils.models.worlds.type.WorldState;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.observation.api.WynncraftTerritoryApiClient;
import org.slf4j.Logger;

public final class WynntilsObservationListener {
    private static final int API_REFRESH_TICKS = 20 * 15;

    private final ObservedStateRepository repository;
    private final WynntilsSessionAdapter sessionAdapter;
    private final WynntilsTerritoryAdapter territoryAdapter;
    private final WynntilsRoutingModeAdapter routingModeAdapter;
    private final WynncraftTerritoryApiClient apiClient;
    private final Logger logger;
    private final AtomicBoolean apiRequestInFlight = new AtomicBoolean();
    private int ticksUntilApiRefresh;
    private String lastApiError = "";

    public WynntilsObservationListener(
            ObservedStateRepository repository,
            WynntilsSessionAdapter sessionAdapter,
            WynntilsTerritoryAdapter territoryAdapter,
            WynntilsRoutingModeAdapter routingModeAdapter,
            WynncraftTerritoryApiClient apiClient,
            Logger logger) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.sessionAdapter = java.util.Objects.requireNonNull(sessionAdapter, "sessionAdapter");
        this.territoryAdapter = java.util.Objects.requireNonNull(territoryAdapter, "territoryAdapter");
        this.routingModeAdapter = java.util.Objects.requireNonNull(routingModeAdapter, "routingModeAdapter");
        this.apiClient = java.util.Objects.requireNonNull(apiClient, "apiClient");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    public void captureInitialState() {
        captureSession();
        captureProfiles();
        captureAdvancementTerritories();
        requestPublicTerritories();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCharacterUpdate(CharacterUpdateEvent event) {
        captureSession();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuildJoined(GuildEvent.Joined event) {
        captureSession();
        requestPublicTerritories();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuildLeft(GuildEvent.Left event) {
        repository.clearSession(System.currentTimeMillis(), "The active guild session ended");
        captureSession();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldStateChanged(WorldStateEvent event) {
        if (event.getOldState() == WorldState.WORLD && event.getNewState() != WorldState.WORLD) {
            repository.clearSession(System.currentTimeMillis(), "The Wynncraft world session ended");
        }
        if (event.getNewState() == WorldState.WORLD) {
            ticksUntilApiRefresh = 0;
            captureProfiles();
            captureAdvancementTerritories();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAdvancementUpdate(AdvancementUpdateEvent event) {
        captureAdvancementTerritories();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerContent(ContainerSetContentEvent.Post event) {
        captureTerritoryItems(event.getItems());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerSlot(ContainerSetSlotEvent.Post event) {
        captureTerritoryItems(List.of(event.getItemStack()));
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!Models.WorldState.onWorld()) return;
        if (ticksUntilApiRefresh-- <= 0) {
            ticksUntilApiRefresh = API_REFRESH_TICKS;
            captureProfiles();
            requestPublicTerritories();
        }
    }

    private void captureSession() {
        safeCapture("session", () -> repository.merge(sessionAdapter.capture(System.currentTimeMillis())));
    }

    private void captureProfiles() {
        safeCapture(
                "territory profiles",
                () -> repository.merge(territoryAdapter.captureProfiles(System.currentTimeMillis())));
    }

    private void captureAdvancementTerritories() {
        safeCapture(
                "advancement territories",
                () -> repository.merge(territoryAdapter.captureAdvancementTerritories(System.currentTimeMillis())));
    }

    private void captureTerritoryItems(List<ItemStack> items) {
        safeCapture(
                "territory menu items",
                () -> {
                    long now = System.currentTimeMillis();
                    repository.merge(territoryAdapter.captureTerritoryItems(items, now));
                    repository.merge(routingModeAdapter.capture(items, now));
                });
    }

    private void requestPublicTerritories() {
        if (!apiRequestInFlight.compareAndSet(false, true)) return;
        String currentGuild = Models.Guild.isInGuild() ? Models.Guild.getGuildName() : null;
        apiClient.fetch(currentGuild).whenComplete((batch, throwable) -> {
            apiRequestInFlight.set(false);
            if (throwable != null) {
                String message = rootMessage(throwable);
                if (!message.equals(lastApiError)) {
                    logger.warn("Public territory refresh failed: {}", message);
                    lastApiError = message;
                }
                return;
            }

            if (!lastApiError.isEmpty()) {
                logger.info("Public territory refresh recovered");
                lastApiError = "";
            }
            repository.merge(batch);
        });
    }

    private void safeCapture(String source, Runnable capture) {
        try {
            capture.run();
        } catch (RuntimeException exception) {
            logger.warn("Failed to capture {}", source, exception);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
