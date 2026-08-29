package net.syllyaddons.compat.wynntils.v4_2_8;

import com.wynntils.core.components.Models;
import java.util.Map;
import net.syllyaddons.domain.CharacterIdentity;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.observation.ObservationBatch;

public final class WynntilsSessionAdapter {
    public ObservationBatch capture(long nowEpochMillis) {
        Evidence evidence = WynntilsEvidence.local(nowEpochMillis, "Observed from the current Wynntils session");

        ObservedValue<CharacterIdentity> character = null;
        if (Models.Character.hasCharacter() && isStableCharacterId(Models.Character.getId())) {
            character = ObservedValue.known(
                    new CharacterIdentity(
                            Models.Character.getId(),
                            Models.Character.getClassType().name(),
                            Models.Character.isReskinned()),
                    evidence);
        }

        ObservedValue<GuildIdentity> guild = null;
        if (Models.Guild.isInGuild()) {
            String guildName = Models.Guild.getGuildName();
            if (guildName != null && !guildName.isBlank()) {
                guild = ObservedValue.known(new GuildIdentity("", guildName, ""), evidence);
            }
        }

        return new ObservationBatch(nowEpochMillis, character, guild, null, null, Map.of());
    }

    private static boolean isStableCharacterId(String id) {
        return id != null && id.matches("[a-z0-9]{8}");
    }
}
