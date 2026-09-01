package net.syllyaddons.impact;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.syllyaddons.domain.ObservedState;

/** Generation-safe asynchronous precomputation cache. Render callers only read immutable completed maps. */
public final class TerritoryImpactCache implements AutoCloseable {
    private final TerritoryImpactComputer computer;
    private final ImpactCacheKeyFactory keyFactory;
    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<ImpactCacheView> view = new AtomicReference<>(ImpactCacheView.empty());
    private final Map<Long, AtomicInteger> progress = new ConcurrentHashMap<>();
    private volatile Future<?> active;
    private volatile boolean closed;

    public TerritoryImpactCache() {
        this(
                new TerritoryImpactSimulator(),
                new ImpactCacheKeyFactory(),
                Executors.newFixedThreadPool(2, runnable -> {
                    Thread thread = new Thread(runnable, "syllyaddons-impact-cache");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    TerritoryImpactCache(
            TerritoryImpactComputer computer, ImpactCacheKeyFactory keyFactory, ExecutorService executor) {
        this.computer = Objects.requireNonNull(computer, "computer");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public long request(ObservedState state, long nowEpochMillis) {
        return request(state, nowEpochMillis, false);
    }

    public synchronized long request(ObservedState state, long nowEpochMillis, boolean force) {
        Objects.requireNonNull(state, "state");
        if (closed) throw new IllegalStateException("Territory impact cache is closed");
        String key = keyFactory.create(state);
        ImpactCacheView current = view.get();
        if (!force && key.equals(current.cacheKey())
                && (current.status() == ImpactCacheStatus.BUILDING
                        || current.status() == ImpactCacheStatus.READY
                        || current.status() == ImpactCacheStatus.UNAVAILABLE)) {
            return current.generation();
        }

        long nextGeneration = generation.incrementAndGet();
        Future<?> previous = active;
        if (previous != null) previous.cancel(true);
        Map<String, TerritoryImpactReport> previousReports = current.completedReports();
        AtomicInteger completed = new AtomicInteger();
        progress.clear();
        progress.put(nextGeneration, completed);
        view.set(new ImpactCacheView(
                ImpactCacheStatus.BUILDING,
                nextGeneration,
                state.revision(),
                key,
                0,
                state.territories().size(),
                current.builtAtEpochMillis(),
                current.buildDurationMillis(),
                previousReports,
                "Building removal impacts off-thread",
                !previousReports.isEmpty()));
        active = CompletableFuture.runAsync(
                () -> build(state, nowEpochMillis, key, nextGeneration, completed, previousReports), executor);
        return nextGeneration;
    }

    public ImpactCacheView view() {
        ImpactCacheView current = view.get();
        if (current.status() != ImpactCacheStatus.BUILDING) return current;
        AtomicInteger counter = progress.get(current.generation());
        int completed = counter == null ? current.completedTargets() : counter.get();
        if (completed == current.completedTargets()) return current;
        return new ImpactCacheView(
                current.status(),
                current.generation(),
                current.requestedRevision(),
                current.cacheKey(),
                completed,
                current.totalTargets(),
                current.builtAtEpochMillis(),
                current.buildDurationMillis(),
                current.completedReports(),
                current.message(),
                current.reportsAreStale());
    }

    public java.util.Optional<TerritoryImpactReport> lookupCompleted(String territory) {
        return view.get().lookupCompleted(territory);
    }

    private void build(
            ObservedState state,
            long nowEpochMillis,
            String key,
            long requestedGeneration,
            AtomicInteger completed,
            Map<String, TerritoryImpactReport> previousReports) {
        long started = System.nanoTime();
        try {
            ImpactBaseline baseline = computer.buildBaseline(state, nowEpochMillis);
            Map<String, TerritoryImpactReport> reports = new LinkedHashMap<>();
            for (String target : baseline.graph().nodes().keySet().stream().sorted().toList()) {
                if (obsolete(requestedGeneration)) return;
                reports.put(target, computer.simulate(baseline, target));
                completed.incrementAndGet();
            }
            if (obsolete(requestedGeneration)) return;
            long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            view.set(new ImpactCacheView(
                    ImpactCacheStatus.READY,
                    requestedGeneration,
                    state.revision(),
                    key,
                    reports.size(),
                    reports.size(),
                    nowEpochMillis,
                    duration,
                    Map.copyOf(reports),
                    "Impact cache ready",
                    false));
        } catch (ImpactUnavailableException exception) {
            if (obsolete(requestedGeneration)) return;
            long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            view.set(new ImpactCacheView(
                    ImpactCacheStatus.UNAVAILABLE,
                    requestedGeneration,
                    state.revision(),
                    key,
                    0,
                    state.territories().size(),
                    0,
                    duration,
                    Map.of(),
                    exception.getMessage() == null ? "Required impact inputs are unavailable" : exception.getMessage(),
                    false));
        } catch (RuntimeException exception) {
            if (obsolete(requestedGeneration)) return;
            long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            view.set(new ImpactCacheView(
                    ImpactCacheStatus.FAILED,
                    requestedGeneration,
                    state.revision(),
                    key,
                    completed.get(),
                    state.territories().size(),
                    0,
                    duration,
                    previousReports,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    !previousReports.isEmpty()));
        } finally {
            progress.remove(requestedGeneration);
        }
    }

    private boolean obsolete(long requestedGeneration) {
        return closed || generation.get() != requestedGeneration || Thread.currentThread().isInterrupted();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        Future<?> current = active;
        if (current != null) current.cancel(true);
        executor.shutdownNow();
        ImpactCacheView previous = view.get();
        view.set(new ImpactCacheView(
                ImpactCacheStatus.CLOSED,
                generation.get(),
                previous.requestedRevision(),
                previous.cacheKey(),
                previous.completedTargets(),
                previous.totalTargets(),
                previous.builtAtEpochMillis(),
                previous.buildDurationMillis(),
                previous.completedReports(),
                "Impact cache closed",
                previous.reportsAreStale()));
    }
}
