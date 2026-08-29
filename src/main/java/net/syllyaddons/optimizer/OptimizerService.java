package net.syllyaddons.optimizer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.syllyaddons.config.OptimizerConfig;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.observation.ObservedStateRepository;

/** Runs bounded searches off-thread and generation-checks results against the observed source revision. */
public final class OptimizerService {
    private final ObservedStateRepository repository;
    private final ObservedOptimizationModelFactory modelFactory;
    private final BoundedEconomyOptimizer optimizer;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<OptimizerView> view = new AtomicReference<>(OptimizerView.idle());

    public OptimizerService(ObservedStateRepository repository) {
        this(repository, new ObservedOptimizationModelFactory(), new BoundedEconomyOptimizer());
    }

    OptimizerService(
            ObservedStateRepository repository,
            ObservedOptimizationModelFactory modelFactory,
            BoundedEconomyOptimizer optimizer) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.modelFactory = java.util.Objects.requireNonNull(modelFactory, "modelFactory");
        this.optimizer = java.util.Objects.requireNonNull(optimizer, "optimizer");
    }

    public void request(OptimizerConfig config) {
        java.util.Objects.requireNonNull(config, "config");
        ObservedState source = repository.snapshot();
        long token = generation.incrementAndGet();
        long started = System.currentTimeMillis();
        view.set(new OptimizerView(
                OptimizerRunStatus.RUNNING, source.revision(), started, 0, java.util.Optional.empty(), List.of()));
        CompletableFuture.runAsync(() -> run(token, source, config, started));
    }

    public OptimizerView view() {
        OptimizerView current = view.get();
        if ((current.status() == OptimizerRunStatus.COMPLETE || current.status() == OptimizerRunStatus.UNAVAILABLE)
                && repository.snapshot().revision() != current.sourceRevision()) {
            return new OptimizerView(
                    OptimizerRunStatus.STALE,
                    current.sourceRevision(),
                    current.startedAtEpochMillis(),
                    current.completedAtEpochMillis(),
                    current.result(),
                    append(current.diagnostics(), "Observed state changed; run the optimizer again."));
        }
        return current;
    }

    private void run(long token, ObservedState source, OptimizerConfig config, long started) {
        try {
            OptimizationModelBuild built = modelFactory.build(source, started);
            if (generation.get() != token) return;
            if (built.model().isEmpty()) {
                view.set(new OptimizerView(
                        OptimizerRunStatus.UNAVAILABLE,
                        source.revision(),
                        started,
                        System.currentTimeMillis(),
                        java.util.Optional.empty(),
                        built.diagnostics()));
                return;
            }
            OptimizationModel model = built.model().orElseThrow();
            OptimizationRequest request = new OptimizationRequest(
                    config.objective(),
                    reserveFloors(model, config.reserveFloorPercent()),
                    config.requireNoDeficits(),
                    config.nodeLimit(),
                    config.timeLimitMillis());
            OptimizationResult result = optimizer.optimize(model, request);
            if (generation.get() != token) return;
            OptimizerRunStatus status = repository.snapshot().revision() == source.revision()
                    ? OptimizerRunStatus.COMPLETE
                    : OptimizerRunStatus.STALE;
            view.set(new OptimizerView(
                    status,
                    source.revision(),
                    started,
                    System.currentTimeMillis(),
                    java.util.Optional.of(result),
                    built.diagnostics()));
        } catch (RuntimeException exception) {
            if (generation.get() != token) return;
            view.set(new OptimizerView(
                    OptimizerRunStatus.FAILED,
                    source.revision(),
                    started,
                    System.currentTimeMillis(),
                    java.util.Optional.empty(),
                    List.of("Optimizer failed safely: " + rootMessage(exception))));
        }
    }

    static Map<ResourceType, Double> reserveFloors(OptimizationModel model, int percent) {
        EnumMap<ResourceType, Double> floors = new EnumMap<>(ResourceType.class);
        model.baselineInput().openingHqStorage().forEach(
                (resource, amount) -> floors.put(resource, amount * percent / 100.0));
        for (ResourceType resource : ResourceType.values()) floors.putIfAbsent(resource, 0.0);
        return Map.copyOf(floors);
    }

    private static List<String> append(List<String> values, String value) {
        java.util.ArrayList<String> copy = new java.util.ArrayList<>(values);
        if (!copy.contains(value)) copy.add(value);
        return List.copyOf(copy);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
