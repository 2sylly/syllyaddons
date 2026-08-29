package net.syllyaddons.domain;

public record ResourceBalance(long generationPerHour, long stored, long storageLimit) {
    public ResourceBalance {
        if (generationPerHour < 0 || stored < 0 || storageLimit < 0) {
            throw new IllegalArgumentException("Resource values must be non-negative");
        }
    }
}
