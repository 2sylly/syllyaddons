package net.syllyaddons.map;

public record TerritoryMapFocus(float centerX, float centerZ, float zoomLevel, int territoryCount) {
    public TerritoryMapFocus {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerZ) || !Float.isFinite(zoomLevel)) {
            throw new IllegalArgumentException("Map focus values must be finite");
        }
        if (zoomLevel < 1 || zoomLevel > 100) {
            throw new IllegalArgumentException("Zoom level must be between 1 and 100");
        }
        if (territoryCount <= 0) throw new IllegalArgumentException("territoryCount must be positive");
    }
}
