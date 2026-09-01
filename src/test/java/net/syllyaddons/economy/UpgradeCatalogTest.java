package net.syllyaddons.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.syllyaddons.domain.ResourceType;
import org.junit.jupiter.api.Test;

class UpgradeCatalogTest {
    @Test
    void mapsPinnedWynntilsLevelsToHourlyExpenses() {
        Map<ResourceType, Double> expenses = UpgradeCatalog.expensesPerHour(Map.of(
                "DAMAGE", 2,
                "HEALTH", 1,
                "RESOURCE_RATE", 3));

        assertEquals(300.0, expenses.get(ResourceType.ORE));
        assertEquals(100.0, expenses.get(ResourceType.WOOD));
        assertEquals(32_000.0, expenses.get(ResourceType.EMERALDS));
    }

    @Test
    void productionMultiplierMakesOneLevelLossTraceable() {
        UpgradeDefinition rate = UpgradeCatalog.find("RESOURCE_RATE").orElseThrow();

        assertEquals(8_000, rate.marginalLoss(16_000, 3), 1.0e-9);
        assertEquals(14_000, rate.marginalSavingAt(3));
    }

    @Test
    void ignoresUnknownOrInvalidValuesInsteadOfCrashingHistoricalAnalysis() {
        Map<ResourceType, Double> expenses = UpgradeCatalog.expensesPerHour(Map.of(
                "NOT_FROM_4_2_9", 1,
                "DAMAGE", 99));

        assertEquals(Map.of(), expenses);
    }
}
