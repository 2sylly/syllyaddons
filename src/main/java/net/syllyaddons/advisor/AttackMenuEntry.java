package net.syllyaddons.advisor;

import java.util.List;

/** Text passively read from one slot in Wynncraft's already-open attack menu. */
public record AttackMenuEntry(String displayName, List<String> lore) {
    public AttackMenuEntry {
        displayName = displayName == null ? "" : displayName.strip();
        lore = lore == null ? List.of() : lore.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
