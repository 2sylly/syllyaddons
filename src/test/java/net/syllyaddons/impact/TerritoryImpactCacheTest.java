package net.syllyaddons.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.RoutingMode;
import org.junit.jupiter.api.Test;

class TerritoryImpactCacheTest {
    @Test
    void requestIsNonBlockingAndObsoleteGenerationCanNeverReplaceNewerResults() throws Exception {
        ObservedState first = withRevision(state(10), 1);
        ObservedState second = withRevision(state(11), 2);
        BlockingComputer computer = new BlockingComputer();
        TerritoryImpactCache cache = new TerritoryImpactCache(
                computer,
                new ImpactCacheKeyFactory(),
                Executors.newCachedThreadPool());
        try {
            long startedNanos = System.nanoTime();
            cache.request(first, 2_000);
            long requestMillis = (System.nanoTime() - startedNanos) / 1_000_000;
            assertTrue(requestMillis < 100, "request should only enqueue work, took " + requestMillis + "ms");
            assertTrue(computer.firstStarted.await(1, TimeUnit.SECONDS));

            cache.request(second, 3_000);
            ImpactCacheView ready = awaitReady(cache, 2);
            assertEquals(ImpactCacheStatus.READY, ready.status());
            assertEquals(2, ready.requestedRevision());
            assertEquals(ready.totalTargets(), ready.completedTargets());

            int simulationsAfterBuild = computer.simulations.get();
            for (int index = 0; index < 1_000; index++) {
                assertTrue(cache.lookupCompleted("Source").isPresent());
            }
            assertEquals(simulationsAfterBuild, computer.simulations.get(), "lookup must never calculate");

            computer.releaseFirst.countDown();
            Thread.sleep(30);
            assertEquals(2, cache.view().requestedRevision(), "obsolete result must be discarded");
        } finally {
            computer.releaseFirst.countDown();
            cache.close();
        }
    }

    private static ImpactCacheView awaitReady(TerritoryImpactCache cache, long revision) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            ImpactCacheView view = cache.view();
            if (view.status() == ImpactCacheStatus.READY && view.requestedRevision() == revision) return view;
            Thread.sleep(5);
        }
        throw new AssertionError("cache did not become ready: " + cache.view());
    }

    private static ObservedState state(long generation) {
        return TerritoryImpactSimulatorTest.state(
                RoutingMode.CHEAPEST,
                TerritoryImpactSimulatorTest.territory(
                        "Source", TerritoryImpactSimulatorTest.owner(), List.of("HQ"), generation),
                TerritoryImpactSimulatorTest.territory(
                        "HQ", TerritoryImpactSimulatorTest.owner(), List.of("Source"), 0));
    }

    private static ObservedState withRevision(ObservedState state, long revision) {
        return new ObservedState(
                state.schemaVersion(),
                revision,
                state.assembledAtEpochMillis(),
                state.character(),
                state.guild(),
                state.hqTerritory(),
                state.routingMode(),
                state.territories());
    }

    private static final class BlockingComputer implements TerritoryImpactComputer {
        private final TerritoryImpactSimulator delegate = new TerritoryImpactSimulator();
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final AtomicInteger simulations = new AtomicInteger();

        @Override
        public ImpactBaseline buildBaseline(ObservedState state, long nowEpochMillis) {
            if (state.revision() == 1) {
                firstStarted.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        released = releaseFirst.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        // Deliberately ignore cancellation to prove the generation guard is sufficient.
                    }
                }
            }
            return delegate.buildBaseline(state, nowEpochMillis);
        }

        @Override
        public TerritoryImpactReport simulate(ImpactBaseline baseline, String removedTerritory) {
            simulations.incrementAndGet();
            return delegate.simulate(baseline, removedTerritory);
        }
    }
}
