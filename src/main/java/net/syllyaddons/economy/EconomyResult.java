package net.syllyaddons.economy;

import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RuleConfidence;

public record EconomyResult(
        String economyRulesVersion,
        String routingRulesVersion,
        Map<ResourceType, ResourceEconomySummary> summaries,
        List<ResourceProvenance> provenance,
        List<ResourceDeficit> deficits,
        RuleConfidence confidence,
        List<RouteDiagnostic> diagnostics) {
    public EconomyResult {
        summaries = Map.copyOf(summaries);
        provenance = List.copyOf(provenance);
        deficits = List.copyOf(deficits);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean exact() {
        return confidence.isExact() && diagnostics.isEmpty();
    }
}
