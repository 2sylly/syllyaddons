package net.syllyaddons.routing;

import java.util.Objects;

public record GraphDiagnostic(
        GraphDiagnosticType type, String territory, String relatedTerritory, String message) {
    public GraphDiagnostic {
        type = Objects.requireNonNull(type, "type");
        territory = Objects.requireNonNull(territory, "territory");
        relatedTerritory = relatedTerritory == null ? "" : relatedTerritory;
        message = Objects.requireNonNull(message, "message");
    }
}
