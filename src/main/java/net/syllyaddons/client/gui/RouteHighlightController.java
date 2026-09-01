package net.syllyaddons.client.gui;

import com.wynntils.screens.maps.GuildMapScreen;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.render.RenderUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.mixin.AbstractMapScreenAccessor;

public final class RouteHighlightController {
    private static volatile HighlightSelection selection = HighlightSelection.empty();
    private static boolean registered;

    private RouteHighlightController() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GuildMapScreen) || !(screen instanceof AbstractMapScreenAccessor map)) return;
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, tickDelta) ->
                    render(graphics, map));
        });
    }

    public static void highlight(EcoSnapshot snapshot, List<String> route, String label) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(route, "route");
        Map<String, TerritoryBounds> bounds = new LinkedHashMap<>();
        for (String territory : route) {
            var state = snapshot.territories().get(territory);
            if (state != null && state.bounds().isKnown()) bounds.put(territory, state.bounds().value());
        }
        selection = new HighlightSelection(List.copyOf(route), Map.copyOf(bounds), label == null ? "" : label);
    }

    public static void clear() {
        selection = HighlightSelection.empty();
    }

    public static List<String> activeRoute() {
        return selection.route();
    }

    private static void render(GuiGraphics graphics, AbstractMapScreenAccessor map) {
        HighlightSelection current = selection;
        if (current.route().isEmpty()) return;
        float mapCenterX = map.syllyaddons$getMapCenterX();
        float mapCenterZ = map.syllyaddons$getMapCenterZ();
        float centerX = map.syllyaddons$getCenterX();
        float centerZ = map.syllyaddons$getCenterZ();
        float zoom = map.syllyaddons$getZoomRenderScale();
        int left = (int) map.syllyaddons$getRenderX();
        int top = (int) map.syllyaddons$getRenderY();
        int right = (int) Math.ceil(map.syllyaddons$getRenderX() + map.syllyaddons$getRenderWidth());
        int bottom = (int) Math.ceil(map.syllyaddons$getRenderY() + map.syllyaddons$getRenderHeight());
        graphics.enableScissor(left, top, right, bottom);
        try {
            for (int index = 1; index < current.route().size(); index++) {
                TerritoryBounds from = current.bounds().get(current.route().get(index - 1));
                TerritoryBounds to = current.bounds().get(current.route().get(index));
                if (from == null || to == null) continue;
                float fromX = renderX(from, mapCenterX, centerX, zoom);
                float fromZ = renderZ(from, mapCenterZ, centerZ, zoom);
                float toX = renderX(to, mapCenterX, centerX, zoom);
                float toZ = renderZ(to, mapCenterZ, centerZ, zoom);
                RenderUtils.drawLine(graphics, CommonColors.DARK_GRAY, fromX, fromZ, toX, toZ, 5f);
                RenderUtils.drawLine(graphics, CommonColors.LIGHT_BLUE, fromX, fromZ, toX, toZ, 2.5f);
            }
            for (String territory : current.route()) {
                TerritoryBounds bounds = current.bounds().get(territory);
                if (bounds == null) continue;
                int x = Math.round(renderX(bounds, mapCenterX, centerX, zoom));
                int z = Math.round(renderZ(bounds, mapCenterZ, centerZ, zoom));
                graphics.fill(x - 3, z - 3, x + 4, z + 4, 0xFF00E9FF);
                graphics.fill(x - 1, z - 1, x + 2, z + 2, 0xFFFFFFFF);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static float renderX(TerritoryBounds bounds, float mapCenter, float screenCenter, float zoom) {
        double worldCenter = ((double) bounds.minX() + bounds.maxX()) / 2.0;
        return (float) (screenCenter + (worldCenter - mapCenter) * zoom);
    }

    private static float renderZ(TerritoryBounds bounds, float mapCenter, float screenCenter, float zoom) {
        double worldCenter = ((double) bounds.minZ() + bounds.maxZ()) / 2.0;
        return (float) (screenCenter + (worldCenter - mapCenter) * zoom);
    }

    private record HighlightSelection(
            List<String> route, Map<String, TerritoryBounds> bounds, String label) {
        private static HighlightSelection empty() {
            return new HighlightSelection(List.of(), Map.of(), "");
        }
    }
}
