package net.syllyaddons.domain;

public record TerritoryBounds(int minX, int minZ, int maxX, int maxZ) {
    public TerritoryBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Territory bounds are inverted");
        }
    }
}
