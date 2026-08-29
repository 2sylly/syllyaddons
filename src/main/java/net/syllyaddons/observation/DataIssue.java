package net.syllyaddons.observation;

import java.util.Objects;
import net.syllyaddons.domain.Evidence;

public record DataIssue(
        DataIssueType type, DataGroup group, String scope, String field, String message, Evidence evidence) {
    public DataIssue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(group, "group");
        scope = normalize(scope, "global");
        field = normalize(field, "unknown");
        message = normalize(message, type.name());
        Objects.requireNonNull(evidence, "evidence");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
