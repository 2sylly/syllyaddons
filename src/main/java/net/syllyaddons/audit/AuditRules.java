package net.syllyaddons.audit;

public record AuditRules(
        String version,
        int longRouteHops,
        double expensiveRouteTaxFraction,
        double highStorageFraction,
        double lowValueBenefitPerUpkeep,
        double safeDowngradeRemainingMarginFraction) {
    public AuditRules {
        if (version == null || version.isBlank() || longRouteHops < 1
                || expensiveRouteTaxFraction < 0 || expensiveRouteTaxFraction > 1
                || highStorageFraction < 0 || highStorageFraction > 1
                || lowValueBenefitPerUpkeep < 0
                || safeDowngradeRemainingMarginFraction < 0) {
            throw new IllegalArgumentException("Invalid audit rules");
        }
    }

    public static AuditRules track6Defaults() {
        return new AuditRules("eco-auditor-2026-08-29.1", 6, 0.35, 0.90, 0.25, 0.20);
    }
}
