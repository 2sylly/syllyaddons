package net.syllyaddons.economy;

import net.syllyaddons.domain.ResourceType;

public record ResourceEconomySummary(
        ResourceType resource,
        double openingStorage,
        double grossProduction,
        double taxLoss,
        double deliveredProduction,
        double expenses,
        double spent,
        double deficit,
        double endingStorage,
        double overflowLoss,
        double undeliveredProduction) {}
