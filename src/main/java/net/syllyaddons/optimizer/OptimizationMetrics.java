package net.syllyaddons.optimizer;

public record OptimizationMetrics(
        double totalExpensePerHour,
        double totalDeficitPerHour,
        double minimumEndingBuffer,
        double minimumReserveHeadroom,
        double totalEndingStorage,
        double totalReserveShortfall) {
    public OptimizationMetrics {
        if (!finite(totalExpensePerHour, totalDeficitPerHour, minimumEndingBuffer,
                minimumReserveHeadroom, totalEndingStorage, totalReserveShortfall)) {
            throw new IllegalArgumentException("Optimization metrics must be finite");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
