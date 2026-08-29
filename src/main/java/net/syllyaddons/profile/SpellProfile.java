package net.syllyaddons.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record SpellProfile(String id, String name, List<SpellBinding> bindings) {
    public SpellProfile {
        id = normalize(id, "id");
        name = normalize(name, "name");
        bindings = normalizeBindings(bindings);
    }

    public OptionalInt spellFor(PhysicalInput input) {
        Objects.requireNonNull(input, "input");
        return bindings.stream()
                .filter(binding -> binding.input().equals(input))
                .mapToInt(SpellBinding::spellNumber)
                .findFirst();
    }

    public Optional<PhysicalInput> inputForSpell(int spellNumber) {
        return bindings.stream()
                .filter(binding -> binding.spellNumber() == spellNumber)
                .map(SpellBinding::input)
                .findFirst();
    }

    public SpellProfile renamed(String newName) {
        return new SpellProfile(id, newName, bindings);
    }

    public SpellProfile withBinding(int spellNumber, PhysicalInput input) {
        Objects.requireNonNull(input, "input");
        List<SpellBinding> updated = new ArrayList<>(bindings);
        updated.removeIf(binding -> binding.spellNumber() == spellNumber || binding.input().equals(input));
        updated.add(new SpellBinding(input, spellNumber));
        return new SpellProfile(id, name, updated);
    }

    public SpellProfile withoutSpell(int spellNumber) {
        return new SpellProfile(
                id,
                name,
                bindings.stream().filter(binding -> binding.spellNumber() != spellNumber).toList());
    }

    private static List<SpellBinding> normalizeBindings(List<SpellBinding> source) {
        if (source == null) return List.of();

        Map<PhysicalInput, SpellBinding> byInput = new LinkedHashMap<>();
        for (SpellBinding binding : source) {
            if (binding == null) continue;
            byInput.values().removeIf(existing -> existing.spellNumber() == binding.spellNumber());
            byInput.put(binding.input(), binding);
        }
        return List.copyOf(byInput.values());
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
