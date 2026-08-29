package net.syllyaddons.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.EconomyEngine;
import org.junit.jupiter.api.Test;

class OptimizationModelTest {
    @Test
    void projectsQuantifiedProductionAndKeepsTowerConfigurationFixed() {
        OptimizationModel model = OptimizerTestFixtures.oneVariable();
        UpgradeCoordinate economyUpgrade = new UpgradeCoordinate("HQ", "EFFICIENT_RESOURCES");

        var candidateInput = model.project(Map.of(economyUpgrade, 1));
        var result = new EconomyEngine().calculate(candidateInput);

        assertEquals(150.0, candidateInput.territories().getFirst()
                .productionPerHour().get(ResourceType.ORE));
        assertEquals(6_000.0, candidateInput.territories().getFirst()
                .expensesPerHour().get(ResourceType.EMERALDS));
        assertEquals(2_400.0, candidateInput.territories().getFirst()
                .expensesPerHour().get(ResourceType.ORE));
        assertEquals(2_400.0, result.summaries().get(ResourceType.ORE).expenses());
        assertTrue(model.changes(Map.of(economyUpgrade, 1)).stream()
                .noneMatch(change -> change.coordinate().upgradeKey().equals("DAMAGE")));
    }

    @Test
    void currentAssignmentAlwaysRepresentsTheNoChangeBaseline() {
        OptimizationModel model = OptimizerTestFixtures.oneVariable();

        assertEquals(Map.of(new UpgradeCoordinate("HQ", "EFFICIENT_RESOURCES"), 2), model.currentAssignment());
        assertTrue(model.changes(model.currentAssignment()).isEmpty());
    }
}
