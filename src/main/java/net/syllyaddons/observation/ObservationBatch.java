package net.syllyaddons.observation;

import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.CharacterIdentity;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.RoutingMode;

/** Null scalar fields mean that the source had no new observation for that field. */
public record ObservationBatch(
        long capturedAtEpochMillis,
        ObservedValue<CharacterIdentity> character,
        ObservedValue<GuildIdentity> guild,
        ObservedValue<String> hqTerritory,
        ObservedValue<RoutingMode> routingMode,
        Map<String, TerritoryObservation> territories) {
    public ObservationBatch {
        if (capturedAtEpochMillis < 0) throw new IllegalArgumentException("capturedAtEpochMillis must be non-negative");
        territories = Map.copyOf(Objects.requireNonNull(territories, "territories"));
    }

    public static ObservationBatch territories(long capturedAtEpochMillis, Map<String, TerritoryObservation> values) {
        return new ObservationBatch(capturedAtEpochMillis, null, null, null, null, values);
    }
}
