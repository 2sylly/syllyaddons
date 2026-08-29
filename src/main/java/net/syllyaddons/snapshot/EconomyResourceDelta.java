package net.syllyaddons.snapshot;

import net.syllyaddons.domain.ResourceType;

public record EconomyResourceDelta(
        ResourceType resource,
        double deliveredProductionChange,
        double taxLossChange,
        double expensesChange,
        double deficitChange,
        double endingStorageChange) {}
