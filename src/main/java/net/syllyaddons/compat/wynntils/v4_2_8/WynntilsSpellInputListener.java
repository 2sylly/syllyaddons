package net.syllyaddons.compat.wynntils.v4_2_8;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.components.Models;
import com.wynntils.mc.event.KeyInputEvent;
import com.wynntils.mc.event.KeyMappingEvent;
import com.wynntils.models.worlds.event.WorldStateEvent;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.syllyaddons.profile.InputDevice;
import net.syllyaddons.profile.PhysicalInput;
import net.syllyaddons.profile.SpellProfileService;
import net.syllyaddons.profile.SpellCastResult;
import org.lwjgl.glfw.GLFW;

/** Claims configured physical inputs before native mappings so a profile press can queue at most one spell. */
public final class WynntilsSpellInputListener {
    private final SpellProfileService profiles;
    private final Set<Integer> heldMouseButtons = new HashSet<>();
    private long lastNoticeAt;

    public WynntilsSpellInputListener(SpellProfileService profiles) {
        this.profiles = java.util.Objects.requireNonNull(profiles, "profiles");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardInput(KeyInputEvent event) {
        if (!safeGameplayContext()) return;

        PhysicalInput matched = matchingKeyboardInput(event);
        if (matched == null) return;

        event.setCanceled(true);
        if (event.getAction() == GLFW.GLFW_PRESS) {
            OptionalInt spell = profiles.spellForInput(matched);
            spell.ifPresent(this::cast);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyMapping(KeyMappingEvent event) {
        InputConstants.Key key = event.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() < 0) return;
        int button = key.getValue();
        if (event.getOperation() == KeyMappingEvent.Operation.UNSET) heldMouseButtons.remove(button);
        if (!safeGameplayContext()) return;

        PhysicalInput input = new PhysicalInput(InputDevice.MOUSE, button);
        OptionalInt spell = profiles.spellForInput(input);
        if (spell.isEmpty()) return;

        event.setCanceled(true);
        if (event.getOperation() == KeyMappingEvent.Operation.SET && heldMouseButtons.add(button)) {
            cast(spell.getAsInt());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldStateChanged(WorldStateEvent event) {
        heldMouseButtons.clear();
        if (!Models.WorldState.onWorld()) profiles.clearSession();
    }

    private PhysicalInput matchingKeyboardInput(KeyInputEvent event) {
        if (event.getKey() >= 0) {
            PhysicalInput key = new PhysicalInput(InputDevice.KEYSYM, event.getKey());
            if (profiles.spellForInput(key).isPresent()) return key;
        }
        if (event.getScanCode() >= 0) {
            PhysicalInput scan = new PhysicalInput(InputDevice.SCANCODE, event.getScanCode());
            if (profiles.spellForInput(scan).isPresent()) return scan;
        }
        return null;
    }

    private void cast(int spellNumber) {
        SpellCastResult result = profiles.castSpell(spellNumber);
        if (result != SpellCastResult.QUICK_CAST_DISABLED && result != SpellCastResult.INTEGRATION_ERROR) return;

        long now = System.currentTimeMillis();
        if (now - lastNoticeAt < 2_000) return;
        lastNoticeAt = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String message = result == SpellCastResult.QUICK_CAST_DISABLED
                ? "Sylly Addons: enable Wynntils Quick Cast to use spell profiles"
                : "Sylly Addons: spell integration failed closed; restart Minecraft";
        minecraft.player.displayClientMessage(Component.literal(message), true);
    }

    private static boolean safeGameplayContext() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.screen == null
                && minecraft.getOverlay() == null
                && Models.WorldState.onWorld();
    }
}
