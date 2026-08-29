package net.syllyaddons.config;

import net.syllyaddons.optimizer.OptimizationObjective;
import net.syllyaddons.optimizer.OptimizationRequest;

/** Persisted bounds and constraints for the read-only Track 10 optimizer. */
public record OptimizerConfig(
        OptimizationObjective objective,
        int reserveFloorPercent,
        boolean requireNoDeficits,
        int nodeLimit,
        int timeLimitMillis) {
    public static final int MIN_RESERVE_PERCENT = 0;
    public static final int MAX_RESERVE_PERCENT = 100;

    public OptimizerConfig {
        java.util.Objects.requireNonNull(objective, "objective");
        if (reserveFloorPercent < MIN_RESERVE_PERCENT || reserveFloorPercent > MAX_RESERVE_PERCENT) {
            throw new IllegalArgumentException("Reserve floor must be between 0 and 100 percent");
        }
        if (nodeLimit < OptimizationRequest.MIN_NODE_LIMIT || nodeLimit > OptimizationRequest.MAX_NODE_LIMIT) {
            throw new IllegalArgumentException("Node limit must be between "
                    + OptimizationRequest.MIN_NODE_LIMIT + " and " + OptimizationRequest.MAX_NODE_LIMIT);
        }
        if (timeLimitMillis < OptimizationRequest.MIN_TIME_LIMIT_MILLIS
                || timeLimitMillis > OptimizationRequest.MAX_TIME_LIMIT_MILLIS) {
            throw new IllegalArgumentException("Time limit must be between "
                    + OptimizationRequest.MIN_TIME_LIMIT_MILLIS + " and "
                    + OptimizationRequest.MAX_TIME_LIMIT_MILLIS + " milliseconds");
        }
    }

    public static OptimizerConfig defaults() {
        return new OptimizerConfig(OptimizationObjective.MINIMUM_EXPENSE, 25, true, 20_000, 750);
    }

    public OptimizerConfig withObjective(OptimizationObjective value) {
        return new OptimizerConfig(value, reserveFloorPercent, requireNoDeficits, nodeLimit, timeLimitMillis);
    }

    public OptimizerConfig withReserveFloorPercent(int value) {
        return new OptimizerConfig(objective, value, requireNoDeficits, nodeLimit, timeLimitMillis);
    }

    public OptimizerConfig withRequireNoDeficits(boolean value) {
        return new OptimizerConfig(objective, reserveFloorPercent, value, nodeLimit, timeLimitMillis);
    }

    public OptimizerConfig withNodeLimit(int value) {
        return new OptimizerConfig(objective, reserveFloorPercent, requireNoDeficits, value, timeLimitMillis);
    }

    public OptimizerConfig withTimeLimitMillis(int value) {
        return new OptimizerConfig(objective, reserveFloorPercent, requireNoDeficits, nodeLimit, value);
    }
}
