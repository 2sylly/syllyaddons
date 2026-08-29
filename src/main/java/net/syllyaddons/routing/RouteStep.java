package net.syllyaddons.routing;

public record RouteStep(
        String from,
        String to,
        double taxRate,
        double selectionCost,
        RuleConfidence taxConfidence,
        String taxBasis) {}
