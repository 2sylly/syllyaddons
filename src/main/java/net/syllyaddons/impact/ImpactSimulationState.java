package net.syllyaddons.impact;

import java.util.Objects;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.routing.TerritoryGraph;

public record ImpactSimulationState(
        ObservedState baselineObservation,
        ObservedState simulatedObservation,
        TerritoryGraph baselineGraph,
        TerritoryGraph simulatedGraph,
        String removedTerritory) {
    public ImpactSimulationState {
        baselineObservation = Objects.requireNonNull(baselineObservation, "baselineObservation");
        simulatedObservation = Objects.requireNonNull(simulatedObservation, "simulatedObservation");
        baselineGraph = Objects.requireNonNull(baselineGraph, "baselineGraph");
        simulatedGraph = Objects.requireNonNull(simulatedGraph, "simulatedGraph");
        removedTerritory = Objects.requireNonNull(removedTerritory, "removedTerritory").strip();
        if (removedTerritory.isEmpty()
                || baselineGraph.node(removedTerritory) == null
                || simulatedGraph.node(removedTerritory) != null) {
            throw new IllegalArgumentException("Simulation must remove exactly the named baseline territory");
        }
    }
}
