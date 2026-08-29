package net.syllyaddons.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.observation.ObservationBatch;
import net.syllyaddons.observation.ObservedStateMerger;
import net.syllyaddons.observation.TerritoryObservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservedStatePersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stateRoundTripsThroughJsonAndAtomicStore() throws Exception {
        Evidence evidence = new Evidence(EvidenceKind.PUBLIC_EXACT, 100, "api", "v3", "");
        TerritoryObservation territory = new TerritoryObservation(
                "Ragni",
                ObservedValue.known(new TerritoryOwner("uuid", "Guild", "TAG"), evidence),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        ObservedState state = new ObservedStateMerger()
                .merge(ObservedState.empty(), ObservationBatch.territories(100, Map.of("Ragni", territory)));
        ObservedStateJsonCodec codec = new ObservedStateJsonCodec();

        assertEquals(state, codec.decode(codec.encode(state)));

        HistoricalObservationStore store =
                new HistoricalObservationStore(temporaryDirectory.resolve("latest.json"), codec);
        assertTrue(store.saveIfUseful(state));
        assertEquals(state, store.load(200).orElseThrow().state());
    }
}
