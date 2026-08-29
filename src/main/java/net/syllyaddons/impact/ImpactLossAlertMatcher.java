package net.syllyaddons.impact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.domain.ObservedState;

/** Matches losses only against an exactly identified completed pre-loss cache generation. */
public final class ImpactLossAlertMatcher {
    private final OwnershipTransitionDetector detector;
    private final ImpactCacheKeyFactory keyFactory;

    public ImpactLossAlertMatcher() {
        this(new OwnershipTransitionDetector(), new ImpactCacheKeyFactory());
    }

    ImpactLossAlertMatcher(OwnershipTransitionDetector detector, ImpactCacheKeyFactory keyFactory) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory");
    }

    public List<ImpactLossAlert> match(
            ObservedState before,
            ObservedState after,
            ImpactCacheView cache,
            SyllyConfig config,
            long detectedAtEpochMillis) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(config, "config");
        if (!config.territoryImpactEnabled() || !OwnershipTransitionDetector.sameSession(before, after)) {
            return List.of();
        }
        String expectedKey = keyFactory.create(before);
        long baselineAge = cache.builtAtEpochMillis() == 0
                ? 0
                : Math.max(0, detectedAtEpochMillis - cache.builtAtEpochMillis());
        List<ImpactLossAlert> alerts = new ArrayList<>();
        for (TerritoryOwnershipChange change : detector.diff(before, after)) {
            if (!OwnershipTransitionDetector.ownedBy(change.before(), before.guild().value())) continue;
            TerritoryImpactReport report = cache.completedReports().get(change.territory());
            if (report == null || report.sourceRevision() != before.revision()
                    || !report.cacheKey().equals(expectedKey)) continue;
            ImpactSeverity severity = report.maximumSeverity();
            if (severity.ordinal() < config.impactAlertMinimumSeverity().ordinal()) continue;
            long beforeOwnerObservedAt = before.territories().get(change.territory())
                    .owner().evidence().observedAtEpochMillis();
            long afterOwnerObservedAt = after.territories().get(change.territory())
                    .owner().evidence().observedAtEpochMillis();
            long refreshWindow = Math.max(0, afterOwnerObservedAt - beforeOwnerObservedAt);
            alerts.add(new ImpactLossAlert(
                    change.territory(),
                    change.after().guildName(),
                    severity,
                    detectedAtEpochMillis,
                    detectedAtEpochMillis + config.impactAlertDurationSeconds() * 1_000L,
                    baselineAge,
                    refreshWindow,
                    before.revision(),
                    report));
        }
        return List.copyOf(alerts);
    }
}
