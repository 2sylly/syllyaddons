package net.syllyaddons.domain;

import java.util.Objects;

public record GuildIdentity(String uuid, String name, String prefix) {
    public GuildIdentity {
        uuid = normalize(uuid);
        name = requireText(name, "name");
        prefix = normalize(prefix);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
