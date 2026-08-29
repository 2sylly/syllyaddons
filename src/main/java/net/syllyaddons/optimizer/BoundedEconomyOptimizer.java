package net.syllyaddons.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.EconomyEngine;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.economy.ResourceEconomySummary;

/** Deterministic depth-first integer search with hard node and wall-clock limits. */
public final class BoundedEconomyOptimizer {
    private static final double EPSILON = 1.0e-6;

    public OptimizationResult optimize(OptimizationModel model, OptimizationRequest request) {
        return optimize(model, request, System::nanoTime);
    }

    OptimizationResult optimize(
            OptimizationModel model,
            OptimizationRequest request,
            java.util.function.LongSupplier nanoTime) {
        java.util.Objects.requireNonNull(model, "model");
        java.util.Objects.requireNonNull(request, "request");
        java.util.Objects.requireNonNull(nanoTime, "nanoTime");
        long started = nanoTime.getAsLong();
        long deadline = saturatingAdd(started, request.timeLimitMillis() * 1_000_000L);
        Search search = new Search(model, request, nanoTime, deadline);

        OptimizationCandidate baseline = search.evaluate(model.currentAssignment());
        search.baseline = baseline;
        search.consider(baseline);
        if (model.variables().isEmpty()) {
            Optional<OptimizationCandidate> recommendation = baseline.feasible()
                    ? Optional.of(baseline)
                    : Optional.empty();
            return finish(
                    model, request, baseline, recommendation, Optional.of(baseline),
                    true, search.nodes, elapsedMillis(started, nanoTime.getAsLong()),
                    OptimizationTermination.NO_VARIABLES, List.of("No quantified economic upgrade can be lowered."));
        }

        search.walk(0, new LinkedHashMap<>());
        OptimizationTermination termination = search.timeLimitReached
                ? OptimizationTermination.TIME_LIMIT
                : search.nodeLimitReached ? OptimizationTermination.NODE_LIMIT : OptimizationTermination.EXHAUSTIVE;
        boolean optimalityProven = termination == OptimizationTermination.EXHAUSTIVE;
        Optional<OptimizationCandidate> recommendation = Optional.ofNullable(search.bestFeasible);
        Optional<OptimizationCandidate> bestEffort = Optional.ofNullable(search.bestEffort);
        List<String> diagnostics = new ArrayList<>();
        if (recommendation.isEmpty()) {
            diagnostics.add("No candidate satisfies every configured reserve/deficit constraint.");
            if (search.bestEffort != null) diagnostics.addAll(search.bestEffort.violations());
        }
        if (!optimalityProven) {
            diagnostics.add("Search stopped at the " + termination.name().toLowerCase().replace('_', ' ')
                    + "; the best known feasible candidate is shown.");
        }
        return finish(
                model, request, baseline, recommendation, bestEffort,
                optimalityProven, search.nodes, elapsedMillis(started, nanoTime.getAsLong()), termination, diagnostics);
    }

    private static OptimizationResult finish(
            OptimizationModel model,
            OptimizationRequest request,
            OptimizationCandidate baseline,
            Optional<OptimizationCandidate> recommendation,
            Optional<OptimizationCandidate> bestEffort,
            boolean optimalityProven,
            long nodes,
            long elapsedMillis,
            OptimizationTermination termination,
            List<String> diagnostics) {
        boolean verified = false;
        Optional<OptimizationCandidate> safeRecommendation = recommendation;
        List<String> combinedDiagnostics = new ArrayList<>(diagnostics);
        if (recommendation.isPresent()) {
            OptimizationCandidate expected = recommendation.get();
            OptimizationCandidate rerun = evaluate(
                    model, request, expected.levels(), new EconomyEngine());
            verified = equivalent(expected, rerun);
            if (!verified) {
                safeRecommendation = Optional.empty();
                combinedDiagnostics.add("Independent economy-engine revalidation disagreed; recommendation withheld.");
            }
        }
        return new OptimizationResult(
                baseline,
                safeRecommendation,
                bestEffort,
                optimalityProven,
                verified,
                nodes,
                elapsedMillis,
                termination,
                combinedDiagnostics);
    }

    private static OptimizationCandidate evaluate(
            OptimizationModel model,
            OptimizationRequest request,
            Map<UpgradeCoordinate, Integer> levels,
            EconomyEngine engine) {
        EconomyResult economy = engine.calculate(model.project(levels));
        OptimizationMetrics metrics = metrics(economy, request.minimumReserves());
        List<String> violations = violations(economy, metrics, request);
        return new OptimizationCandidate(
                levels,
                economy,
                metrics,
                model.changes(levels),
                violations.isEmpty(),
                violations);
    }

    private static OptimizationMetrics metrics(
            EconomyResult economy,
            Map<ResourceType, Double> reserves) {
        double expense = 0;
        double deficit = 0;
        double minimumBuffer = Double.POSITIVE_INFINITY;
        double minimumHeadroom = Double.POSITIVE_INFINITY;
        double stored = 0;
        double shortfall = 0;
        for (ResourceType resource : ResourceType.values()) {
            ResourceEconomySummary summary = economy.summaries().get(resource);
            double ending = summary == null ? 0 : summary.endingStorage();
            double resourceExpense = summary == null ? 0 : summary.expenses();
            double resourceDeficit = summary == null ? 0 : summary.deficit();
            double floor = reserves.getOrDefault(resource, 0.0);
            expense += resourceExpense;
            deficit += resourceDeficit;
            stored += ending;
            minimumBuffer = Math.min(minimumBuffer, ending);
            minimumHeadroom = Math.min(minimumHeadroom, ending - floor);
            shortfall += Math.max(0, floor - ending);
        }
        if (minimumBuffer == Double.POSITIVE_INFINITY) minimumBuffer = 0;
        if (minimumHeadroom == Double.POSITIVE_INFINITY) minimumHeadroom = 0;
        return new OptimizationMetrics(expense, deficit, minimumBuffer, minimumHeadroom, stored, shortfall);
    }

    private static List<String> violations(
            EconomyResult economy,
            OptimizationMetrics metrics,
            OptimizationRequest request) {
        List<String> violations = new ArrayList<>();
        if (economy.summaries().isEmpty()) violations.add("The normal economy engine did not produce resource totals.");
        if (request.requireNoDeficits() && metrics.totalDeficitPerHour() > EPSILON) {
            violations.add(format(metrics.totalDeficitPerHour()) + " total resources/hour remain unfunded.");
        }
        for (ResourceType resource : ResourceType.values()) {
            double required = request.minimumReserves().getOrDefault(resource, 0.0);
            double actual = Optional.ofNullable(economy.summaries().get(resource))
                    .map(ResourceEconomySummary::endingStorage)
                    .orElse(0.0);
            if (actual + EPSILON < required) {
                violations.add(resource + " reserve is " + format(required - actual) + " below its floor.");
            }
        }
        return List.copyOf(violations);
    }

    private static boolean equivalent(OptimizationCandidate expected, OptimizationCandidate rerun) {
        return expected.levels().equals(rerun.levels())
                && expected.feasible() == rerun.feasible()
                && close(expected.metrics().totalExpensePerHour(), rerun.metrics().totalExpensePerHour())
                && close(expected.metrics().totalDeficitPerHour(), rerun.metrics().totalDeficitPerHour())
                && close(expected.metrics().minimumEndingBuffer(), rerun.metrics().minimumEndingBuffer())
                && close(expected.metrics().totalEndingStorage(), rerun.metrics().totalEndingStorage());
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static Comparator<OptimizationCandidate> comparator(OptimizationObjective objective) {
        Comparator<OptimizationCandidate> order = switch (objective) {
            case MINIMUM_EXPENSE -> Comparator
                    .comparingDouble((OptimizationCandidate value) -> value.metrics().totalExpensePerHour())
                    .thenComparingDouble(value -> value.metrics().totalDeficitPerHour())
                    .thenComparing(Comparator.comparingDouble(
                            (OptimizationCandidate value) -> value.metrics().minimumEndingBuffer()).reversed());
            case REPAIR_DEFICITS -> Comparator
                    .comparingDouble((OptimizationCandidate value) -> value.metrics().totalDeficitPerHour())
                    .thenComparingDouble(value -> value.metrics().totalExpensePerHour())
                    .thenComparing(Comparator.comparingDouble(
                            (OptimizationCandidate value) -> value.metrics().minimumEndingBuffer()).reversed());
            case MAXIMUM_MINIMUM_BUFFER -> Comparator
                    .comparingDouble((OptimizationCandidate value) -> -value.metrics().minimumEndingBuffer())
                    .thenComparingDouble(value -> value.metrics().totalDeficitPerHour())
                    .thenComparingDouble(value -> value.metrics().totalExpensePerHour());
            case PRESERVE_RESERVES -> Comparator
                    .comparingDouble((OptimizationCandidate value) -> -value.metrics().minimumReserveHeadroom())
                    .thenComparingDouble(value -> value.metrics().totalExpensePerHour())
                    .thenComparingDouble(value -> value.metrics().totalDeficitPerHour());
        };
        return order
                .thenComparingInt(value -> value.changes().size())
                .thenComparing(BoundedEconomyOptimizer::signature);
    }

    private static Comparator<OptimizationCandidate> bestEffortComparator(OptimizationObjective objective) {
        return Comparator
                .comparingDouble((OptimizationCandidate value) -> value.metrics().totalReserveShortfall())
                .thenComparingDouble(value -> value.metrics().totalDeficitPerHour())
                .thenComparing(comparator(objective));
    }

    private static String signature(OptimizationCandidate candidate) {
        return candidate.levels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().territory() + "/" + entry.getKey().upgradeKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static long elapsedMillis(long started, long ended) {
        return Math.max(0, ended - started) / 1_000_000L;
    }

    private static long saturatingAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static final class Search {
        private final OptimizationModel model;
        private final OptimizationRequest request;
        private final java.util.function.LongSupplier nanoTime;
        private final long deadline;
        private final EconomyEngine engine = new EconomyEngine();
        private final Comparator<OptimizationCandidate> candidateOrder;
        private final Comparator<OptimizationCandidate> effortOrder;
        private long nodes;
        private boolean nodeLimitReached;
        private boolean timeLimitReached;
        private OptimizationCandidate baseline;
        private OptimizationCandidate bestFeasible;
        private OptimizationCandidate bestEffort;

        private Search(
                OptimizationModel model,
                OptimizationRequest request,
                java.util.function.LongSupplier nanoTime,
                long deadline) {
            this.model = model;
            this.request = request;
            this.nanoTime = nanoTime;
            this.deadline = deadline;
            candidateOrder = comparator(request.objective());
            effortOrder = bestEffortComparator(request.objective());
        }

        private void walk(int variableIndex, LinkedHashMap<UpgradeCoordinate, Integer> partial) {
            if (stopped() || variableIndex >= model.variables().size()) return;
            UpgradeVariable variable = model.variables().get(variableIndex);
            for (int level = variable.minimumLevel(); level <= variable.currentLevel(); level++) {
                if (stopped()) return;
                partial.put(variable.coordinate(), level);
                OptimizationCandidate candidate = evaluate(partial);
                consider(candidate);
                walk(variableIndex + 1, partial);
                partial.remove(variable.coordinate());
            }
        }

        private OptimizationCandidate evaluate(Map<UpgradeCoordinate, Integer> levels) {
            nodes++;
            return BoundedEconomyOptimizer.evaluate(model, request, Map.copyOf(levels), engine);
        }

        private void consider(OptimizationCandidate candidate) {
            if (candidate.feasible()
                    && (bestFeasible == null || candidateOrder.compare(candidate, bestFeasible) < 0)) {
                bestFeasible = candidate;
            }
            if (bestEffort == null || effortOrder.compare(candidate, bestEffort) < 0) bestEffort = candidate;
        }

        private boolean stopped() {
            if (nodes >= request.nodeLimit()) {
                nodeLimitReached = true;
                return true;
            }
            if (nanoTime.getAsLong() >= deadline) {
                timeLimitReached = true;
                return true;
            }
            return false;
        }
    }
}
