package net.syllyaddons.config;

/** Persisted decision thresholds for the read-only Track 9 attack-routing advisor. */
public record RoutingAdvisorConfig(
        int minimumTimeSavingSeconds,
        int maximumAdditionalCostEmeralds,
        boolean activeOperationsOnly,
        int insignificantTimeSeconds,
        int insignificantCostEmeralds) {
    public static final int MIN_TIME_SECONDS = 0;
    public static final int MAX_TIME_SECONDS = 7_200;
    public static final int MIN_COST_EMERALDS = 0;
    public static final int MAX_COST_EMERALDS = 16_777_216;

    public RoutingAdvisorConfig {
        checkRange("Minimum time saving", minimumTimeSavingSeconds, MIN_TIME_SECONDS, MAX_TIME_SECONDS);
        checkRange("Maximum additional cost", maximumAdditionalCostEmeralds, MIN_COST_EMERALDS, MAX_COST_EMERALDS);
        checkRange("Insignificant time", insignificantTimeSeconds, MIN_TIME_SECONDS, MAX_TIME_SECONDS);
        checkRange("Insignificant cost", insignificantCostEmeralds, MIN_COST_EMERALDS, MAX_COST_EMERALDS);
    }

    public static RoutingAdvisorConfig defaults() {
        return new RoutingAdvisorConfig(60, 32_768, true, 30, 64);
    }

    public RoutingAdvisorConfig withMinimumTimeSavingSeconds(int value) {
        return new RoutingAdvisorConfig(
                value, maximumAdditionalCostEmeralds, activeOperationsOnly,
                insignificantTimeSeconds, insignificantCostEmeralds);
    }

    public RoutingAdvisorConfig withMaximumAdditionalCostEmeralds(int value) {
        return new RoutingAdvisorConfig(
                minimumTimeSavingSeconds, value, activeOperationsOnly,
                insignificantTimeSeconds, insignificantCostEmeralds);
    }

    public RoutingAdvisorConfig withActiveOperationsOnly(boolean value) {
        return new RoutingAdvisorConfig(
                minimumTimeSavingSeconds, maximumAdditionalCostEmeralds, value,
                insignificantTimeSeconds, insignificantCostEmeralds);
    }

    public RoutingAdvisorConfig withInsignificantTimeSeconds(int value) {
        return new RoutingAdvisorConfig(
                minimumTimeSavingSeconds, maximumAdditionalCostEmeralds, activeOperationsOnly,
                value, insignificantCostEmeralds);
    }

    public RoutingAdvisorConfig withInsignificantCostEmeralds(int value) {
        return new RoutingAdvisorConfig(
                minimumTimeSavingSeconds, maximumAdditionalCostEmeralds, activeOperationsOnly,
                insignificantTimeSeconds, value);
    }

    private static void checkRange(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
    }
}
