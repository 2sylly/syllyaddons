package net.syllyaddons.impact;

import net.syllyaddons.domain.ObservedState;

public interface TerritoryImpactComputer {
    ImpactBaseline buildBaseline(ObservedState state, long nowEpochMillis);

    TerritoryImpactReport simulate(ImpactBaseline baseline, String removedTerritory);
}
