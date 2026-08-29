package net.syllyaddons.observation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryState;

public final class ObservedStateMerger {
    public ObservedState merge(ObservedState current, ObservationBatch batch) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(batch, "batch");

        ObservedValue<?> mergedCharacter = mergeValue(current.character(), batch.character());
        ObservedValue<?> mergedGuild = mergeValue(current.guild(), batch.guild());
        ObservedValue<?> mergedHq = mergeValue(current.hqTerritory(), batch.hqTerritory());
        ObservedValue<?> mergedRouting = mergeValue(current.routingMode(), batch.routingMode());

        Map<String, TerritoryState> territories = new HashMap<>(current.territories());
        batch.territories().forEach((name, observation) -> territories.compute(
                name,
                (ignored, existing) -> mergeTerritory(
                        existing == null ? TerritoryState.empty(observation.name()) : existing, observation)));

        ObservedState candidate = new ObservedState(
                current.schemaVersion(),
                current.revision(),
                current.assembledAtEpochMillis(),
                cast(mergedCharacter),
                cast(mergedGuild),
                cast(mergedHq),
                cast(mergedRouting),
                territories);

        if (candidate.equals(current)) return current;

        return new ObservedState(
                candidate.schemaVersion(),
                current.revision() + 1,
                batch.capturedAtEpochMillis(),
                candidate.character(),
                candidate.guild(),
                candidate.hqTerritory(),
                candidate.routingMode(),
                candidate.territories());
    }

    private TerritoryState mergeTerritory(TerritoryState current, TerritoryObservation incoming) {
        return new TerritoryState(
                current.name(),
                mergeValue(current.owner(), incoming.owner()),
                mergeValue(current.acquiredAtEpochMillis(), incoming.acquiredAtEpochMillis()),
                mergeValue(current.headquarters(), incoming.headquarters()),
                mergeValue(current.bounds(), incoming.bounds()),
                mergeValue(current.links(), incoming.links()),
                mergeValue(current.resources(), incoming.resources()),
                mergeValue(current.treasury(), incoming.treasury()),
                mergeValue(current.treasuryBonusPercent(), incoming.treasuryBonusPercent()),
                mergeValue(current.defences(), incoming.defences()),
                mergeValue(current.upgrades(), incoming.upgrades()),
                mergeValue(current.alerts(), incoming.alerts()));
    }

    private static <T> ObservedValue<T> mergeValue(ObservedValue<T> current, ObservedValue<T> incoming) {
        return incoming == null ? current : current.merge(incoming);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObservedValue<T> cast(ObservedValue<?> value) {
        return (ObservedValue<T>) value;
    }
}
