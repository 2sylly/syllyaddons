package net.syllyaddons.config;

public enum ImpactAlertSize {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large");

    private final String label;

    ImpactAlertSize(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public ImpactAlertSize next() {
        ImpactAlertSize[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
