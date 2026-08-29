package net.syllyaddons.observation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.observation.ObservationBatch;
import org.junit.jupiter.api.Test;

class WynncraftTerritoryApiClientTest {
    @Test
    void parsesCurrentPublicTerritoryShapeAndNormalizesBounds() {
        String json = """
                {
                  "Ragni": {
                    "guild": {
                      "uuid": "guild-uuid",
                      "name": "Spectral Cabbage",
                      "prefix": "SPC",
                      "hq": "Detlas"
                    },
                    "acquired": "2026-04-23T14:33:23.733Z",
                    "hq": false,
                    "resources": [
                      {"type": "EMERALD", "generation": 3600, "stored": 1800, "limit": 9000}
                    ],
                    "links": ["Katoa Ranch", "Maltic"],
                    "treasury": "MEDIUM",
                    "defences": "HIGH",
                    "location": {"start": [-955, -1415], "end": [-756, -1748]}
                  }
                }
                """;
        WynncraftTerritoryApiClient client = new WynncraftTerritoryApiClient(
                HttpClient.newHttpClient(), URI.create("https://example.invalid"), () -> 1234L);

        ObservationBatch batch = client.parse(json, "Spectral Cabbage", "v3.7.2", 1234L);
        var ragni = batch.territories().get("Ragni");

        assertEquals("Detlas", batch.hqTerritory().value());
        assertEquals("Spectral Cabbage", ragni.owner().value().guildName());
        assertEquals(-1748, ragni.bounds().value().minZ());
        assertEquals(3600, ragni.resources().value().get(ResourceType.EMERALDS).generationPerHour());
        assertTrue(ragni.links().value().contains("Maltic"));
    }
}
