package net.syllyaddons.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryOwner;

/** Preserves calculation inputs while replacing character, guild, and territory identities with local aliases. */
public final class RedactedObservedStateFactory {
    private final Gson gson = new Gson();

    public JsonObject create(ObservedState state) {
        Map<String, String> territoryAliases = aliases(state.territories().keySet().stream().sorted().toList(), "territory-");
        Map<String, String> ownerAliases = new HashMap<>();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", state.schemaVersion());
        root.addProperty("revision", state.revision());
        root.addProperty("assembledAtEpochMillis", state.assembledAtEpochMillis());
        root.addProperty(
                "redaction",
                "Character IDs, guild identities, profile names, territory names, evidence notes, and alert text are omitted or aliased.");

        JsonObject session = new JsonObject();
        JsonObject character = observed(state.character(), identity -> {
            JsonObject value = new JsonObject();
            value.addProperty("className", identity.className());
            value.addProperty("reskinned", identity.reskinned());
            return value;
        });
        session.add("character", character);
        session.add("guild", observed(state.guild(), ignored -> {
            JsonObject value = new JsonObject();
            value.addProperty("identityRedacted", true);
            return value;
        }));
        session.add("headquarters", observed(
                state.hqTerritory(), value -> gson.toJsonTree(territoryAliases.getOrDefault(value, "territory-unknown"))));
        session.add("routingMode", observed(state.routingMode(), gson::toJsonTree));
        root.add("session", session);

        JsonArray territories = new JsonArray();
        state.territories().values().stream().sorted(java.util.Comparator.comparing(value -> value.name())).forEach(territory -> {
            JsonObject item = new JsonObject();
            item.addProperty("alias", territoryAliases.get(territory.name()));
            item.add("owner", observed(territory.owner(), owner -> gson.toJsonTree(
                    ownerAliases.computeIfAbsent(ownerKey(owner), ignored -> "guild-" + (ownerAliases.size() + 1)))));
            item.add("acquiredAtEpochMillis", observed(territory.acquiredAtEpochMillis(), gson::toJsonTree));
            item.add("headquarters", observed(territory.headquarters(), gson::toJsonTree));
            item.add("bounds", observed(territory.bounds(), gson::toJsonTree));
            item.add("links", observed(territory.links(), links -> gson.toJsonTree(links.stream()
                    .map(link -> territoryAliases.getOrDefault(link, "territory-external"))
                    .toList())));
            item.add("resources", observed(territory.resources(), gson::toJsonTree));
            item.add("treasury", observed(territory.treasury(), gson::toJsonTree));
            item.add("treasuryBonusPercent", observed(territory.treasuryBonusPercent(), gson::toJsonTree));
            item.add("defences", observed(territory.defences(), gson::toJsonTree));
            item.add("upgrades", observed(territory.upgrades(), gson::toJsonTree));
            item.add("alertCount", observed(territory.alerts(), alerts -> gson.toJsonTree(alerts.size())));
            territories.add(item);
        });
        root.add("territories", territories);
        return root;
    }

    private static <T> JsonObject observed(ObservedValue<T> observed, Function<T, JsonElement> valueMapper) {
        JsonObject result = new JsonObject();
        result.addProperty("known", observed.isKnown());
        result.add("value", observed.isKnown() ? valueMapper.apply(observed.value()) : JsonNull.INSTANCE);
        JsonObject evidence = new JsonObject();
        evidence.addProperty("kind", observed.evidence().kind().name());
        evidence.addProperty("observedAtEpochMillis", observed.evidence().observedAtEpochMillis());
        evidence.addProperty("source", observed.evidence().source());
        evidence.addProperty("sourceVersion", observed.evidence().sourceVersion());
        result.add("evidence", evidence);
        return result;
    }

    private static Map<String, String> aliases(List<String> values, String prefix) {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            aliases.put(values.get(index), prefix + String.format(java.util.Locale.ROOT, "%03d", index + 1));
        }
        return Map.copyOf(aliases);
    }

    private static String ownerKey(TerritoryOwner owner) {
        return owner.guildUuid() + "\u0000" + owner.guildName() + "\u0000" + owner.guildPrefix();
    }
}
