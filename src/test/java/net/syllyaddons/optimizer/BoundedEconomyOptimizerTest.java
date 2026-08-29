package net.syllyaddons.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.EconomyEngine;
import org.junit.jupiter.api.Test;

class BoundedEconomyOptimizerTest {
    private final BoundedEconomyOptimizer optimizer = new BoundedEconomyOptimizer();

    @Test
    void tinySearchMatchesItsEnumerableOptimumAndRevalidates() {
        OptimizationModel model = OptimizerTestFixtures.oneVariable();
        OptimizationRequest request = request(Map.of(ResourceType.ORE, 120.0), 100, 10_000);

        OptimizationResult result = optimizer.optimize(model, request);

        OptimizationCandidate recommendation = result.recommendation().orElseThrow();
        UpgradeCoordinate coordinate = new UpgradeCoordinate("HQ", "EFFICIENT_RESOURCES");
        assertEquals(bruteForceBestLevel(model, coordinate, 120.0), recommendation.levels().get(coordinate));
        assertEquals(OptimizationTermination.EXHAUSTIVE, result.termination());
        assertTrue(result.optimalityProven());
        assertTrue(result.independentlyVerified());
        assertEquals(4, result.evaluatedNodes());
        assertEquals(2, result.baseline().levels()
                .get(new UpgradeCoordinate("HQ", "EFFICIENT_RESOURCES")));
    }

    @Test
    void impossibleReserveProducesExplanationInsteadOfEmptySuccess() {
        OptimizationRequest request = request(Map.of(ResourceType.ORE, 1_000.0), 100, 10_000);

        OptimizationResult result = optimizer.optimize(OptimizerTestFixtures.oneVariable(), request);

        assertTrue(result.recommendation().isEmpty());
        assertTrue(result.bestEffort().isPresent());
        assertFalse(result.independentlyVerified());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("No candidate satisfies")));
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("ORE reserve")));
    }

    @Test
    void returnsBestKnownVerifiedCandidateAtNodeLimit() {
        OptimizationRequest request = request(Map.of(), 100, 10_000);

        OptimizationResult result = optimizer.optimize(OptimizerTestFixtures.manyVariables(), request);

        assertEquals(OptimizationTermination.NODE_LIMIT, result.termination());
        assertEquals(100, result.evaluatedNodes());
        assertFalse(result.optimalityProven());
        assertTrue(result.recommendation().isPresent());
        assertTrue(result.independentlyVerified());
    }

    @Test
    void wallClockLimitStopsWithoutDiscardingTheBaseline() {
        OptimizationRequest request = request(Map.of(), 1_000, 50);
        AtomicInteger calls = new AtomicInteger();

        OptimizationResult result = optimizer.optimize(
                OptimizerTestFixtures.manyVariables(),
                request,
                () -> calls.getAndIncrement() == 0 ? 0 : 60_000_000L);

        assertEquals(OptimizationTermination.TIME_LIMIT, result.termination());
        assertEquals(1, result.evaluatedNodes());
        assertTrue(result.baseline().levels().size() == 6);
        assertTrue(result.recommendation().isPresent());
    }

    @Test
    void everyObjectiveKeepsAFeasibleBaselineAvailable() {
        for (OptimizationObjective objective : OptimizationObjective.values()) {
            OptimizationRequest request = new OptimizationRequest(objective, Map.of(), false, 100, 10_000);
            OptimizationResult result = optimizer.optimize(OptimizerTestFixtures.oneVariable(), request);
            assertTrue(result.recommendation().isPresent(), objective.name());
            assertTrue(result.independentlyVerified(), objective.name());
        }
    }

    private static OptimizationRequest request(
            Map<ResourceType, Double> reserves, long nodeLimit, long timeLimitMillis) {
        return new OptimizationRequest(
                OptimizationObjective.MINIMUM_EXPENSE,
                reserves,
                true,
                nodeLimit,
                timeLimitMillis);
    }

    private static int bruteForceBestLevel(
            OptimizationModel model, UpgradeCoordinate coordinate, double oreReserve) {
        EconomyEngine engine = new EconomyEngine();
        int bestLevel = -1;
        double bestExpense = Double.POSITIVE_INFINITY;
        for (int level = 0; level <= 2; level++) {
            var economy = engine.calculate(model.project(Map.of(coordinate, level)));
            double deficit = economy.summaries().values().stream().mapToDouble(value -> value.deficit()).sum();
            double ore = economy.summaries().get(ResourceType.ORE).endingStorage();
            double expense = economy.summaries().values().stream().mapToDouble(value -> value.expenses()).sum();
            if (deficit <= 1.0e-6 && ore >= oreReserve && expense < bestExpense) {
                bestExpense = expense;
                bestLevel = level;
            }
        }
        return bestLevel;
    }
}
