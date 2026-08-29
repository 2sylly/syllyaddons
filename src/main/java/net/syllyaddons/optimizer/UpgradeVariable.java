package net.syllyaddons.optimizer;

import net.syllyaddons.economy.UpgradeCatalog;
import net.syllyaddons.economy.UpgradeDefinition;

public record UpgradeVariable(UpgradeCoordinate coordinate, int minimumLevel, int currentLevel) {
    public UpgradeVariable {
        java.util.Objects.requireNonNull(coordinate, "coordinate");
        UpgradeDefinition definition = UpgradeCatalog.find(coordinate.upgradeKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown upgrade " + coordinate.upgradeKey()));
        if (!definition.hasQuantifiedEconomicEffect()) {
            throw new IllegalArgumentException("Upgrade is not a quantified economy variable: " + coordinate.upgradeKey());
        }
        if (!definition.isValidLevel(minimumLevel)
                || !definition.isValidLevel(currentLevel)
                || minimumLevel < 0
                || minimumLevel > currentLevel) {
            throw new IllegalArgumentException("Invalid level range for " + coordinate);
        }
    }

    public long maximumHourlySaving() {
        UpgradeDefinition definition = UpgradeCatalog.find(coordinate.upgradeKey()).orElseThrow();
        return definition.costAt(currentLevel) - definition.costAt(minimumLevel);
    }
}
