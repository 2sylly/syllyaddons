package net.syllyaddons.routing;

/** Research fallback until live diplomacy/tax values are observable. */
public final class OwnerTaxPolicy implements RouteTaxPolicy {
    private final String ownerId;
    private final double foreignRate;

    public OwnerTaxPolicy(String ownerId, double foreignRate) {
        this.ownerId = ownerId == null ? "" : ownerId.strip();
        if (!Double.isFinite(foreignRate) || foreignRate < 0 || foreignRate > 1) {
            throw new IllegalArgumentException("Foreign tax rate must be between 0 and 1");
        }
        this.foreignRate = foreignRate;
    }

    @Override
    public TaxQuote quote(TerritoryNode from, TerritoryNode to) {
        boolean own = !ownerId.isBlank() && ownerId.equals(to.ownerId());
        return new TaxQuote(
                own ? 0 : foreignRate,
                RuleConfidence.RESEARCH_ASSUMPTION,
                own ? "assumed zero tax through own territory" : "assumed foreign territory tax");
    }
}
