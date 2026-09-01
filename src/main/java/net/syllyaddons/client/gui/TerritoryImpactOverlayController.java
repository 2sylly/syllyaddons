package net.syllyaddons.client.gui;

import com.wynntils.screens.maps.GuildMapScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.impact.ImpactCacheView;
import net.syllyaddons.impact.ImpactOverlayFilter;
import net.syllyaddons.impact.ImpactVisualSeverity;
import net.syllyaddons.impact.RouteChangeKind;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.impact.TerritoryImpactReport;
import net.syllyaddons.mixin.AbstractMapScreenAccessor;
import net.syllyaddons.observation.ObservedStateRepository;

/** Track 8 map layer. It performs map lookups only; cache construction remains off the render thread. */
public final class TerritoryImpactOverlayController {
    private static final ImpactOverlayFilter FILTER = new ImpactOverlayFilter();
    private static Supplier<ObservedStateRepository> repositorySupplier;
    private static Supplier<TerritoryImpactCache> cacheSupplier;
    private static Supplier<SyllyConfigService> settingsSupplier;
    private static boolean registered;

    private TerritoryImpactOverlayController() {}

    public static synchronized void register(
            Supplier<ObservedStateRepository> repository,
            Supplier<TerritoryImpactCache> cache,
            Supplier<SyllyConfigService> settings) {
        repositorySupplier = Objects.requireNonNull(repository, "repository");
        cacheSupplier = Objects.requireNonNull(cache, "cache");
        settingsSupplier = Objects.requireNonNull(settings, "settings");
        if (registered) return;
        registered = true;
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GuildMapScreen) || !(screen instanceof AbstractMapScreenAccessor map)) return;
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, tickDelta) ->
                    render(graphics, mouseX, mouseY, map));
        });
    }

    private static void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            AbstractMapScreenAccessor map) {
        ObservedState state = repositorySupplier.get().snapshot();
        ImpactCacheView cache = cacheSupplier.get().view();
        SyllyConfig config = settingsSupplier.get().snapshot();
        if (!config.territoryImpactEnabled()) return;

        float mapCenterX = map.syllyaddons$getMapCenterX();
        float mapCenterZ = map.syllyaddons$getMapCenterZ();
        float centerX = map.syllyaddons$getCenterX();
        float centerZ = map.syllyaddons$getCenterZ();
        float zoom = map.syllyaddons$getZoomRenderScale();
        int left = (int) map.syllyaddons$getRenderX();
        int top = (int) map.syllyaddons$getRenderY();
        int right = (int) Math.ceil(map.syllyaddons$getRenderX() + map.syllyaddons$getRenderWidth());
        int bottom = (int) Math.ceil(map.syllyaddons$getRenderY() + map.syllyaddons$getRenderHeight());

        if (config.impactOverlayEnabled()) {
            graphics.enableScissor(left, top, right, bottom);
            try {
                for (TerritoryImpactReport report : cache.completedReports().values()) {
                    if (!FILTER.matches(report, state, config)) continue;
                    TerritoryState territory = state.territories().get(report.removedTerritory());
                    if (territory == null || !territory.bounds().isKnown()) continue;
                    renderRegion(graphics, territory.bounds().value(), report, mapCenterX, mapCenterZ,
                            centerX, centerZ, zoom);
                }
            } finally {
                graphics.disableScissor();
            }
        }

        if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom || zoom <= 0) return;
        double worldX = (mouseX - centerX) / zoom + mapCenterX;
        double worldZ = (mouseY - centerZ) / zoom + mapCenterZ;
        TerritoryImpactReport hovered = findHovered(state, cache, worldX, worldZ);
        if (hovered != null) renderSummary(graphics, hovered, cache, mouseX, mouseY);
    }

    private static void renderRegion(
            GuiGraphics graphics,
            TerritoryBounds bounds,
            TerritoryImpactReport report,
            float mapCenterX,
            float mapCenterZ,
            float centerX,
            float centerZ,
            float zoom) {
        int x1 = Math.round(centerX + (bounds.minX() - mapCenterX) * zoom);
        int x2 = Math.round(centerX + (bounds.maxX() - mapCenterX) * zoom);
        int y1 = Math.round(centerZ + (bounds.minZ() - mapCenterZ) * zoom);
        int y2 = Math.round(centerZ + (bounds.maxZ() - mapCenterZ) * zoom);
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2) + 1;
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2) + 1;
        ImpactVisualSeverity severity = ImpactVisualSeverity.forReport(report);
        graphics.fill(left, top, right, bottom, severity.fillColor());
        graphics.fill(left, top, right, top + 1, severity.borderColor());
        graphics.fill(left, bottom - 1, right, bottom, severity.borderColor());
        graphics.fill(left, top, left + 1, bottom, severity.borderColor());
        graphics.fill(right - 1, top, right, bottom, severity.borderColor());
    }

    private static TerritoryImpactReport findHovered(
            ObservedState state,
            ImpactCacheView cache,
            double worldX,
            double worldZ) {
        for (TerritoryState territory : state.territories().values()) {
            if (!territory.bounds().isKnown()) continue;
            TerritoryBounds bounds = territory.bounds().value();
            if (worldX < bounds.minX() || worldX > bounds.maxX()
                    || worldZ < bounds.minZ() || worldZ > bounds.maxZ()) continue;
            TerritoryImpactReport report = cache.completedReports().get(territory.name());
            if (report != null) return report;
        }
        return null;
    }

    private static void renderSummary(
            GuiGraphics graphics,
            TerritoryImpactReport report,
            ImpactCacheView cache,
            int mouseX,
            int mouseY) {
        long disconnected = report.modes().values().stream()
                .flatMap(mode -> mode.routeImpacts().stream())
                .filter(route -> route.changes().contains(RouteChangeKind.DISCONNECTED))
                .map(route -> route.sourceTerritory())
                .distinct()
                .count();
        long rerouted = report.modes().values().stream()
                .flatMap(mode -> mode.routeImpacts().stream())
                .filter(route -> route.changes().contains(RouteChangeKind.REROUTED))
                .map(route -> route.sourceTerritory())
                .distinct()
                .count();
        long ageSeconds = cache.builtAtEpochMillis() == 0
                ? 0
                : Math.max(0, System.currentTimeMillis() - cache.builtAtEpochMillis()) / 1_000;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("SyllyAddons · remove " + report.removedTerritory())
                .withStyle(color(report.maximumSeverity())));
        lines.add(Component.literal(report.maximumSeverity() + " · " + report.ownerRelation())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(disconnected + " disconnected · " + rerouted + " rerouted · max +"
                + ImpactOverlayFilter.maximumDelay(report) + "s").withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("Baseline rev " + report.sourceRevision() + " · " + ageSeconds + "s old"
                + (cache.reportsAreStale() ? " · stale/building" : "")).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Advisory snapshot; updates only on observed refreshes.")
                .withStyle(ChatFormatting.DARK_GRAY));
        graphics.setTooltipForNextFrame(lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
    }

    private static ChatFormatting color(net.syllyaddons.impact.ImpactSeverity severity) {
        return switch (severity) {
            case MINOR -> ChatFormatting.YELLOW;
            case WARNING -> ChatFormatting.GOLD;
            case CRITICAL -> ChatFormatting.RED;
            case CATASTROPHIC -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
