package net.syllyaddons.client.gui;

import com.wynntils.screens.territorymanagement.TerritoryManagementScreen;
import com.wynntils.utils.render.MapRenderer;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.syllyaddons.map.TerritoryMapFocusCalculator;
import net.syllyaddons.mixin.AbstractMapScreenAccessor;
import net.syllyaddons.observation.ObservedStateRepository;

/** Applies one owned-territory camera fit when Wynntils initializes its territory management screen. */
public final class TerritoryManagementAutoFocusController {
    private static final int MAX_RETRY_TICKS = 20 * 10;
    private static final TerritoryMapFocusCalculator CALCULATOR = new TerritoryMapFocusCalculator();
    private static Supplier<ObservedStateRepository> repositorySupplier;
    private static boolean registered;

    private TerritoryManagementAutoFocusController() {}

    public static synchronized void register(Supplier<ObservedStateRepository> repository) {
        repositorySupplier = Objects.requireNonNull(repository, "repository");
        if (registered) return;
        registered = true;
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TerritoryManagementScreen management)
                    || !(screen instanceof AbstractMapScreenAccessor map)) {
                return;
            }
            FocusAttempt attempt = new FocusAttempt(management, map);
            attempt.applyIfReady();
            ScreenEvents.afterTick(screen).register(ignored -> attempt.applyIfReady());
        });
    }

    private static final class FocusAttempt {
        private final TerritoryManagementScreen screen;
        private final AbstractMapScreenAccessor map;
        private int remainingTicks = MAX_RETRY_TICKS;
        private boolean complete;

        private FocusAttempt(TerritoryManagementScreen screen, AbstractMapScreenAccessor map) {
            this.screen = screen;
            this.map = map;
        }

        private void applyIfReady() {
            if (complete || remainingTicks-- <= 0) return;
            CALCULATOR.calculate(
                            repositorySupplier.get().snapshot(),
                            map.syllyaddons$getMapWidth(),
                            map.syllyaddons$getMapHeight(),
                            level -> MapRenderer.getZoomRenderScaleFromLevel((float) level))
                    .ifPresent(focus -> {
                        screen.setMapPosition(focus.centerX(), focus.centerZ(), focus.zoomLevel());
                        complete = true;
                    });
        }
    }
}
