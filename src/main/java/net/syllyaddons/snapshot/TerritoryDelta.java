package net.syllyaddons.snapshot;

import java.util.Set;

public record TerritoryDelta(String territory, Set<TerritoryChangeKind> changes) {
    public TerritoryDelta {
        changes = Set.copyOf(changes);
    }
}
