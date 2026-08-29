package net.syllyaddons.advisor;

public record QueueTimerValidation(
        String territory,
        int menuTimerSeconds,
        int queuedTimerSeconds,
        long observedAtEpochMillis) {
    public QueueTimerValidation {
        territory = territory == null ? "" : territory.strip();
    }

    public boolean matches() {
        return Math.abs(menuTimerSeconds - queuedTimerSeconds) <= 5;
    }
}
