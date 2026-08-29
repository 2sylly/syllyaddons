package net.syllyaddons.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryOwner;
import org.junit.jupiter.api.Test;

class DataHealthServiceTest {
    @Test
    void reportsMissingAndStaleFieldsWithoutDiscardingTheValue() {
        Evidence oldEvidence = new Evidence(EvidenceKind.PUBLIC_EXACT, 1, "api", "v3", "");
        TerritoryObservation territory = new TerritoryObservation(
                "Ragni",
                ObservedValue.known(new TerritoryOwner("", "Guild", "TAG"), oldEvidence),
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
                .merge(ObservedState.empty(), ObservationBatch.territories(1, Map.of("Ragni", territory)));

        DataHealthReport report =
                new DataHealthService(FreshnessPolicy.personalDefaults()).assess(state, 1_000_000);

        assertTrue(report.issues().stream().anyMatch(issue -> issue.type() == DataIssueType.STALE
                && issue.field().equals("owner")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type() == DataIssueType.MISSING
                && issue.field().equals("links")));
        assertEquals(state.revision(), report.stateRevision());
    }
}
