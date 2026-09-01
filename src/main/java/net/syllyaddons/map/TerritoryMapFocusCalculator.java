package net.syllyaddons.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryState;

/** Calculates a deterministic map camera that contains every observed territory held by the current guild. */
public final class TerritoryMapFocusCalculator {
    static final float MIN_ZOOM_LEVEL = 1;
    static final float MAX_ZOOM_LEVEL = 100;
    static final float EDGE_PADDING_PIXELS = 24;
    private static final int ZOOM_SEARCH_STEPS = 32;

    public Optional<TerritoryMapFocus> calculate(
            ObservedState state,
            float viewportWidth,
            float viewportHeight,
            DoubleUnaryOperator renderScaleAtZoomLevel) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(renderScaleAtZoomLevel, "renderScaleAtZoomLevel");
        if (!state.guild().isKnown()) return Optional.empty();

        GuildIdentity guild = state.guild().value();
        List<TerritoryBounds> ownedBounds = new ArrayList<>();
        for (TerritoryState territory : state.territories().values()) {
            if (!territory.owner().isKnown() || !ownedBy(territory, guild)) continue;
            // Fitting only a subset would create a confidently wrong camera. Retry when all held bounds are known.
            if (!territory.bounds().isKnown()) return Optional.empty();
            ownedBounds.add(territory.bounds().value());
        }
        return calculate(ownedBounds, viewportWidth, viewportHeight, EDGE_PADDING_PIXELS, renderScaleAtZoomLevel);
    }

    static Optional<TerritoryMapFocus> calculate(
            List<TerritoryBounds> bounds,
            float viewportWidth,
            float viewportHeight,
            float edgePadding,
            DoubleUnaryOperator renderScaleAtZoomLevel) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(renderScaleAtZoomLevel, "renderScaleAtZoomLevel");
        if (bounds.isEmpty()
                || !Float.isFinite(viewportWidth)
                || !Float.isFinite(viewportHeight)
                || viewportWidth <= 0
                || viewportHeight <= 0) {
            return Optional.empty();
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TerritoryBounds territory : bounds) {
            minX = Math.min(minX, territory.minX());
            minZ = Math.min(minZ, territory.minZ());
            maxX = Math.max(maxX, territory.maxX());
            maxZ = Math.max(maxZ, territory.maxZ());
        }

        double paddingX = Math.min(Math.max(0, edgePadding), viewportWidth * 0.2);
        double paddingZ = Math.min(Math.max(0, edgePadding), viewportHeight * 0.2);
        double availableWidth = Math.max(1, viewportWidth - paddingX * 2);
        double availableHeight = Math.max(1, viewportHeight - paddingZ * 2);
        double worldWidth = Math.max(1, (double) maxX - minX);
        double worldHeight = Math.max(1, (double) maxZ - minZ);
        double maximumScale = Math.min(availableWidth / worldWidth, availableHeight / worldHeight);

        float zoomLevel = largestFittingZoom(maximumScale, renderScaleAtZoomLevel);
        float centerX = (float) ((minX / 2.0) + (maxX / 2.0));
        float centerZ = (float) ((minZ / 2.0) + (maxZ / 2.0));
        return Optional.of(new TerritoryMapFocus(centerX, centerZ, zoomLevel, bounds.size()));
    }

    private static float largestFittingZoom(
            double maximumScale, DoubleUnaryOperator renderScaleAtZoomLevel) {
        double minimumScale = checkedScale(renderScaleAtZoomLevel, MIN_ZOOM_LEVEL);
        if (maximumScale <= minimumScale) return MIN_ZOOM_LEVEL;
        double maximumZoomScale = checkedScale(renderScaleAtZoomLevel, MAX_ZOOM_LEVEL);
        if (maximumScale >= maximumZoomScale) return MAX_ZOOM_LEVEL;

        double low = MIN_ZOOM_LEVEL;
        double high = MAX_ZOOM_LEVEL;
        for (int step = 0; step < ZOOM_SEARCH_STEPS; step++) {
            double candidate = (low + high) / 2.0;
            if (checkedScale(renderScaleAtZoomLevel, candidate) <= maximumScale) {
                low = candidate;
            } else {
                high = candidate;
            }
        }
        return (float) low;
    }

    private static double checkedScale(DoubleUnaryOperator renderScaleAtZoomLevel, double zoomLevel) {
        double scale = renderScaleAtZoomLevel.applyAsDouble(zoomLevel);
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Zoom render scale must be finite and positive");
        }
        return scale;
    }

    private static boolean ownedBy(TerritoryState territory, GuildIdentity guild) {
        var owner = territory.owner().value();
        return (!guild.uuid().isBlank() && guild.uuid().equals(owner.guildUuid()))
                || (!guild.name().isBlank() && guild.name().equals(owner.guildName()));
    }
}
