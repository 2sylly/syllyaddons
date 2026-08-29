package net.syllyaddons.domain;

import java.util.Map;
import java.util.Objects;

public record EcoSnapshot(
        int schemaVersion,
        long createdAtEpochMillis,
        long sourceRevision,
        ObservedValue<GuildIdentity> guild,
        ObservedValue<String> hqTerritory,
        ObservedValue<RoutingMode> routingMode,
        Map<String, TerritoryState> territories) {
    public EcoSnapshot {
        if (schemaVersion <= 0 || createdAtEpochMillis < 0 || sourceRevision < 0) {
            throw new IllegalArgumentException("Invalid snapshot metadata");
        }
        guild = Objects.requireNonNull(guild, "guild");
        hqTerritory = Objects.requireNonNull(hqTerritory, "hqTerritory");
        routingMode = Objects.requireNonNull(routingMode, "routingMode");
        territories = Map.copyOf(Objects.requireNonNull(territories, "territories"));
    }

    public static EcoSnapshot from(ObservedState state, long createdAtEpochMillis) {
        Objects.requireNonNull(state, "state");
        return new EcoSnapshot(
                state.schemaVersion(),
                createdAtEpochMillis,
                state.revision(),
                state.guild(),
                state.hqTerritory(),
                state.routingMode(),
                state.territories());
    }
}
