package net.syllyaddons.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.syllyaddons.domain.ResourceType;
import org.junit.jupiter.api.Test;

class ResourceDrillDownServiceTest {
    @Test
    void exposesCompleteSourceRoutesAndEstimateDiagnostics() {
        SnapshotPayload payload = SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000).payload();

        ResourceDrillDown drillDown = new ResourceDrillDownService().build(payload, ResourceType.ORE);

        assertEquals(12.0, drillDown.totals().grossProduction(), 1.0e-9);
        assertEquals(2, drillDown.production().size());
        assertTrue(drillDown.production().stream().anyMatch(value -> value.sourceTerritory().equals("Mine")
                && value.route().equals(List.of("Mine", "HQ"))
                && value.taxSteps().size() == 1));
        assertTrue(drillDown.diagnostics().stream()
                .anyMatch(value -> value.code().equals("EXPENSE_MODEL_UNAVAILABLE")));
        assertFalse(drillDown.exact());
    }

    @Test
    void missingAnalysisProducesAnInspectableReasonInsteadOfEmptyExactTotals() {
        SnapshotPayload original = SnapshotTestFixtures.content(SnapshotTestFixtures.state(4, 10, 3), 2_000).payload();
        SnapshotPayload withoutAnalysis = new SnapshotPayload(original.observed(), null, List.of());

        ResourceDrillDown drillDown = new ResourceDrillDownService().build(withoutAnalysis, ResourceType.ORE);

        assertEquals(null, drillDown.totals());
        assertFalse(drillDown.exact());
        assertTrue(drillDown.diagnostics().stream()
                .anyMatch(value -> value.code().equals("NO_ECONOMY_ANALYSIS")));
    }
}
