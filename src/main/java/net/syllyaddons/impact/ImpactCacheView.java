package net.syllyaddons.impact;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ImpactCacheView(
        ImpactCacheStatus status,
        long generation,
        long requestedRevision,
        String cacheKey,
        int completedTargets,
        int totalTargets,
        long builtAtEpochMillis,
        long buildDurationMillis,
        Map<String, TerritoryImpactReport> completedReports,
        String message,
        boolean reportsAreStale) {
    public ImpactCacheView {
        status = Objects.requireNonNull(status, "status");
        cacheKey = cacheKey == null ? "" : cacheKey;
        completedReports = Map.copyOf(Objects.requireNonNull(completedReports, "completedReports"));
        message = message == null ? "" : message.strip();
        if (generation < 0 || requestedRevision < 0 || completedTargets < 0 || totalTargets < 0
                || builtAtEpochMillis < 0 || buildDurationMillis < 0) {
            throw new IllegalArgumentException("Invalid cache view");
        }
    }

    public Optional<TerritoryImpactReport> lookupCompleted(String territory) {
        return Optional.ofNullable(completedReports.get(territory));
    }

    public boolean readyFor(String key) {
        return status == ImpactCacheStatus.READY && cacheKey.equals(key);
    }

    public static ImpactCacheView empty() {
        return new ImpactCacheView(ImpactCacheStatus.EMPTY, 0, 0, "", 0, 0, 0, 0, Map.of(), "No baseline requested", false);
    }
}
