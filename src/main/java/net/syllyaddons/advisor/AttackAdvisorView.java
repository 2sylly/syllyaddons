package net.syllyaddons.advisor;

public record AttackAdvisorView(
        AttackMenuSnapshot menu,
        AttackRoutingAdvice advice,
        QueueTimerValidation queueValidation,
        long updatedAtEpochMillis) {
    public AttackAdvisorView {
        java.util.Objects.requireNonNull(menu, "menu");
        java.util.Objects.requireNonNull(advice, "advice");
    }
}
