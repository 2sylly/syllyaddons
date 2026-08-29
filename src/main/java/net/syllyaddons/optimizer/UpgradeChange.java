package net.syllyaddons.optimizer;

import net.syllyaddons.domain.ResourceType;

public record UpgradeChange(
        UpgradeCoordinate coordinate,
        String displayName,
        int beforeLevel,
        int afterLevel,
        ResourceType savedResource,
        long hourlySaving) {
    public UpgradeChange {
        java.util.Objects.requireNonNull(coordinate, "coordinate");
        displayName = displayName == null ? coordinate.upgradeKey() : displayName.strip();
        java.util.Objects.requireNonNull(savedResource, "savedResource");
        if (beforeLevel < afterLevel || afterLevel < 0 || hourlySaving < 0) {
            throw new IllegalArgumentException("Invalid downgrade change");
        }
    }
}
