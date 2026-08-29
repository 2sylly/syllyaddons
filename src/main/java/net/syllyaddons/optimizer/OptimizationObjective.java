package net.syllyaddons.optimizer;

public enum OptimizationObjective {
    MINIMUM_EXPENSE("Minimum expense"),
    REPAIR_DEFICITS("Repair deficits"),
    MAXIMUM_MINIMUM_BUFFER("Max minimum buffer"),
    PRESERVE_RESERVES("Preserve reserves");

    private final String label;

    OptimizationObjective(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
