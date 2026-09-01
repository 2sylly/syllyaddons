package net.syllyaddons.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.syllyaddons.domain.ObservedState;

/** Exact-value redaction for free-form diagnostics included in a bundle. */
public final class SensitiveValueRedactor {
    private final List<String> sensitiveValues;

    public SensitiveValueRedactor(List<String> sensitiveValues) {
        this.sensitiveValues = sensitiveValues == null ? List.of() : sensitiveValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    public static SensitiveValueRedactor from(ObservedState state) {
        ArrayList<String> values = new ArrayList<>();
        if (state.character().isKnown()) values.add(state.character().value().id());
        if (state.guild().isKnown()) {
            values.add(state.guild().value().uuid());
            values.add(state.guild().value().name());
            values.add(state.guild().value().prefix());
        }
        state.territories().forEach((name, territory) -> {
            values.add(name);
            if (territory.owner().isKnown()) {
                values.add(territory.owner().value().guildUuid());
                values.add(territory.owner().value().guildName());
                values.add(territory.owner().value().guildPrefix());
            }
        });
        return new SensitiveValueRedactor(values);
    }

    public String redact(String value) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        String redacted = value;
        for (String sensitive : sensitiveValues) redacted = redacted.replace(sensitive, "[redacted]");
        return redacted;
    }

    public List<String> redact(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(this::redact).toList();
    }
}
