package net.syllyaddons.compat.wynntils.v4_2_9;

/** Explicit lifecycle policy so a same-character world re-entry cannot depend on another character-change event. */
record WorldTransitionPlan(boolean clearSession, boolean recaptureSession, boolean refreshTerritories) {
    static WorldTransitionPlan between(boolean wasOnWorld, boolean isOnWorld) {
        return new WorldTransitionPlan(
                wasOnWorld && !isOnWorld,
                !wasOnWorld && isOnWorld,
                !wasOnWorld && isOnWorld);
    }
}
