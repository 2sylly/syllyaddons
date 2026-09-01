package net.syllyaddons.config;

/** Persisted interaction setting for the Track 9 attack-routing advisor. */
public record RoutingAdvisorConfig(boolean blockAttackWhenFastestIsFaster) {

    public static RoutingAdvisorConfig defaults() {
        return new RoutingAdvisorConfig(true);
    }

    public RoutingAdvisorConfig withBlockAttackWhenFastestIsFaster(boolean value) {
        return new RoutingAdvisorConfig(value);
    }
}
