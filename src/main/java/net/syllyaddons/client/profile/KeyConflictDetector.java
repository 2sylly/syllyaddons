package net.syllyaddons.client.profile;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.syllyaddons.profile.InputDevice;
import net.syllyaddons.profile.PhysicalInput;
import net.syllyaddons.profile.SpellProfile;

public final class KeyConflictDetector {
    private KeyConflictDetector() {}

    public static List<InputConflict> detect(SpellProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) return List.of();

        List<InputConflict> conflicts = new ArrayList<>();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
            if (key.getValue() < 0) continue;
            PhysicalInput input = new PhysicalInput(toDevice(key.getType()), key.getValue());
            if (profile.spellFor(input).isEmpty()) continue;
            conflicts.add(new InputConflict(input, Component.translatable(mapping.getName()).getString()));
        }
        return List.copyOf(conflicts);
    }

    private static InputDevice toDevice(InputConstants.Type type) {
        if (type == InputConstants.Type.MOUSE) return InputDevice.MOUSE;
        if (type == InputConstants.Type.SCANCODE) return InputDevice.SCANCODE;
        return InputDevice.KEYSYM;
    }
}
