package net.syllyaddons.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import org.junit.jupiter.api.Test;

class ImpactCacheKeyFactoryTest {
    private final ImpactCacheKeyFactory factory = new ImpactCacheKeyFactory();

    @Test
    void ignoresRevisionButChangesForRelevantProductionInputs() {
        ObservedState first = TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory("Source", TerritoryImpactSimulatorTest.owner(), List.of("HQ"), 10),
                TerritoryImpactSimulatorTest.territory("HQ", TerritoryImpactSimulatorTest.owner(), List.of("Source"), 0));
        ObservedState sameInputsNewRevision = new ObservedState(
                first.schemaVersion(),
                99,
                9_999,
                first.character(),
                first.guild(),
                first.hqTerritory(),
                first.routingMode(),
                first.territories());
        ObservedState changedProduction = TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory("Source", TerritoryImpactSimulatorTest.owner(), List.of("HQ"), 11),
                TerritoryImpactSimulatorTest.territory("HQ", TerritoryImpactSimulatorTest.owner(), List.of("Source"), 0));

        assertEquals(factory.create(first), factory.create(sameInputsNewRevision));
        assertNotEquals(factory.create(first), factory.create(changedProduction));
    }
}
