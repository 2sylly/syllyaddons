package net.syllyaddons.observation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryRating;

/** Null fields mean that this batch did not observe that field. */
public record TerritoryObservation(
        String name,
        ObservedValue<TerritoryOwner> owner,
        ObservedValue<Long> acquiredAtEpochMillis,
        ObservedValue<Boolean> headquarters,
        ObservedValue<TerritoryBounds> bounds,
        ObservedValue<List<String>> links,
        ObservedValue<Map<ResourceType, ResourceBalance>> resources,
        ObservedValue<TerritoryRating> treasury,
        ObservedValue<Double> treasuryBonusPercent,
        ObservedValue<TerritoryRating> defences,
        ObservedValue<Map<String, Integer>> upgrades,
        ObservedValue<List<String>> alerts) {
    public TerritoryObservation {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        name = name.strip();
    }
}
