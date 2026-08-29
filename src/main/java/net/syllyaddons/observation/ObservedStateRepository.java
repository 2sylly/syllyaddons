package net.syllyaddons.observation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;

public final class ObservedStateRepository {
    private final AtomicReference<ObservedState> state;
    private final ObservedStateMerger merger;
    private final List<Consumer<ObservedState>> listeners = new CopyOnWriteArrayList<>();

    public ObservedStateRepository() {
        this(ObservedState.empty(), new ObservedStateMerger());
    }

    public ObservedStateRepository(ObservedState initialState, ObservedStateMerger merger) {
        state = new AtomicReference<>(Objects.requireNonNull(initialState, "initialState"));
        this.merger = Objects.requireNonNull(merger, "merger");
    }

    public ObservedState snapshot() {
        return state.get();
    }

    public ObservedState merge(ObservationBatch batch) {
        while (true) {
            ObservedState current = state.get();
            ObservedState merged = merger.merge(current, batch);
            if (merged == current) return current;
            if (state.compareAndSet(current, merged)) {
                notifyListeners(merged);
                return merged;
            }
        }
    }

    public ObservedState clearSession(long nowEpochMillis, String reason) {
        while (true) {
            ObservedState current = state.get();
            ObservedState cleared = new ObservedState(
                    current.schemaVersion(),
                    current.revision() + 1,
                    nowEpochMillis,
                    ObservedValue.unknown(reason),
                    ObservedValue.unknown(reason),
                    ObservedValue.unknown(reason),
                    ObservedValue.unknown(reason),
                    current.territories());
            if (state.compareAndSet(current, cleared)) {
                notifyListeners(cleared);
                return cleared;
            }
        }
    }

    public AutoCloseable addListener(Consumer<ObservedState> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void notifyListeners(ObservedState newState) {
        for (Consumer<ObservedState> listener : listeners) {
            listener.accept(newState);
        }
    }
}
