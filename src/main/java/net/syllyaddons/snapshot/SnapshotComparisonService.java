package net.syllyaddons.snapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.ResourceEconomySummary;

public final class SnapshotComparisonService {
    public SnapshotComparison compare(SnapshotPayload baseline, SnapshotPayload current) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(current, "current");
        EcoSnapshot before = baseline.observed();
        EcoSnapshot after = current.observed();
        List<TerritoryDelta> territoryDeltas = territoryDeltas(before, after);
        Map<ResourceType, ResourceTotalDelta> resourceDeltas = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            ResourceTotals beforeTotals = totals(before, resource);
            ResourceTotals afterTotals = totals(after, resource);
            resourceDeltas.put(resource, new ResourceTotalDelta(
                    resource,
                    beforeTotals,
                    afterTotals,
                    afterTotals.generationPerHour() - beforeTotals.generationPerHour(),
                    afterTotals.stored() - beforeTotals.stored(),
                    afterTotals.storageLimit() - beforeTotals.storageLimit()));
        }

        boolean economyComparable = baseline.economy() != null && current.economy() != null;
        Map<ResourceType, EconomyResourceDelta> economyDeltas = new EnumMap<>(ResourceType.class);
        if (economyComparable) {
            for (ResourceType resource : ResourceType.values()) {
                ResourceEconomySummary beforeSummary = baseline.economy().summaries().get(resource);
                ResourceEconomySummary afterSummary = current.economy().summaries().get(resource);
                if (beforeSummary == null || afterSummary == null) continue;
                economyDeltas.put(resource, new EconomyResourceDelta(
                        resource,
                        afterSummary.deliveredProduction() - beforeSummary.deliveredProduction(),
                        afterSummary.taxLoss() - beforeSummary.taxLoss(),
                        afterSummary.expenses() - beforeSummary.expenses(),
                        afterSummary.deficit() - beforeSummary.deficit(),
                        afterSummary.endingStorage() - beforeSummary.endingStorage()));
            }
        }
        return new SnapshotComparison(
                before.sourceRevision(),
                after.sourceRevision(),
                knownString(before.hqTerritory()),
                knownString(after.hqTerritory()),
                territoryDeltas,
                resourceDeltas,
                economyDeltas,
                economyComparable);
    }

    private static List<TerritoryDelta> territoryDeltas(EcoSnapshot before, EcoSnapshot after) {
        Set<String> names = new TreeSet<>();
        names.addAll(before.territories().keySet());
        names.addAll(after.territories().keySet());
        List<TerritoryDelta> result = new ArrayList<>();
        for (String name : names) {
            TerritoryState left = before.territories().get(name);
            TerritoryState right = after.territories().get(name);
            EnumSet<TerritoryChangeKind> changes = EnumSet.noneOf(TerritoryChangeKind.class);
            if (left == null) {
                changes.add(TerritoryChangeKind.ADDED);
            } else if (right == null) {
                changes.add(TerritoryChangeKind.REMOVED);
            } else {
                addIfDifferent(changes, TerritoryChangeKind.OWNER, left.owner(), right.owner());
                addIfDifferent(changes, TerritoryChangeKind.ACQUIRED_AT, left.acquiredAtEpochMillis(), right.acquiredAtEpochMillis());
                addIfDifferent(changes, TerritoryChangeKind.HEADQUARTERS, left.headquarters(), right.headquarters());
                addIfDifferent(changes, TerritoryChangeKind.BOUNDS, left.bounds(), right.bounds());
                addIfDifferent(changes, TerritoryChangeKind.LINKS, left.links(), right.links());
                addIfDifferent(changes, TerritoryChangeKind.RESOURCES, left.resources(), right.resources());
                addIfDifferent(changes, TerritoryChangeKind.TREASURY, left.treasury(), right.treasury());
                addIfDifferent(
                        changes,
                        TerritoryChangeKind.TREASURY_BONUS,
                        left.treasuryBonusPercent(),
                        right.treasuryBonusPercent());
                addIfDifferent(changes, TerritoryChangeKind.DEFENCES, left.defences(), right.defences());
                addIfDifferent(changes, TerritoryChangeKind.UPGRADES, left.upgrades(), right.upgrades());
                addIfDifferent(changes, TerritoryChangeKind.ALERTS, left.alerts(), right.alerts());
            }
            if (!changes.isEmpty()) result.add(new TerritoryDelta(name, changes));
        }
        return result;
    }

    private static void addIfDifferent(
            Set<TerritoryChangeKind> changes,
            TerritoryChangeKind kind,
            ObservedValue<?> left,
            ObservedValue<?> right) {
        Object leftValue = left != null && left.isKnown() ? left.value() : null;
        Object rightValue = right != null && right.isKnown() ? right.value() : null;
        if (!Objects.equals(leftValue, rightValue)) changes.add(kind);
    }

    private static ResourceTotals totals(EcoSnapshot snapshot, ResourceType resource) {
        double generation = 0;
        double stored = 0;
        double limit = 0;
        int missing = 0;
        for (TerritoryState territory : snapshot.territories().values()) {
            if (!territory.resources().isKnown()) {
                missing++;
                continue;
            }
            ResourceBalance balance = territory.resources().value().get(resource);
            if (balance == null) continue;
            generation += balance.generationPerHour();
            stored += balance.stored();
            limit += balance.storageLimit();
        }
        return new ResourceTotals(resource, generation, stored, limit, missing);
    }

    private static String knownString(ObservedValue<String> value) {
        return value != null && value.isKnown() ? value.value() : "Unknown";
    }
}
