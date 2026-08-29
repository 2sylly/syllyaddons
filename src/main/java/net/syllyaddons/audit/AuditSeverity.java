package net.syllyaddons.audit;

public enum AuditSeverity {
    INFO,
    WARNING,
    CRITICAL;

    public static AuditSeverity highest(AuditSeverity left, AuditSeverity right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
