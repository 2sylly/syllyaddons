package net.syllyaddons.impact;

import java.util.Objects;
import net.syllyaddons.domain.TerritoryOwner;

public record TerritoryOwnershipChange(
        String territory,
        TerritoryOwner before,
        TerritoryOwner after,
        long beforeRevision,
        long afterRevision) {
    public TerritoryOwnershipChange {
        territory = Objects.requireNonNull(territory, "territory").strip();
        before = Objects.requireNonNull(before, "before");
        after = Objects.requireNonNull(after, "after");
        if (territory.isEmpty() || beforeRevision < 0 || afterRevision <= beforeRevision) {
            throw new IllegalArgumentException("Invalid ownership change");
        }
    }
}
