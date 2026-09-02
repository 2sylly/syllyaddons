package net.syllyaddons.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.syllyaddons.client.gui.AttackAdvisorOverlayController;
import net.syllyaddons.compat.AttackPacketGuardMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Last-resort attack guard at the outgoing packet boundary. */
@Mixin(Connection.class)
public abstract class ConnectionSendMixin implements AttackPacketGuardMarker {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void syllyaddons$guardAttackPacket(
            Packet<?> packet,
            ChannelFutureListener listener,
            boolean flush,
            CallbackInfo callback) {
        if (packet instanceof ServerboundContainerClickPacket click
                && AttackAdvisorOverlayController.interceptContainerPacket(click)) {
            callback.cancel();
        }
    }
}
