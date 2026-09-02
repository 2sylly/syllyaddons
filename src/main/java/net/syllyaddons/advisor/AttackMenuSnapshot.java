package net.syllyaddons.advisor;

import java.util.List;
import java.util.OptionalInt;

/** Parsed evidence from the attack menu. Empty fields stay empty; they are never inferred by the parser. */
public record AttackMenuSnapshot(
        String target,
        OptionalInt observedTimerSeconds,
        List<String> observedRoute,
        long observedAtEpochMillis,
        List<String> diagnostics) {
    public AttackMenuSnapshot {
        target = target == null ? "" : target.strip();
        observedTimerSeconds = observedTimerSeconds == null ? OptionalInt.empty() : observedTimerSeconds;
        observedRoute = observedRoute == null ? List.of() : List.copyOf(observedRoute);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (observedAtEpochMillis < 0) throw new IllegalArgumentException("observedAtEpochMillis must be non-negative");
    }

    public boolean hasRequiredInputs() {
        return !target.isBlank() && observedTimerSeconds.isPresent();
    }
}
