package net.syllyaddons.impact;

import java.util.Objects;
import net.syllyaddons.domain.ResourceType;

public record ResourceImpactDelta(
        ResourceType resource,
        double baselineDeliveredPerHour,
        double simulatedDeliveredPerHour,
        double deliveredDeltaPerHour,
        double baselineTaxLossPerHour,
        double simulatedTaxLossPerHour,
        double taxLossDeltaPerHour,
        double baselineTowerSupplyPerHour,
        double simulatedTowerSupplyPerHour,
        double towerSupplyDeltaPerHour,
        double baselineDeficitPerHour,
        double simulatedDeficitPerHour,
        double deficitDeltaPerHour,
        double baselineEndingStorage,
        double simulatedEndingStorage,
        double endingStorageDelta,
        ImpactCertainty certainty) {
    public ResourceImpactDelta {
        resource = Objects.requireNonNull(resource, "resource");
        certainty = Objects.requireNonNull(certainty, "certainty");
    }
}
