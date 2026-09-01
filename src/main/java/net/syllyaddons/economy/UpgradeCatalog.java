package net.syllyaddons.economy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.syllyaddons.domain.ResourceType;

/** Exact data verified against Wynntils 4.2.9 TerritoryUpgrade and TerritoryItem. */
public final class UpgradeCatalog {
    public static final String VERSION = "wynntils-4.2.9-territory-upgrades";
    public static final String SOURCE =
            "Wynntils v4.2.9 TerritoryUpgrade.java and TerritoryItem.java (tag commit 9a3562a4)";

    private static final Map<String, UpgradeDefinition> DEFINITIONS = definitions();

    private UpgradeCatalog() {}

    public static Optional<UpgradeDefinition> find(String key) {
        return Optional.ofNullable(DEFINITIONS.get(key));
    }

    public static Map<String, UpgradeDefinition> definitionsByKey() {
        return DEFINITIONS;
    }

    public static Map<ResourceType, Double> expensesPerHour(Map<String, Integer> upgrades) {
        Map<ResourceType, Double> expenses = new java.util.EnumMap<>(ResourceType.class);
        upgrades.forEach((key, level) -> find(key)
                .filter(definition -> definition.isValidLevel(level))
                .ifPresent(definition -> expenses.merge(
                        definition.upkeepResource(), (double) definition.costAt(level), Double::sum)));
        return Map.copyOf(expenses);
    }

    private static Map<String, UpgradeDefinition> definitions() {
        Map<String, UpgradeDefinition> values = new LinkedHashMap<>();
        add(values, "DAMAGE", "Damage", ResourceType.ORE, costs(0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800));
        add(values, "ATTACK", "Attack", ResourceType.CROPS, costs(0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800));
        add(values, "HEALTH", "Health", ResourceType.WOOD, costs(0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800));
        add(values, "DEFENCE", "Defence", ResourceType.FISH, costs(0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800));
        add(values, "STRONGER_MINIONS", "Stronger Minions", ResourceType.WOOD, costs(0, 200, 400, 800, 1600));
        add(values, "TOWER_MULTI_ATTACKS", "Tower Multi-Attacks", ResourceType.FISH, costs(0, 4800));
        add(values, "TOWER_AURA", "Tower Aura", ResourceType.CROPS, costs(0, 800, 1600, 3200));
        add(values, "TOWER_VOLLEY", "Tower Volley", ResourceType.ORE, costs(0, 200, 400, 800));
        add(values, "GATHERING_EXPERIENCE", "Gathering Experience", ResourceType.WOOD, costs(0, 600, 1300, 2000, 2700, 3400, 5500, 10000, 20000));
        add(values, "MOB_EXPERIENCE", "Mob Experience", ResourceType.FISH, costs(0, 600, 1200, 1800, 2400, 3000, 5000, 10000, 20000));
        add(values, "MOB_DAMAGE", "Mob Damage", ResourceType.CROPS, costs(0, 600, 1200, 1800, 2400, 3000, 5000, 10000, 20000));
        add(values, "PVP_DAMAGE", "PvP Damage", ResourceType.ORE, costs(0, 600, 1200, 1800, 2400, 3000, 5000, 10000, 20000));
        add(values, "XP_SEEKING", "XP Seeking", ResourceType.EMERALDS, costs(0, 100, 200, 400, 800, 1600, 3200, 6400, 9600, 12800));
        add(values, "TOME_SEEKING", "Tome Seeking", ResourceType.FISH, costs(0, 400, 3200, 6400));
        add(values, "EMERALD_SEEKING", "Emerald Seeking", ResourceType.WOOD, costs(0, 200, 800, 1600, 3200, 6400));
        add(values, "RESOURCE_STORAGE", "Larger Resource Storage", ResourceType.EMERALDS,
                costs(0, 400, 800, 2000, 5000, 16000, 48000), UpgradeEffect.RESOURCE_STORAGE,
                multipliers(1, 2, 4, 8, 15, 34, 80));
        add(values, "EMERALD_STORAGE", "Larger Emerald Storage", ResourceType.WOOD,
                costs(0, 200, 400, 1000, 2500, 8000, 24000), UpgradeEffect.EMERALD_STORAGE,
                multipliers(1, 2, 4, 8, 15, 34, 80));
        add(values, "EFFICIENT_RESOURCES", "Efficient Resources", ResourceType.EMERALDS,
                costs(0, 6000, 12000, 24000, 48000, 96000, 192000), UpgradeEffect.RESOURCE_PRODUCTION,
                multipliers(1, 1.5, 2, 2.5, 3, 3.5, 4));
        add(values, "RESOURCE_RATE", "Resource Rate", ResourceType.EMERALDS,
                costs(0, 6000, 18000, 32000), UpgradeEffect.RESOURCE_PRODUCTION,
                multipliers(1, 4.0 / 3.0, 2, 4));
        add(values, "EFFICIENT_EMERALDS", "Efficient Emeralds", ResourceType.ORE,
                costs(0, 2000, 8000, 32000), UpgradeEffect.EMERALD_PRODUCTION,
                multipliers(1, 1.35, 2, 4));
        add(values, "EMERALD_RATE", "Emerald Rate", ResourceType.CROPS,
                costs(0, 2000, 8000, 32000), UpgradeEffect.EMERALD_PRODUCTION,
                multipliers(1, 4.0 / 3.0, 2, 4));
        return Map.copyOf(values);
    }

    private static void add(
            Map<String, UpgradeDefinition> values,
            String key,
            String displayName,
            ResourceType resource,
            List<Long> costs) {
        add(values, key, displayName, resource, costs, UpgradeEffect.NONE, List.of());
    }

    private static void add(
            Map<String, UpgradeDefinition> values,
            String key,
            String displayName,
            ResourceType resource,
            List<Long> costs,
            UpgradeEffect effect,
            List<Double> multipliers) {
        values.put(key, new UpgradeDefinition(key, displayName, resource, costs, effect, multipliers));
    }

    private static List<Long> costs(long... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    private static List<Double> multipliers(double... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
