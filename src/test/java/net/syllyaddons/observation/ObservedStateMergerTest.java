package net.syllyaddons.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryOwner;
import org.junit.jupiter.api.Test;

class ObservedStateMergerTest {
    private final ObservedStateMerger merger = new ObservedStateMerger();

    @Test
    void mergesPartialTerritoryObservationAndAdvancesRevision() {
        Evidence evidence = new Evidence(EvidenceKind.PUBLIC_EXACT, 100, "api", "v3", "");
        TerritoryObservation territory = observation(
                "Ragni", ObservedValue.known(new TerritoryOwner("uuid", "Guild", "TAG"), evidence));
        ObservationBatch batch = ObservationBatch.territories(100, Map.of("Ragni", territory));

        ObservedState result = merger.merge(ObservedState.empty(), batch);

        assertEquals(1, result.revision());
        assertEquals("Guild", result.territories().get("Ragni").owner().value().guildName());
    }

    @Test
    void identicalBatchDoesNotPublishAnotherRevision() {
        Evidence evidence = new Evidence(EvidenceKind.PUBLIC_EXACT, 100, "api", "v3", "");
        ObservationBatch batch = ObservationBatch.territories(
                100,
                Map.of(
                        "Ragni",
                        observation(
                                "Ragni",
                                ObservedValue.known(new TerritoryOwner("uuid", "Guild", "TAG"), evidence))));
        ObservedState first = merger.merge(ObservedState.empty(), batch);

        assertSame(first, merger.merge(first, batch));
    }

    private static TerritoryObservation observation(String name, ObservedValue<TerritoryOwner> owner) {
        return new TerritoryObservation(name, owner, null, null, null, null, null, null, null, null, null, null);
    }
}
