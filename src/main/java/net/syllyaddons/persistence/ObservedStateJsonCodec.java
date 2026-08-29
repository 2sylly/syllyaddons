package net.syllyaddons.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.Objects;
import net.syllyaddons.domain.ObservedState;

public final class ObservedStateJsonCodec {
    private final Gson gson;

    public ObservedStateJsonCodec() {
        gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    }

    public String encode(ObservedState state) {
        return gson.toJson(Objects.requireNonNull(state, "state"));
    }

    public ObservedState decode(String json) {
        if (json == null || json.isBlank()) throw new JsonParseException("Observed-state JSON is empty");

        ObservedState state = gson.fromJson(json, ObservedState.class);
        if (state == null) throw new JsonParseException("Observed-state JSON contains null");
        if (state.schemaVersion() != ObservedState.CURRENT_SCHEMA_VERSION) {
            throw new JsonParseException("Unsupported observed-state schema: " + state.schemaVersion());
        }
        return state;
    }
}
