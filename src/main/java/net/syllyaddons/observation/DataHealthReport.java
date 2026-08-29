package net.syllyaddons.observation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record DataHealthReport(long evaluatedAtEpochMillis, long stateRevision, List<DataIssue> issues) {
    public DataHealthReport {
        if (evaluatedAtEpochMillis < 0 || stateRevision < 0) {
            throw new IllegalArgumentException("Invalid data-health metadata");
        }
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean healthy() {
        return issues.isEmpty();
    }

    public Map<DataIssueType, Long> countsByType() {
        return issues.stream().collect(Collectors.groupingBy(DataIssue::type, Collectors.counting()));
    }
}
