package net.syllyaddons.routing;

import java.util.Objects;

public record RouteDiagnostic(String code, String message) {
    public RouteDiagnostic {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }
}
