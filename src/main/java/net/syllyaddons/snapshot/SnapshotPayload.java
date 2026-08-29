package net.syllyaddons.snapshot;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.routing.RouteDiagnostic;

public record SnapshotPayload(
        EcoSnapshot observed,
        EconomyResult economy,
        List<RouteDiagnostic> analysisDiagnostics) {
    public SnapshotPayload {
        observed = Objects.requireNonNull(observed, "observed");
        analysisDiagnostics = List.copyOf(Objects.requireNonNull(analysisDiagnostics, "analysisDiagnostics"));
    }

    public boolean hasEconomyAnalysis() {
        return economy != null;
    }
}
