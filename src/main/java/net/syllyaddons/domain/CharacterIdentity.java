package net.syllyaddons.domain;

import java.util.Objects;

public record CharacterIdentity(String id, String className, boolean reskinned) {
    public CharacterIdentity {
        id = requireText(id, "id");
        className = requireText(className, "className");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
