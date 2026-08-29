package net.syllyaddons.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TerritoryState(
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
    public TerritoryState {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        name = name.strip();
        owner = Objects.requireNonNull(owner, "owner");
        acquiredAtEpochMillis = Objects.requireNonNull(acquiredAtEpochMillis, "acquiredAtEpochMillis");
        headquarters = Objects.requireNonNull(headquarters, "headquarters");
        bounds = Objects.requireNonNull(bounds, "bounds");
        links = copyList(Objects.requireNonNull(links, "links"));
        resources = copyMap(Objects.requireNonNull(resources, "resources"));
        treasury = Objects.requireNonNull(treasury, "treasury");
        treasuryBonusPercent = Objects.requireNonNull(treasuryBonusPercent, "treasuryBonusPercent");
        defences = Objects.requireNonNull(defences, "defences");
        upgrades = copyMap(Objects.requireNonNull(upgrades, "upgrades"));
        alerts = copyList(Objects.requireNonNull(alerts, "alerts"));
    }

    public static TerritoryState empty(String name) {
        return new TerritoryState(
                name,
                ObservedValue.unknown("Owner has not been observed"),
                ObservedValue.unknown("Acquisition time has not been observed"),
                ObservedValue.unknown("HQ status has not been observed"),
                ObservedValue.unknown("Bounds have not been observed"),
                ObservedValue.unknown("Links have not been observed"),
                ObservedValue.unknown("Resources have not been observed"),
                ObservedValue.unknown("Treasury has not been observed"),
                ObservedValue.unknown("Treasury bonus has not been observed"),
                ObservedValue.unknown("Defences have not been observed"),
                ObservedValue.unknown("Upgrades have not been observed"),
                ObservedValue.unknown("Alerts have not been observed"));
    }

    private static <K, V> ObservedValue<Map<K, V>> copyMap(ObservedValue<Map<K, V>> value) {
        if (!value.isKnown()) return value;
        return ObservedValue.known(Map.copyOf(value.value()), value.evidence());
    }

    private static <E> ObservedValue<List<E>> copyList(ObservedValue<List<E>> value) {
        if (!value.isKnown()) return value;
        return ObservedValue.known(List.copyOf(value.value()), value.evidence());
    }
}
