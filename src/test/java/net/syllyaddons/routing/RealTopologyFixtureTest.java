package net.syllyaddons.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.syllyaddons.domain.TerritoryBounds;
import org.junit.jupiter.api.Test;

class RealTopologyFixtureTest {
    @Test
    void retainsConnectionOrderFromCapturedWynntils428Excerpt() throws Exception {
        JsonObject fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/track4/wynntils-4.2.8-karoc-excerpt.json")) {
            fixture = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<TerritoryNode> nodes = new ArrayList<>();
        for (JsonElement element : fixture.getAsJsonArray("territories")) {
            JsonObject territory = element.getAsJsonObject();
            JsonObject bounds = territory.getAsJsonObject("bounds");
            JsonArray linksJson = territory.getAsJsonArray("links");
            List<String> links = new ArrayList<>();
            linksJson.forEach(link -> links.add(link.getAsString()));
            nodes.add(new TerritoryNode(
                    territory.get("name").getAsString(),
                    "",
                    new TerritoryBounds(
                            bounds.get("minX").getAsInt(),
                            bounds.get("minZ").getAsInt(),
                            bounds.get("maxX").getAsInt(),
                            bounds.get("maxZ").getAsInt()),
                    links));
        }

        TerritoryGraph graph = TerritoryGraph.from(nodes);

        assertEquals(
                List.of("Llevigar", "Harnort Compound", "Shineridge Orc Camp"),
                graph.neighbors("Karoc Quarry"));
        assertTrue(graph.diagnostics().stream()
                .anyMatch(value -> value.type() == GraphDiagnosticType.UNKNOWN_LINK));
    }
}
