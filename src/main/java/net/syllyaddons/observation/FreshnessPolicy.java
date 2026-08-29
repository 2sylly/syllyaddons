package net.syllyaddons.observation;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class FreshnessPolicy {
    private final Map<DataGroup, Duration> maxAges;

    public FreshnessPolicy(Map<DataGroup, Duration> maxAges) {
        EnumMap<DataGroup, Duration> copy = new EnumMap<>(DataGroup.class);
        copy.putAll(Objects.requireNonNull(maxAges, "maxAges"));
        for (DataGroup group : DataGroup.values()) {
            Duration maxAge = copy.get(group);
            if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
                throw new IllegalArgumentException("Missing positive freshness duration for " + group);
            }
        }
        this.maxAges = Map.copyOf(copy);
    }

    public static FreshnessPolicy personalDefaults() {
        return new FreshnessPolicy(Map.of(
                DataGroup.CHARACTER, Duration.ofMinutes(10),
                DataGroup.GUILD, Duration.ofMinutes(10),
                DataGroup.HEADQUARTERS, Duration.ofMinutes(5),
                DataGroup.ROUTING_MODE, Duration.ofMinutes(30),
                DataGroup.OWNERSHIP, Duration.ofSeconds(30),
                DataGroup.TOPOLOGY, Duration.ofMinutes(5),
                DataGroup.PUBLIC_RESOURCES, Duration.ofMinutes(2),
                DataGroup.LOCAL_ECONOMY, Duration.ofMinutes(30)));
    }

    public Duration maxAge(DataGroup group) {
        return maxAges.get(group);
    }
}
