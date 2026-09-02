package net.syllyaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.syllyaddons.client.gui.AttackAdvisorOverlayController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final client-side guard immediately before a container click is sent to the server. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenClickMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void syllyaddons$guardAttackClick(
            Slot slot,
            int slotId,
            int mouseButton,
            ClickType clickType,
            CallbackInfo callback) {
        if (AttackAdvisorOverlayController.interceptSlotClick(
                (AbstractContainerScreen<?>) (Object) this, slot, mouseButton, clickType)) {
            callback.cancel();
        }
    }
}
