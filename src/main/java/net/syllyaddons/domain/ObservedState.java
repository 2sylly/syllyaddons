package net.syllyaddons.domain;

import java.util.Map;
import java.util.Objects;

public record ObservedState(
        int schemaVersion,
        long revision,
        long assembledAtEpochMillis,
        ObservedValue<CharacterIdentity> character,
        ObservedValue<GuildIdentity> guild,
        ObservedValue<String> hqTerritory,
        ObservedValue<RoutingMode> routingMode,
        Map<String, TerritoryState> territories) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ObservedState {
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        if (revision < 0 || assembledAtEpochMillis < 0) {
            throw new IllegalArgumentException("Revision and timestamp must be non-negative");
        }
        character = Objects.requireNonNull(character, "character");
        guild = Objects.requireNonNull(guild, "guild");
        hqTerritory = Objects.requireNonNull(hqTerritory, "hqTerritory");
        routingMode = Objects.requireNonNull(routingMode, "routingMode");
        territories = Map.copyOf(Objects.requireNonNull(territories, "territories"));
    }

    public static ObservedState empty() {
        return new ObservedState(
                CURRENT_SCHEMA_VERSION,
                0,
                0,
                ObservedValue.unknown("No character selected"),
                ObservedValue.unknown("Guild has not been observed"),
                ObservedValue.unknown("HQ has not been observed"),
                ObservedValue.unknown("Routing mode has not been observed"),
                Map.of());
    }
}
