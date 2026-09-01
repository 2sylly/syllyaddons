package net.syllyaddons.diagnostics;

public enum Subsystem {
    COMPATIBILITY("Wynntils compatibility"),
    OBSERVATION("Observed data"),
    SPELL_PROFILES("Spell profiles"),
    SNAPSHOTS("Snapshots"),
    ECO_AUDITOR("Eco auditor"),
    TERRITORY_IMPACT("Territory impact"),
    ROUTING_ADVISOR("Routing advisor"),
    OPTIMIZER("Defence optimizer");

    private final String label;

    Subsystem(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
