package net.syllyaddons.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.syllyaddons.domain.ResourceType;
import org.junit.jupiter.api.Test;

class SnapshotComparisonServiceTest {
    @Test
    void comparesNormalizedCurrentStateAgainstImportedBaseline() {
        SnapshotPayload baseline = SnapshotTestFixtures.content(SnapshotTestFixtures.state(2, 5, 1), 2_000).payload();
        SnapshotPayload current = SnapshotTestFixtures.content(SnapshotTestFixtures.state(9, 12, 4), 3_000).payload();

        SnapshotComparison comparison = new SnapshotComparisonService().compare(baseline, current);

        assertEquals(2, comparison.baselineRevision());
        assertEquals(9, comparison.currentRevision());
        assertEquals(7.0, comparison.resourceDeltas().get(ResourceType.ORE).generationChange());
        assertEquals(3.0, comparison.resourceDeltas().get(ResourceType.ORE).storedChange());
        assertTrue(comparison.territoryDeltas().stream()
                .anyMatch(delta -> delta.territory().equals("Mine")
                        && delta.changes().contains(TerritoryChangeKind.RESOURCES)));
        assertTrue(comparison.economyComparable());
        assertEquals(
                7.0,
                comparison.economyDeltas().get(ResourceType.ORE).deliveredProductionChange(),
                1.0e-9);
    }
}
