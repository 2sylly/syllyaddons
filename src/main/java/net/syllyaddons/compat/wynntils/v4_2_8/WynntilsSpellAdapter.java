package net.syllyaddons.compat.wynntils.v4_2_8;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.keybinds.KeyBindDefinition;
import com.wynntils.features.combat.QuickCastFeature;
import com.wynntils.models.spells.QueuedMeleeScheduler;
import com.wynntils.models.spells.type.CombatClickType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.syllyaddons.profile.InputDevice;
import net.syllyaddons.profile.NativeSpellBindingProvider;
import net.syllyaddons.profile.PhysicalInput;
import net.syllyaddons.profile.SpellBinding;
import net.syllyaddons.profile.SpellCastGateway;
import net.syllyaddons.profile.SpellCastResult;

/** Exact-version bridge to Wynntils' own validated Quick Cast path and current key mappings. */
public final class WynntilsSpellAdapter implements SpellCastGateway, NativeSpellBindingProvider {
    private static final List<List<CombatClickType>> SPELL_SEQUENCES = List.of(
            List.of(CombatClickType.PRIMARY, CombatClickType.SECONDARY, CombatClickType.PRIMARY),
            List.of(CombatClickType.PRIMARY, CombatClickType.PRIMARY, CombatClickType.PRIMARY),
            List.of(CombatClickType.PRIMARY, CombatClickType.SECONDARY, CombatClickType.SECONDARY),
            List.of(CombatClickType.PRIMARY, CombatClickType.PRIMARY, CombatClickType.SECONDARY));
    private static final List<KeyBindDefinition> SPELL_DEFINITIONS = List.of(
            KeyBindDefinition.CAST_FIRST_SPELL,
            KeyBindDefinition.CAST_SECOND_SPELL,
            KeyBindDefinition.CAST_THIRD_SPELL,
            KeyBindDefinition.CAST_FOURTH_SPELL);

    private final Method tryCastSpell;

    public WynntilsSpellAdapter() {
        try {
            tryCastSpell = QuickCastFeature.class.getDeclaredMethod("tryCastSpell", List.class, boolean.class);
            tryCastSpell.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Wynntils 4.2.8 Quick Cast bridge is unavailable", exception);
        }
    }

    @Override
    public SpellCastResult castSpell(int spellNumber) {
        if (spellNumber < 1 || spellNumber > 4) return SpellCastResult.INTEGRATION_ERROR;
        if (!QueuedMeleeScheduler.canHandleCombatInput()) return SpellCastResult.UNSAFE_CONTEXT;
        if (Models.SpellCaster.isBusy()) return SpellCastResult.BUSY_OR_REJECTED;

        try {
            QuickCastFeature feature = Managers.Feature.getFeatureInstance(QuickCastFeature.class);
            if (!feature.isEnabled()) return SpellCastResult.QUICK_CAST_DISABLED;
            boolean queued = (boolean) tryCastSpell.invoke(feature, SPELL_SEQUENCES.get(spellNumber - 1), true);
            return queued ? SpellCastResult.QUEUED : SpellCastResult.BUSY_OR_REJECTED;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return SpellCastResult.INTEGRATION_ERROR;
        }
    }

    @Override
    public List<SpellBinding> currentBindings() {
        List<SpellBinding> bindings = new ArrayList<>();
        for (int index = 0; index < SPELL_DEFINITIONS.size(); index++) {
            KeyMapping mapping = Managers.KeyBind.getKeyMapping(SPELL_DEFINITIONS.get(index).name());
            if (mapping == null) continue;
            InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
            if (key.getValue() < 0) continue;
            bindings.add(new SpellBinding(
                    new PhysicalInput(toDevice(key.getType()), key.getValue()), index + 1));
        }
        return List.copyOf(bindings);
    }

    private static InputDevice toDevice(InputConstants.Type type) {
        if (type == InputConstants.Type.MOUSE) return InputDevice.MOUSE;
        if (type == InputConstants.Type.SCANCODE) return InputDevice.SCANCODE;
        return InputDevice.KEYSYM;
    }
}
