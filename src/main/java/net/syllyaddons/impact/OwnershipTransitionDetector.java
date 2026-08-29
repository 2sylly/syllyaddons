package net.syllyaddons.impact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;

/** Diffs normalized known owners; unknown values are never interpreted as captures or losses. */
public final class OwnershipTransitionDetector {
    public List<TerritoryOwnershipChange> diff(ObservedState before, ObservedState after) {
        List<TerritoryOwnershipChange> changes = new ArrayList<>();
        if (after.revision() <= before.revision()) return List.of();
        for (var entry : before.territories().entrySet()) {
            TerritoryState previous = entry.getValue();
            TerritoryState current = after.territories().get(entry.getKey());
            if (current == null || !previous.owner().isKnown() || !current.owner().isKnown()) continue;
            TerritoryOwner oldOwner = previous.owner().value();
            TerritoryOwner newOwner = current.owner().value();
            if (!sameOwner(oldOwner, newOwner)) {
                changes.add(new TerritoryOwnershipChange(
                        entry.getKey(), oldOwner, newOwner, before.revision(), after.revision()));
            }
        }
        return List.copyOf(changes);
    }

    public static boolean ownedBy(TerritoryOwner owner, GuildIdentity guild) {
        if (!owner.guildUuid().isBlank() && !guild.uuid().isBlank()) {
            return normalize(owner.guildUuid()).equals(normalize(guild.uuid()));
        }
        return normalize(owner.guildName()).equals(normalize(guild.name()));
    }

    public static boolean sameSession(ObservedState before, ObservedState after) {
        if (!before.guild().isKnown() || !after.guild().isKnown()
                || !before.character().isKnown() || !after.character().isKnown()) return false;
        GuildIdentity oldGuild = before.guild().value();
        GuildIdentity newGuild = after.guild().value();
        boolean guildMatches = (!oldGuild.uuid().isBlank() && !newGuild.uuid().isBlank())
                ? normalize(oldGuild.uuid()).equals(normalize(newGuild.uuid()))
                : normalize(oldGuild.name()).equals(normalize(newGuild.name()));
        return guildMatches && before.character().value().id().equals(after.character().value().id());
    }

    private static boolean sameOwner(TerritoryOwner first, TerritoryOwner second) {
        if (!first.guildUuid().isBlank() && !second.guildUuid().isBlank()) {
            return normalize(first.guildUuid()).equals(normalize(second.guildUuid()));
        }
        return normalize(first.guildName()).equals(normalize(second.guildName()));
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
