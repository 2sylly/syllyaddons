package net.syllyaddons.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.syllyaddons.profile.SpellProfileService;
import org.lwjgl.glfw.GLFW;

public final class SpellProfileController {
    private SpellProfileController() {}

    public static void register(Supplier<SpellProfileService> serviceSupplier) {
        KeyMapping openPicker = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.syllyaddons.open_spell_profiles",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPicker.consumeClick()) {
                SpellProfileService service = serviceSupplier.get();
                if (service != null) {
                    service.refreshCharacterCatalog();
                    client.setScreen(new SpellProfilePickerScreen(service));
                }
            }
        });
    }
}
