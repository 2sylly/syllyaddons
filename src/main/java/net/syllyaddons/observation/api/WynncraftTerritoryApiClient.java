package net.syllyaddons.observation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryRating;
import net.syllyaddons.observation.ObservationBatch;
import net.syllyaddons.observation.TerritoryObservation;

public final class WynncraftTerritoryApiClient {
    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.wynncraft.com/v3/guild/list/territory");
    private static final String SOURCE = "wynncraft-api:/v3/guild/list/territory";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final LongSupplier clock;

    public WynncraftTerritoryApiClient(HttpClient httpClient, URI endpoint, LongSupplier clock) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = java.util.Objects.requireNonNull(endpoint, "endpoint");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public static WynncraftTerritoryApiClient createDefault() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new WynncraftTerritoryApiClient(client, DEFAULT_ENDPOINT, System::currentTimeMillis);
    }

    public CompletableFuture<ObservationBatch> fetch(String currentGuildName) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "SyllyAddons-private/0.1")
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("Wynncraft territory API returned HTTP " + response.statusCode());
                    }
                    String apiVersion = response.headers().firstValue("Version").orElse("v3");
                    return parse(response.body(), currentGuildName, apiVersion, clock.getAsLong());
                });
    }

    ObservationBatch parse(String json, String currentGuildName, String apiVersion, long observedAtEpochMillis) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) throw new JsonParseException("Territory API root is not an object");

        Evidence exact = new Evidence(
                EvidenceKind.PUBLIC_EXACT,
                observedAtEpochMillis,
                SOURCE,
                apiVersion,
                "Public territory data; normally refreshed on the API's shared cache");
        Evidence delayedResources = new Evidence(
                EvidenceKind.PUBLIC_DELAYED,
                observedAtEpochMillis,
                SOURCE,
                apiVersion,
                "Public resource values may be delayed by the API resource refresh interval");

        Map<String, TerritoryObservation> territories = new HashMap<>();
        String detectedHq = null;

        for (Map.Entry<String, JsonElement> entry : rootElement.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            String territoryName = entry.getKey();
            JsonObject territory = entry.getValue().getAsJsonObject();
            JsonObject guild = objectOrNull(territory, "guild");

            TerritoryOwner owner = parseOwner(guild);
            ObservedValue<Long> acquired = parseAcquired(territory, exact);
            ObservedValue<Boolean> headquarters = booleanValue(territory, "hq", exact);
            ObservedValue<TerritoryBounds> bounds = parseBounds(territory, exact);
            ObservedValue<List<String>> links = parseLinks(territory, exact);
            ObservedValue<Map<ResourceType, ResourceBalance>> resources =
                    parseResources(territory, delayedResources);
            ObservedValue<TerritoryRating> treasury = parseRating(territory, "treasury", exact);
            ObservedValue<TerritoryRating> defences = parseRating(territory, "defences", exact);

            territories.put(
                    territoryName,
                    new TerritoryObservation(
                            territoryName,
                            ObservedValue.known(owner, exact),
                            acquired,
                            headquarters,
                            bounds,
                            links,
                            resources,
                            treasury,
                            null,
                            defences,
                            null,
                            null));

            if (guild != null
                    && currentGuildName != null
                    && currentGuildName.equals(stringOrEmpty(guild, "name"))) {
                String hq = stringOrEmpty(guild, "hq");
                if (!hq.isBlank()) detectedHq = hq;
            }
        }

        ObservedValue<String> hq = detectedHq == null ? null : ObservedValue.known(detectedHq, exact);
        return new ObservationBatch(observedAtEpochMillis, null, null, hq, null, territories);
    }

    private static TerritoryOwner parseOwner(JsonObject guild) {
        if (guild == null) return TerritoryOwner.unowned();
        return new TerritoryOwner(
                stringOrEmpty(guild, "uuid"), stringOrEmpty(guild, "name"), stringOrEmpty(guild, "prefix"));
    }

    private static ObservedValue<Long> parseAcquired(JsonObject territory, Evidence evidence) {
        String acquired = stringOrEmpty(territory, "acquired");
        if (acquired.isBlank()) return null;
        try {
            return ObservedValue.known(Instant.parse(acquired).toEpochMilli(), evidence);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static ObservedValue<Boolean> booleanValue(JsonObject object, String field, Evidence evidence) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        return ObservedValue.known(value.getAsBoolean(), evidence);
    }

    private static ObservedValue<TerritoryBounds> parseBounds(JsonObject territory, Evidence evidence) {
        JsonObject location = objectOrNull(territory, "location");
        if (location == null) return null;
        JsonArray start = arrayOrNull(location, "start");
        JsonArray end = arrayOrNull(location, "end");
        if (start == null || end == null || start.size() < 2 || end.size() < 2) return null;

        int startX = start.get(0).getAsInt();
        int startZ = start.get(1).getAsInt();
        int endX = end.get(0).getAsInt();
        int endZ = end.get(1).getAsInt();
        return ObservedValue.known(
                new TerritoryBounds(
                        Math.min(startX, endX), Math.min(startZ, endZ), Math.max(startX, endX), Math.max(startZ, endZ)),
                evidence);
    }

    private static ObservedValue<List<String>> parseLinks(JsonObject territory, Evidence evidence) {
        JsonArray links = arrayOrNull(territory, "links");
        if (links == null) return null;
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement link : links) {
            if (link.isJsonNull()) continue;
            String name = link.getAsString();
            if (seen.add(name)) values.add(name);
        }
        return ObservedValue.known(List.copyOf(values), evidence);
    }

    private static ObservedValue<Map<ResourceType, ResourceBalance>> parseResources(
            JsonObject territory, Evidence evidence) {
        JsonArray resources = arrayOrNull(territory, "resources");
        if (resources == null) return null;

        Map<ResourceType, ResourceBalance> values = new EnumMap<>(ResourceType.class);
        for (JsonElement element : resources) {
            if (!element.isJsonObject()) continue;
            JsonObject resource = element.getAsJsonObject();
            ResourceType type = parseResourceType(stringOrEmpty(resource, "type"));
            if (type == null) continue;
            long generation = longOrZero(resource, "generation");
            long stored = longOrZero(resource, "stored");
            long limit = longOrZero(resource, "limit");
            values.put(type, new ResourceBalance(generation, stored, limit));
        }
        return ObservedValue.known(Map.copyOf(values), evidence);
    }

    private static ObservedValue<TerritoryRating> parseRating(
            JsonObject territory, String field, Evidence evidence) {
        String value = stringOrEmpty(territory, field);
        if (value.isBlank()) return null;
        try {
            return ObservedValue.known(TerritoryRating.valueOf(value.toUpperCase()), evidence);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ResourceType parseResourceType(String value) {
        return switch (value.toUpperCase()) {
            case "EMERALD", "EMERALDS" -> ResourceType.EMERALDS;
            case "ORE" -> ResourceType.ORE;
            case "WOOD" -> ResourceType.WOOD;
            case "FISH" -> ResourceType.FISH;
            case "CROP", "CROPS" -> ResourceType.CROPS;
            default -> null;
        };
    }

    private static JsonObject objectOrNull(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray arrayOrNull(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String stringOrEmpty(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static long longOrZero(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? 0 : Math.max(0, value.getAsLong());
    }
}
