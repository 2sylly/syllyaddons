package net.syllyaddons.config;

public enum ImpactOverlayScope {
    OWN_GUILD("Own guild"),
    SELECTED_ENEMY("Selected enemy"),
    VISIBLE_GUILDS("Visible guilds");

    private final String label;

    ImpactOverlayScope(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public ImpactOverlayScope next() {
        ImpactOverlayScope[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
