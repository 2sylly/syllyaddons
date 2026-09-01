package net.syllyaddons.recorder.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int syllyrecorder$getLeftPos();

    @Accessor("topPos")
    int syllyrecorder$getTopPos();
}
