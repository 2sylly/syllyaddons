package net.syllyaddons.config;

import java.util.Optional;
import net.syllyaddons.domain.ResourceType;

public enum ImpactResourceFilter {
    ALL("All resources", null),
    EMERALDS("Emeralds", ResourceType.EMERALDS),
    ORE("Ore", ResourceType.ORE),
    WOOD("Wood", ResourceType.WOOD),
    FISH("Fish", ResourceType.FISH),
    CROPS("Crops", ResourceType.CROPS);

    private final String label;
    private final ResourceType resource;

    ImpactResourceFilter(String label, ResourceType resource) {
        this.label = label;
        this.resource = resource;
    }

    public String label() {
        return label;
    }

    public Optional<ResourceType> resource() {
        return Optional.ofNullable(resource);
    }

    public ImpactResourceFilter next() {
        ImpactResourceFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
