package net.syllyaddons.routing;

import java.util.Map;
import java.util.Objects;

/** Edge tax inputs supplied by a fixture or a future live observer. */
public final class ExplicitTaxPolicy implements RouteTaxPolicy {
    private final Map<RouteEdge, Double> rates;
    private final Double defaultRate;

    public ExplicitTaxPolicy(Map<RouteEdge, Double> rates, Double defaultRate) {
        this.rates = Map.copyOf(Objects.requireNonNull(rates, "rates"));
        this.rates.forEach((edge, rate) -> {
            if (edge == null || rate == null || !Double.isFinite(rate) || rate < 0 || rate > 1) {
                throw new IllegalArgumentException("Edge tax rates must be between 0 and 1");
            }
        });
        if (defaultRate != null && (!Double.isFinite(defaultRate) || defaultRate < 0 || defaultRate > 1)) {
            throw new IllegalArgumentException("Default tax rate must be between 0 and 1");
        }
        this.defaultRate = defaultRate;
    }

    @Override
    public TaxQuote quote(TerritoryNode from, TerritoryNode to) {
        Double rate = rates.get(new RouteEdge(from.name(), to.name()));
        if (rate != null) return new TaxQuote(rate, RuleConfidence.EXPLICIT_INPUT, "explicit edge tax");
        if (defaultRate != null) {
            return new TaxQuote(defaultRate, RuleConfidence.EXPLICIT_INPUT, "explicit default tax");
        }
        return new TaxQuote(0, RuleConfidence.UNKNOWN, "tax was not observed");
    }
}
