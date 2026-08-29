package net.syllyaddons.economy;

import java.util.List;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RuleConfidence;

public record ResourceProvenance(
        ProvenanceKind kind,
        String sourceTerritory,
        ResourceType resource,
        double sourceAmount,
        List<String> route,
        List<TaxLedgerStep> taxSteps,
        double taxLoss,
        double deliveredToHq,
        List<SpendingAllocation> spending,
        double storedAtHq,
        double overflowLoss,
        double undelivered,
        RuleConfidence confidence,
        List<RouteDiagnostic> diagnostics) {
    public ResourceProvenance {
        route = List.copyOf(route);
        taxSteps = List.copyOf(taxSteps);
        spending = List.copyOf(spending);
        diagnostics = List.copyOf(diagnostics);
    }
}
