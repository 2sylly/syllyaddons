package net.syllyaddons.routing;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.TerritoryBounds;

public record TerritoryNode(
        String name,
        String ownerId,
        TerritoryBounds bounds,
        List<String> declaredLinks,
        boolean linksKnown) {
    public TerritoryNode(String name, String ownerId, TerritoryBounds bounds, List<String> declaredLinks) {
        this(name, ownerId, bounds, declaredLinks, true);
    }

    public TerritoryNode {
        name = normalizeRequired(name, "name");
        ownerId = ownerId == null ? "" : ownerId.strip();
        declaredLinks = List.copyOf(Objects.requireNonNull(declaredLinks, "declaredLinks"));
    }

    public double centerX() {
        if (bounds == null) return Double.NaN;
        return ((double) bounds.minX() + bounds.maxX()) / 2.0;
    }

    public double centerZ() {
        if (bounds == null) return Double.NaN;
        return ((double) bounds.minZ() + bounds.maxZ()) / 2.0;
    }

    private static String normalizeRequired(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
