package net.syllyaddons.routing;

/** How strongly a calculation rule or input is supported. */
public enum RuleConfidence {
    EXPLICIT_INPUT,
    RESEARCH_ASSUMPTION,
    UNKNOWN;

    public boolean isExact() {
        return this == EXPLICIT_INPUT;
    }

    public static RuleConfidence weakest(RuleConfidence left, RuleConfidence right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
