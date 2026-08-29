package net.syllyaddons.impact;

public enum ImpactSeverity {
    MINOR,
    WARNING,
    CRITICAL,
    CATASTROPHIC;

    public static ImpactSeverity fromScore(double score) {
        if (score >= 70) return CATASTROPHIC;
        if (score >= 45) return CRITICAL;
        if (score >= 20) return WARNING;
        return MINOR;
    }
}
