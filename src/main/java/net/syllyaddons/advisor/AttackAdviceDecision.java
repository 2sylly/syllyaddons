package net.syllyaddons.advisor;

public enum AttackAdviceDecision {
    FASTEST_WORTH_COST("Fastest is worth the added cost"),
    CHEAPEST_NEGLIGIBLE_DELAY("Cheapest adds negligible delay"),
    FASTEST_TOO_EXPENSIVE("Cheapest avoids too much extra cost"),
    NO_SIGNIFICANT_DIFFERENCE("No significant difference"),
    UNAVAILABLE("Recommendation unavailable");

    private final String label;

    AttackAdviceDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
