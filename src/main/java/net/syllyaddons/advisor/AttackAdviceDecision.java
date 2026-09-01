package net.syllyaddons.advisor;

public enum AttackAdviceDecision {
    FASTEST_FASTER("Fastest has the shorter queue"),
    SAME_QUEUE_TIME("Fastest and Cheapest have the same queue time"),
    UNAVAILABLE("Recommendation unavailable");

    private final String label;

    AttackAdviceDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
