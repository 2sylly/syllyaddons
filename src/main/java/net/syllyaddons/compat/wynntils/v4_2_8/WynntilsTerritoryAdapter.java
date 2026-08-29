package net.syllyaddons.compat.wynntils.v4_2_8;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.gui.TerritoryItem;
import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.models.territories.type.GuildResource;
import com.wynntils.models.territories.type.GuildResourceValues;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.type.CappedValue;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryRating;
import net.syllyaddons.observation.ObservationBatch;
import net.syllyaddons.observation.TerritoryObservation;

public final class WynntilsTerritoryAdapter {
    public ObservationBatch captureProfiles(long nowEpochMillis) {
        Evidence evidence = WynntilsEvidence.publicModel(
                nowEpochMillis, "Ownership and bounds exposed by Wynntils' territory profile cache");
        Map<String, TerritoryObservation> observations = new HashMap<>();

        Models.Territory.getTerritoryNames().forEach(name -> {
            TerritoryProfile profile = Models.Territory.getTerritoryProfile(name);
            if (profile == null) return;

            TerritoryOwner owner = isNoOwner(profile.getGuild())
                    ? TerritoryOwner.unowned()
                    : new TerritoryOwner("", profile.getGuild(), profile.getGuildPrefix());
            ObservedValue<Long> acquired = profile.getAcquired() == null
                    ? null
                    : ObservedValue.known(profile.getAcquired().toEpochMilli(), evidence);
            ObservedValue<TerritoryBounds> bounds = hasUsableBounds(profile)
                    ? ObservedValue.known(
                            new TerritoryBounds(
                                    Math.min(profile.getStartX(), profile.getEndX()),
                                    Math.min(profile.getStartZ(), profile.getEndZ()),
                                    Math.max(profile.getStartX(), profile.getEndX()),
                                    Math.max(profile.getStartZ(), profile.getEndZ())),
                            evidence)
                    : null;

            observations.put(
                    name,
                    new TerritoryObservation(
                            name,
                            ObservedValue.known(owner, evidence),
                            acquired,
                            null,
                            bounds,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
        });

        return ObservationBatch.territories(nowEpochMillis, observations);
    }

    public ObservationBatch captureAdvancementTerritories(long nowEpochMillis) {
        Evidence evidence = WynntilsEvidence.local(
                nowEpochMillis, "Passively observed from guild-map territory advancements parsed by Wynntils");
        Map<String, TerritoryObservation> observations = new HashMap<>();

        for (TerritoryPoi poi : Models.Territory.getTerritoryPoisFromAdvancement()) {
            TerritoryInfo info = poi.getTerritoryInfo();
            if (info == null) continue;

            TerritoryOwner owner = isNoOwner(info.getGuildName())
                    ? TerritoryOwner.unowned()
                    : new TerritoryOwner("", info.getGuildName(), info.getGuildPrefix());
            observations.put(
                    poi.getName(),
                    new TerritoryObservation(
                            poi.getName(),
                            ObservedValue.known(owner, evidence),
                            null,
                            ObservedValue.known(info.isHeadquarters(), evidence),
                            null,
                            ObservedValue.known(Set.copyOf(info.getTradingRoutes()), evidence),
                            ObservedValue.known(resources(info.getGenerators(), info.getStorage()), evidence),
                            rating(info.getTreasury(), evidence),
                            null,
                            rating(info.getDefences(), evidence),
                            null,
                            null));
        }

        return ObservationBatch.territories(nowEpochMillis, observations);
    }

    public ObservationBatch captureTerritoryItems(List<ItemStack> items, long nowEpochMillis) {
        Evidence evidence = WynntilsEvidence.local(
                nowEpochMillis, "Passively observed from territory menu items parsed by Wynntils");
        Map<String, TerritoryObservation> observations = new HashMap<>();

        for (ItemStack item : items) {
            Models.Item.asWynnItem(item, TerritoryItem.class).ifPresent(territory -> observations.put(
                    territory.getName(), fromTerritoryItem(territory, evidence)));
        }

        return ObservationBatch.territories(nowEpochMillis, observations);
    }

    private static TerritoryObservation fromTerritoryItem(TerritoryItem territory, Evidence evidence) {
        Map<String, Integer> upgrades = new HashMap<>();
        territory.getUpgrades().forEach((upgrade, level) -> upgrades.put(upgrade.name(), level));

        ObservedValue<Double> treasuryBonus = territory.getTreasuryBonus() < 0
                ? null
                : ObservedValue.known((double) territory.getTreasuryBonus(), evidence);

        return new TerritoryObservation(
                territory.getName(),
                null,
                null,
                ObservedValue.known(territory.isHeadquarters(), evidence),
                null,
                null,
                ObservedValue.known(resources(territory.getProduction(), territory.getStorage()), evidence),
                null,
                treasuryBonus,
                null,
                ObservedValue.known(Map.copyOf(upgrades), evidence),
                ObservedValue.known(List.copyOf(territory.getAlerts()), evidence));
    }

    private static Map<ResourceType, ResourceBalance> resources(
            Map<GuildResource, Integer> generation, Map<GuildResource, CappedValue> storage) {
        Set<GuildResource> present = new HashSet<>(generation.keySet());
        present.addAll(storage.keySet());
        Map<ResourceType, ResourceBalance> values = new EnumMap<>(ResourceType.class);

        for (GuildResource resource : present) {
            CappedValue stored = storage.get(resource);
            values.put(
                    ResourceType.valueOf(resource.name()),
                    new ResourceBalance(
                            generation.getOrDefault(resource, 0),
                            stored == null ? 0 : stored.current(),
                            stored == null ? 0 : stored.max()));
        }
        return Map.copyOf(values);
    }

    private static ObservedValue<TerritoryRating> rating(GuildResourceValues value, Evidence evidence) {
        if (value == null) return null;
        return ObservedValue.known(TerritoryRating.valueOf(value.name()), evidence);
    }

    private static boolean isNoOwner(String guildName) {
        return guildName == null
                || guildName.isBlank()
                || guildName.equalsIgnoreCase("No owner")
                || guildName.equalsIgnoreCase("None");
    }

    private static boolean hasUsableBounds(TerritoryProfile profile) {
        int limit = 30_000_000;
        return Math.abs((long) profile.getStartX()) <= limit
                && Math.abs((long) profile.getStartZ()) <= limit
                && Math.abs((long) profile.getEndX()) <= limit
                && Math.abs((long) profile.getEndZ()) <= limit;
    }
}
