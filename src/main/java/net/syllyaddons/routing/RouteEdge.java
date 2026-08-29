package net.syllyaddons.routing;

import java.util.Objects;

public record RouteEdge(String from, String to) {
    public RouteEdge {
        from = normalize(from, "from");
        to = normalize(to, "to");
    }

    private static String normalize(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
