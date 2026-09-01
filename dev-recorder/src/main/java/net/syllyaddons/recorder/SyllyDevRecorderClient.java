package net.syllyaddons.recorder;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.WynntilsMod;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SyllyDevRecorderClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("syllydevrecorder");
    private RecorderStore store;

    @Override
    public void onInitializeClient() {
        Path sessions = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("sylly-dev-recorder")
                .resolve("sessions");
        store = new RecorderStore(sessions, LOGGER);
        registerScreens();

        KeyMapping toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.syllydevrecorder.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) toggle(client);
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            WynntilsMod.registerEventListener(new RecorderWynntilsListener(store));
            LOGGER.info("Sylly Dev Recorder is loaded but OFF; press F9 to start a local session");
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> store.close());
    }

    private void registerScreens() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenMouseEvents.beforeMouseClick(screen).register((ignored, event) -> {
                Map<String, Object> data = new LinkedHashMap<>(RecordedData.screen(screen));
                data.put("x", event.x());
                data.put("y", event.y());
                data.put("button", event.button());
                data.put("modifiers", event.modifiers());
                data.put("hovered", RecordedData.slotAt(screen, event.x(), event.y()));
                store.record("screen_mouse_click", data);
            });
            ScreenEvents.remove(screen).register(ignored ->
                    store.record("screen_close", RecordedData.screen(screen)));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> recordScreen(screen));
    }

    private void toggle(Minecraft client) {
        if (store.recording()) {
            Path finished = store.stop();
            message(client, "Recording stopped: " + finished.getFileName());
            LOGGER.info("Local dev recording saved to {}", finished);
            return;
        }

        try {
            Path started = store.start(sessionMetadata(client));
            message(client, "Recording started (F9 to stop): " + started.getFileName());
            LOGGER.info("Local dev recording started at {}", started);
            if (client.screen != null) recordScreen(client.screen);
        } catch (IOException exception) {
            LOGGER.error("Could not start local dev recording", exception);
            message(client, "Could not start recording; check latest.log");
        }
    }

    private void recordScreen(Screen screen) {
        Map<String, Object> data = new LinkedHashMap<>(RecordedData.screen(screen));
        if (screen instanceof AbstractContainerScreen<?> container) {
            data.put("items", RecordedData.items(container.getMenu().getItems()));
        }
        store.record("screen_open_or_init", data);
    }

    private static Map<String, Object> sessionMetadata(Minecraft client) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recorderVersion", version("syllydevrecorder"));
        data.put("minecraftVersion", version("minecraft"));
        data.put("fabricLoaderVersion", version("fabricloader"));
        data.put("wynntilsVersion", version("wynntils"));
        data.put("syllyAddonsVersion", version("syllyaddons"));
        data.put("windowWidth", client.getWindow().getWidth());
        data.put("windowHeight", client.getWindow().getHeight());
        data.put("guiWidth", client.getWindow().getGuiScaledWidth());
        data.put("guiHeight", client.getWindow().getGuiScaledHeight());
        data.put("privacy", "Local only; no chat messages, typed characters, or raw GUI key presses are recorded");
        return data;
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("not-installed");
    }

    private static void message(Minecraft client, String text) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[SyllyRecorder] " + text), false);
        }
    }
}
