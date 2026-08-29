package net.syllyaddons.snapshot;

import java.util.List;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.ResourceEconomySummary;
import net.syllyaddons.economy.ResourceProvenance;
import net.syllyaddons.routing.RouteDiagnostic;

public record ResourceDrillDown(
        ResourceType resource,
        ResourceEconomySummary totals,
        List<ResourceProvenance> production,
        List<ResourceExpenseLine> expenses,
        List<RouteDiagnostic> diagnostics,
        boolean exact) {
    public ResourceDrillDown {
        production = List.copyOf(production);
        expenses = List.copyOf(expenses);
        diagnostics = List.copyOf(diagnostics);
    }
}
