package net.syllyaddons.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MixinInventoryTest {
    @Test
    void mixinInventoryStaysMinimalAndOptional() throws Exception {
        try (var input = getClass().getResourceAsStream("/syllyaddons.mixins.json")) {
            var document = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(document.get("required").getAsBoolean());
            assertEquals(
                    List.of("AbstractMapScreenAccessor", "AbstractContainerScreenAccessor"),
                    document.getAsJsonArray("client").asList().stream().map(value -> value.getAsString()).toList());
        }
    }
}
