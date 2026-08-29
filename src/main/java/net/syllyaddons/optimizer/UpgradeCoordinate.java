package net.syllyaddons.optimizer;

public record UpgradeCoordinate(String territory, String upgradeKey) implements Comparable<UpgradeCoordinate> {
    public UpgradeCoordinate {
        territory = requireText(territory, "territory");
        upgradeKey = requireText(upgradeKey, "upgradeKey");
    }

    @Override
    public int compareTo(UpgradeCoordinate other) {
        int territoryOrder = territory.compareTo(other.territory);
        return territoryOrder != 0 ? territoryOrder : upgradeKey.compareTo(other.upgradeKey);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
