package net.syllyaddons.snapshot;

import net.syllyaddons.domain.ResourceType;

public record ResourceTotalDelta(
        ResourceType resource,
        ResourceTotals baseline,
        ResourceTotals current,
        double generationChange,
        double storedChange,
        double storageLimitChange) {}
