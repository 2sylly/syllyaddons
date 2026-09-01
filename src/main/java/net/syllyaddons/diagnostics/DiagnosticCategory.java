package net.syllyaddons.diagnostics;

/** User-facing reason category. These values deliberately separate integration, input, and arithmetic failures. */
public enum DiagnosticCategory {
    HEALTHY,
    WAITING,
    DISABLED,
    INTEGRATION_FAILURE,
    MISSING_DATA,
    CALCULATION_DISAGREEMENT,
    INTERNAL_FAILURE
}
