package net.syllyaddons.economy;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;

public record UpgradeDefinition(
        String key,
        String displayName,
        ResourceType upkeepResource,
        List<Long> levelCosts,
        UpgradeEffect effect,
        List<Double> effectMultipliers) {
    public UpgradeDefinition {
        key = Objects.requireNonNull(key, "key").strip();
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        upkeepResource = Objects.requireNonNull(upkeepResource, "upkeepResource");
        levelCosts = List.copyOf(Objects.requireNonNull(levelCosts, "levelCosts"));
        effect = Objects.requireNonNull(effect, "effect");
        effectMultipliers = List.copyOf(Objects.requireNonNull(effectMultipliers, "effectMultipliers"));
        if (key.isEmpty() || displayName.isEmpty() || levelCosts.isEmpty() || levelCosts.getFirst() != 0L) {
            throw new IllegalArgumentException("Invalid upgrade definition");
        }
        if (!effectMultipliers.isEmpty() && effectMultipliers.size() != levelCosts.size()) {
            throw new IllegalArgumentException("Effect multipliers must match level costs");
        }
    }

    public long costAt(int level) {
        if (level < 0 || level >= levelCosts.size()) throw new IllegalArgumentException("Invalid " + key + " level " + level);
        return levelCosts.get(level);
    }

    public boolean isValidLevel(int level) {
        return level >= 0 && level < levelCosts.size();
    }

    public long marginalSavingAt(int level) {
        return level <= 0 ? 0 : costAt(level) - costAt(level - 1);
    }

    public boolean hasQuantifiedEconomicEffect() {
        return effect != UpgradeEffect.NONE && !effectMultipliers.isEmpty();
    }

    public double marginalLoss(double currentValue, int level) {
        if (!hasQuantifiedEconomicEffect() || level <= 0 || level >= effectMultipliers.size()) return 0;
        double currentMultiplier = effectMultipliers.get(level);
        double previousMultiplier = effectMultipliers.get(level - 1);
        return currentValue * (1.0 - previousMultiplier / currentMultiplier);
    }
}
