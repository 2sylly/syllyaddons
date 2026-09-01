package net.syllyaddons.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
class TerritoryImpactPerformanceTest {
    private static final int REPRESENTATIVE_TERRITORY_COUNT = 405;

    @Test
    void rebuildsEveryTerritoryInRepresentativeFullWorldFixtureWithinBudget() {
        ObservedState state = starState(REPRESENTATIVE_TERRITORY_COUNT);
        TerritoryImpactSimulator simulator = new TerritoryImpactSimulator();

        assertTimeout(Duration.ofSeconds(15), () -> {
            ImpactBaseline baseline = simulator.buildBaseline(state, 2_000);
            var reports = simulator.simulateAll(baseline, () -> false);
            assertEquals(REPRESENTATIVE_TERRITORY_COUNT, reports.size());
        });
    }

    private static ObservedState starState(int count) {
        ArrayList<TerritoryState> territories = new ArrayList<>();
        ArrayList<String> hqLinks = new ArrayList<>();
        for (int index = 1; index < count; index++) hqLinks.add("T" + index);
        territories.add(TerritoryImpactSimulatorTest.territory("HQ", TerritoryImpactSimulatorTest.owner(), hqLinks, 0));
        for (int index = 1; index < count; index++) {
            territories.add(TerritoryImpactSimulatorTest.territory(
                    "T" + index, TerritoryImpactSimulatorTest.owner(), List.of("HQ"), index % 7));
        }
        return TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST, territories.toArray(TerritoryState[]::new));
    }
}
