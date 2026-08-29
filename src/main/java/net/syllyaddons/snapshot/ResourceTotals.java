package net.syllyaddons.snapshot;

import net.syllyaddons.domain.ResourceType;

public record ResourceTotals(
        ResourceType resource,
        double generationPerHour,
        double stored,
        double storageLimit,
        int territoriesWithoutData) {}
