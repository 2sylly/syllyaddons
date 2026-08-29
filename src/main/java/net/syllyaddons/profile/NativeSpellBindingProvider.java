package net.syllyaddons.profile;

import java.util.List;

@FunctionalInterface
public interface NativeSpellBindingProvider {
    List<SpellBinding> currentBindings();
}
