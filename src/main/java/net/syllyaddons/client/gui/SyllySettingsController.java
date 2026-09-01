package net.syllyaddons.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.diagnostics.DebugBundleService;
import net.syllyaddons.diagnostics.OperationsHealthService;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.optimizer.OptimizerService;
import net.syllyaddons.profile.SpellProfileService;
import net.syllyaddons.snapshot.SnapshotManagerService;
import org.lwjgl.glfw.GLFW;

public final class SyllySettingsController {
    private static Supplier<SyllyConfigService> settingsSupplier;
    private static Supplier<SpellProfileService> profilesSupplier;
    private static Supplier<ObservedStateRepository> repositorySupplier;
    private static Supplier<SnapshotManagerService> snapshotManagerSupplier;
    private static Supplier<TerritoryImpactCache> territoryImpactCacheSupplier;
    private static Supplier<OptimizerService> optimizerSupplier;
    private static Supplier<OperationsHealthService> operationsHealthSupplier;
    private static Supplier<DebugBundleService> debugBundleSupplier;
    private static boolean warningShown;

    private SyllySettingsController() {}

    public static void register(
            Supplier<SyllyConfigService> settings,
            Supplier<SpellProfileService> profiles,
            Supplier<ObservedStateRepository> repository,
            Supplier<SnapshotManagerService> snapshotManager,
            Supplier<TerritoryImpactCache> territoryImpactCache,
            Supplier<OptimizerService> optimizer,
            Supplier<OperationsHealthService> operationsHealth,
            Supplier<DebugBundleService> debugBundle) {
        settingsSupplier = Objects.requireNonNull(settings, "settings");
        profilesSupplier = Objects.requireNonNull(profiles, "profiles");
        repositorySupplier = Objects.requireNonNull(repository, "repository");
        snapshotManagerSupplier = Objects.requireNonNull(snapshotManager, "snapshotManager");
        territoryImpactCacheSupplier = Objects.requireNonNull(territoryImpactCache, "territoryImpactCache");
        optimizerSupplier = Objects.requireNonNull(optimizer, "optimizer");
        operationsHealthSupplier = Objects.requireNonNull(operationsHealth, "operationsHealth");
        debugBundleSupplier = Objects.requireNonNull(debugBundle, "debugBundle");

        KeyMapping openSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.syllyaddons.open_settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                client.setScreen(createScreen(null));
            }
            if (!warningShown && client.player != null) {
                SyllyConfigService service = settingsSupplier.get();
                if (service != null && service.snapshot().configurationWarnings()) {
                    service.warning().ifPresent(warning -> client.player.displayClientMessage(
                            Component.literal("[SyllyAddons] " + warning), false));
                }
                warningShown = true;
            }
        });
    }

    public static Screen createScreen(Screen parent) {
        if (settingsSupplier == null || profilesSupplier == null || repositorySupplier == null
                || snapshotManagerSupplier == null || territoryImpactCacheSupplier == null || optimizerSupplier == null
                || operationsHealthSupplier == null || debugBundleSupplier == null) {
            throw new IllegalStateException("Sylly Addons settings are not initialized");
        }
        return new SyllySettingsScreen(
                parent,
                settingsSupplier.get(),
                profilesSupplier,
                repositorySupplier.get(),
                snapshotManagerSupplier.get(),
                territoryImpactCacheSupplier.get(),
                optimizerSupplier.get(),
                operationsHealthSupplier.get(),
                debugBundleSupplier.get());
    }
}
