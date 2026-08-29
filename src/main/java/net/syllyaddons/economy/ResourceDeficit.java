package net.syllyaddons.economy;

import net.syllyaddons.domain.ResourceType;

public record ResourceDeficit(
        String consumerTerritory, ResourceType resource, double required, double supplied, double unmet) {}
