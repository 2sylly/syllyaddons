package net.syllyaddons.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.ProvenanceKind;
import net.syllyaddons.economy.ResourceProvenance;
import net.syllyaddons.routing.RouteDiagnostic;

public final class ResourceDrillDownService {
    public ResourceDrillDown build(SnapshotPayload payload, ResourceType resource) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(resource, "resource");
        if (payload.economy() == null) {
            List<RouteDiagnostic> diagnostics = new ArrayList<>(payload.analysisDiagnostics());
            diagnostics.add(new RouteDiagnostic(
                    "NO_ECONOMY_ANALYSIS", "This snapshot lacks the inputs needed for economy provenance"));
            return new ResourceDrillDown(resource, null, List.of(), List.of(), diagnostics, false);
        }

        List<ResourceProvenance> production = payload.economy().provenance().stream()
                .filter(value -> value.kind() == ProvenanceKind.PRODUCTION && value.resource() == resource)
                .sorted(Comparator.comparing(ResourceProvenance::sourceTerritory))
                .toList();
        Map<String, Double> expenses = new LinkedHashMap<>();
        payload.economy().provenance().stream()
                .filter(value -> value.resource() == resource)
                .flatMap(value -> value.spending().stream())
                .forEach(value -> expenses.merge(value.consumerTerritory(), value.amount(), Double::sum));
        List<ResourceExpenseLine> expenseLines = expenses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ResourceExpenseLine(entry.getKey(), entry.getValue()))
                .toList();
        List<RouteDiagnostic> diagnostics = new ArrayList<>(payload.analysisDiagnostics());
        diagnostics.addAll(payload.economy().diagnostics());
        return new ResourceDrillDown(
                resource,
                payload.economy().summaries().get(resource),
                production,
                expenseLines,
                diagnostics.stream().distinct().toList(),
                payload.economy().exact() && payload.analysisDiagnostics().isEmpty());
    }
}
