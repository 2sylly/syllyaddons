package net.syllyaddons.impact;

/** Five map states: neutral plus the four scored impact severities. */
public enum ImpactVisualSeverity {
    NONE(0x506F7785, 0xFF89919E),
    MINOR(0x60E5C849, 0xFFFFE06A),
    WARNING(0x60E68932, 0xFFFFA64D),
    CRITICAL(0x60D93648, 0xFFFF5C70),
    CATASTROPHIC(0x607A3DB8, 0xFFD49BFF);

    private final int fillColor;
    private final int borderColor;

    ImpactVisualSeverity(int fillColor, int borderColor) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
    }

    public int fillColor() {
        return fillColor;
    }

    public int borderColor() {
        return borderColor;
    }

    public static ImpactVisualSeverity forReport(TerritoryImpactReport report) {
        boolean changed = report.modes().values().stream().anyMatch(mode ->
                mode.routeImpacts().stream().anyMatch(TerritoryRouteImpact::changed)
                        || mode.resourceDeltas().values().stream().anyMatch(ImpactVisualSeverity::changed));
        if (!changed) return NONE;
        return switch (report.maximumSeverity()) {
            case MINOR -> MINOR;
            case WARNING -> WARNING;
            case CRITICAL -> CRITICAL;
            case CATASTROPHIC -> CATASTROPHIC;
        };
    }

    private static boolean changed(ResourceImpactDelta delta) {
        double epsilon = 1.0e-9;
        return Math.abs(delta.deliveredDeltaPerHour()) > epsilon
                || Math.abs(delta.taxLossDeltaPerHour()) > epsilon
                || Math.abs(delta.towerSupplyDeltaPerHour()) > epsilon
                || Math.abs(delta.deficitDeltaPerHour()) > epsilon
                || Math.abs(delta.endingStorageDelta()) > epsilon;
    }
}
