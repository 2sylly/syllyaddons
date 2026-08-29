package net.syllyaddons.client.profile;

import com.mojang.blaze3d.platform.InputConstants;
import net.syllyaddons.profile.InputDevice;
import net.syllyaddons.profile.PhysicalInput;

public final class InputDisplayName {
    private InputDisplayName() {}

    public static String display(PhysicalInput input) {
        return type(input.device()).getOrCreate(input.code()).getDisplayName().getString();
    }

    public static InputConstants.Type type(InputDevice device) {
        return switch (device) {
            case KEYSYM -> InputConstants.Type.KEYSYM;
            case SCANCODE -> InputConstants.Type.SCANCODE;
            case MOUSE -> InputConstants.Type.MOUSE;
        };
    }
}
