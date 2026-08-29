package net.syllyaddons.optimizer;

import java.util.EnumMap;
import java.util.Map;
import net.syllyaddons.domain.ResourceType;

public record OptimizationRequest(
        OptimizationObjective objective,
        Map<ResourceType, Double> minimumReserves,
        boolean requireNoDeficits,
        long nodeLimit,
        long timeLimitMillis) {
    public static final long MIN_NODE_LIMIT = 100;
    public static final long MAX_NODE_LIMIT = 250_000;
    public static final long MIN_TIME_LIMIT_MILLIS = 50;
    public static final long MAX_TIME_LIMIT_MILLIS = 10_000;

    public OptimizationRequest {
        java.util.Objects.requireNonNull(objective, "objective");
        java.util.Objects.requireNonNull(minimumReserves, "minimumReserves");
        EnumMap<ResourceType, Double> copy = new EnumMap<>(ResourceType.class);
        minimumReserves.forEach((resource, value) -> {
            if (resource == null || value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Reserve floors must be finite and non-negative");
            }
            copy.put(resource, value);
        });
        minimumReserves = Map.copyOf(copy);
        if (nodeLimit < MIN_NODE_LIMIT || nodeLimit > MAX_NODE_LIMIT) {
            throw new IllegalArgumentException("nodeLimit must be between " + MIN_NODE_LIMIT + " and " + MAX_NODE_LIMIT);
        }
        if (timeLimitMillis < MIN_TIME_LIMIT_MILLIS || timeLimitMillis > MAX_TIME_LIMIT_MILLIS) {
            throw new IllegalArgumentException(
                    "timeLimitMillis must be between " + MIN_TIME_LIMIT_MILLIS + " and " + MAX_TIME_LIMIT_MILLIS);
        }
    }
}
