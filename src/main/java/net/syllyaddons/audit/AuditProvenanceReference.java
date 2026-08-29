package net.syllyaddons.audit;

import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;

public record AuditProvenanceReference(
        ResourceType resource,
        String sourceTerritory,
        List<String> route,
        double grossAmount,
        double deliveredAmount,
        double taxLoss) {
    public AuditProvenanceReference {
        resource = Objects.requireNonNull(resource, "resource");
        sourceTerritory = Objects.requireNonNull(sourceTerritory, "sourceTerritory").strip();
        route = List.copyOf(Objects.requireNonNull(route, "route"));
    }
}
