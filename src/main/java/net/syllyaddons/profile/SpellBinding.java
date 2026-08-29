package net.syllyaddons.profile;

import java.util.Objects;

public record SpellBinding(PhysicalInput input, int spellNumber) {
    public SpellBinding {
        Objects.requireNonNull(input, "input");
        if (spellNumber < 1 || spellNumber > 4) {
            throw new IllegalArgumentException("Spell number must be between 1 and 4");
        }
    }
}
