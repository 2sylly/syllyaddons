package net.syllyaddons.impact;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyRules;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.snapshot.ObservedEconomyAnalyzer;

/** Hashes only inputs capable of changing a Track 7 result; evidence age and revision are deliberately excluded. */
public final class ImpactCacheKeyFactory {
    public String create(ObservedState state) {
        MessageDigest digest = sha256();
        put(digest, "impact-cache-v1");
        put(digest, RoutingRules.research2026_08_29().version());
        put(digest, EconomyRules.research2026_08_29().version());
        put(digest, Double.doubleToLongBits(ObservedEconomyAnalyzer.ASSUMED_FOREIGN_TAX_RATE));
        put(digest, state.guild().isKnown());
        if (state.guild().isKnown()) {
            put(digest, state.guild().value().uuid());
            put(digest, state.guild().value().name());
        }
        put(digest, state.hqTerritory().isKnown());
        if (state.hqTerritory().isKnown()) put(digest, state.hqTerritory().value());
        put(digest, state.routingMode().isKnown());
        if (state.routingMode().isKnown()) put(digest, state.routingMode().value().name());

        state.territories().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> putTerritory(digest, entry.getValue()));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void putTerritory(MessageDigest digest, TerritoryState territory) {
        put(digest, territory.name());
        put(digest, territory.owner().isKnown());
        if (territory.owner().isKnown()) {
            put(digest, territory.owner().value().guildUuid());
            put(digest, territory.owner().value().guildName());
        }
        put(digest, territory.headquarters().isKnown());
        if (territory.headquarters().isKnown()) put(digest, territory.headquarters().value());
        put(digest, territory.bounds().isKnown());
        if (territory.bounds().isKnown()) {
            put(digest, territory.bounds().value().minX());
            put(digest, territory.bounds().value().minZ());
            put(digest, territory.bounds().value().maxX());
            put(digest, territory.bounds().value().maxZ());
        }
        put(digest, territory.links().isKnown());
        if (territory.links().isKnown()) {
            put(digest, territory.links().value().size());
            territory.links().value().forEach(value -> put(digest, value));
        }
        put(digest, territory.resources().isKnown());
        if (territory.resources().isKnown()) {
            territory.resources().value().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().ordinal()))
                    .forEach(entry -> {
                        put(digest, entry.getKey().name());
                        put(digest, entry.getValue().generationPerHour());
                        put(digest, entry.getValue().stored());
                        put(digest, entry.getValue().storageLimit());
                    });
            for (ResourceType resource : ResourceType.values()) {
                put(digest, territory.resources().value().containsKey(resource));
            }
        }
        put(digest, territory.upgrades().isKnown());
        if (territory.upgrades().isKnown()) {
            territory.upgrades().value().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        put(digest, entry.getKey());
                        put(digest, entry.getValue());
                    });
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void put(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        put(digest, bytes.length);
        digest.update(bytes);
    }

    private static void put(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void put(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void put(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
