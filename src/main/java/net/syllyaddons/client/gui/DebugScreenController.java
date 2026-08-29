package net.syllyaddons.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.syllyaddons.observation.ObservedStateRepository;
import org.lwjgl.glfw.GLFW;

/** Registers the deliberately small Track 1 inspection UI. */
public final class DebugScreenController {
    private DebugScreenController() {}

    public static void register(ObservedStateRepository repository) {
        KeyMapping openDebugScreen = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.syllyaddons.open_data_status",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDebugScreen.consumeClick()) {
                client.setScreen(new ObservedStateDebugScreen(repository));
            }
        });
    }
}
