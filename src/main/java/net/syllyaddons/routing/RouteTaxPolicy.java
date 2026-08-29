package net.syllyaddons.routing;

@FunctionalInterface
public interface RouteTaxPolicy {
    TaxQuote quote(TerritoryNode from, TerritoryNode to);
}
