package net.syllyaddons.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpellProfileTest {
    @Test
    void replacingAnInputOrSpellKeepsBindingsUnambiguous() {
        PhysicalInput q = new PhysicalInput(InputDevice.KEYSYM, 81);
        PhysicalInput e = new PhysicalInput(InputDevice.KEYSYM, 69);
        SpellProfile profile = new SpellProfile(
                "war", "War", List.of(new SpellBinding(q, 1), new SpellBinding(e, 2)));

        SpellProfile movedSpell = profile.withBinding(1, e);

        assertEquals(1, movedSpell.bindings().size());
        assertEquals(1, movedSpell.spellFor(e).orElseThrow());
        assertTrue(movedSpell.spellFor(q).isEmpty());
    }
}
