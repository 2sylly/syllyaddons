package net.syllyaddons.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.syllyaddons.config.ImpactOverlayScope;
import net.syllyaddons.config.ImpactResourceFilter;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryOwner;
import org.junit.jupiter.api.Test;

class ImpactOverlayFilterTest {
    private final TerritoryImpactSimulator simulator = new TerritoryImpactSimulator();
    private final ImpactOverlayFilter filter = new ImpactOverlayFilter();

    @Test
    void ownGuildDisconnectionResourceAndDelayFiltersCompose() {
        ObservedState state = TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory(
                        "Source", TerritoryImpactSimulatorTest.owner(), List.of("Middle"), 10),
                TerritoryImpactSimulatorTest.territory(
                        "Middle", TerritoryImpactSimulatorTest.owner(), List.of("Source", "HQ"), 0),
                TerritoryImpactSimulatorTest.territory(
                        "HQ", TerritoryImpactSimulatorTest.owner(), List.of("Middle"), 0));
        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(state, 2_000), "Middle");
        SyllyConfig config = SyllyConfig.defaults()
                .withImpactDisconnectionsOnly(true)
                .withImpactResourceFilter(ImpactResourceFilter.ORE);

        assertTrue(filter.matches(report, state, config));
        assertFalse(filter.matches(report, state, config.withImpactMinimumDelaySeconds(3_600)));
        assertFalse(filter.matches(report, state, config.withImpactResourceFilter(ImpactResourceFilter.FISH)));
    }

    @Test
    void selectedEnemyAndVisibleGuildScopesUseObservedOwnerIdentity() {
        TerritoryOwner enemy = new TerritoryOwner("enemy-id", "Enemy Guild", "ENY");
        ObservedState state = TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory("Enemy", enemy, List.of("HQ"), 0),
                TerritoryImpactSimulatorTest.territory(
                        "HQ", TerritoryImpactSimulatorTest.owner(), List.of("Enemy"), 0));
        TerritoryImpactReport report = simulator.simulate(simulator.buildBaseline(state, 2_000), "Enemy");
        SyllyConfig selected = SyllyConfig.defaults()
                .withImpactOverlayScope(ImpactOverlayScope.SELECTED_ENEMY)
                .withImpactSelectedEnemy("eny");

        assertTrue(filter.matches(report, state, selected));
        assertFalse(filter.matches(report, state, selected.withImpactSelectedEnemy("someone else")));
        assertTrue(filter.matches(report, state,
                selected.withImpactOverlayScope(ImpactOverlayScope.VISIBLE_GUILDS)));
    }

    @Test
    void paletteHasNeutralGreyAndAllFourSeverityColours() {
        ImpactScore zero = new ImpactScore(0, ImpactSeverity.MINOR, ImpactCertainty.ESTIMATED, List.of(), List.of());
        RoutingModeImpact unchanged = new RoutingModeImpact(
                RoutingMode.FASTEST, List.of(), Map.of(), ImpactCertainty.EXACT_OBSERVATION,
                ImpactCertainty.ESTIMATED, ImpactCertainty.ESTIMATED, zero, zero, List.of());
        TerritoryImpactReport neutral = new TerritoryImpactReport(
                "Neutral", OwnerRelation.UNKNOWN, false, 1, "key", Map.of(RoutingMode.FASTEST, unchanged), List.of());
        assertEquals(ImpactVisualSeverity.NONE, ImpactVisualSeverity.forReport(neutral));

        for (ImpactSeverity severity : ImpactSeverity.values()) {
            assertEquals(severity.name(), ImpactVisualSeverity.valueOf(severity.name()).name());
        }
    }
}
