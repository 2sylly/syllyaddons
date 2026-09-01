package net.syllyaddons.recorder;

import com.wynntils.mc.event.ContainerSetContentEvent;
import com.wynntils.mc.event.ContainerSetSlotEvent;
import com.wynntils.mc.event.KeyMappingEvent;
import com.wynntils.models.territories.event.GuildWarQueuedEvent;
import com.wynntils.models.worlds.event.WorldStateEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.bus.api.SubscribeEvent;

final class RecorderWynntilsListener {
    private final RecorderStore store;

    RecorderWynntilsListener(RecorderStore store) {
        this.store = store;
    }

    @SubscribeEvent
    public void onContainerContent(ContainerSetContentEvent.Post event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("containerId", event.getContainerId());
        data.put("stateId", event.getStateId());
        data.put("items", RecordedData.items(event.getItems()));
        data.put("carriedItem", RecordedData.item(event.getCarriedItem()));
        store.record("wynntils_container_content", data);
    }

    @SubscribeEvent
    public void onContainerSlot(ContainerSetSlotEvent.Post event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("containerId", event.getContainerId());
        data.put("stateId", event.getStateId());
        data.put("slot", event.getSlot());
        data.put("item", RecordedData.item(event.getItemStack()));
        store.record("wynntils_container_slot", data);
    }

    @SubscribeEvent
    public void onKeyMapping(KeyMappingEvent event) {
        store.record("wynntils_input_action", Map.of(
                "key", event.getKey().getName(),
                "operation", event.getOperation().name()));
    }

    @SubscribeEvent
    public void onWarQueued(GuildWarQueuedEvent event) {
        var timer = event.getAttackTimer();
        store.record("wynntils_guild_war_queued", Map.of(
                "territory", timer.territoryName(),
                "timerSeconds", timer.asSeconds()));
    }

    @SubscribeEvent
    public void onWorldState(WorldStateEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("oldState", event.getOldState().name());
        data.put("newState", event.getNewState().name());
        data.put("worldName", event.getWorldName() == null ? "" : event.getWorldName());
        data.put("firstJoin", event.isFirstJoinWorld());
        store.record("wynntils_world_state", data);
    }
}
