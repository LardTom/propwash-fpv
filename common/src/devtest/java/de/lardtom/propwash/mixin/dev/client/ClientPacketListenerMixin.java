package de.lardtom.propwash.mixin.dev.client;

import de.lardtom.propwash.audio.client.SoundPacketLog;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void propwash$logSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        SoundPacketLog.record(SoundPacketLog.Kind.SOUND, packet.getSound().value().location().toString(), new Vec3(packet.getX(), packet.getY(), packet.getZ()),
                packet.getPitch(), -1);
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void propwash$logEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        SoundPacketLog.record(SoundPacketLog.Kind.ENTITY_SOUND, packet.getSound().value().location().toString(), Vec3.ZERO, packet.getPitch(), packet.getId());
    }

    @Inject(method = "handleLevelEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void propwash$logLevelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        SoundPacketLog.record(SoundPacketLog.Kind.LEVEL_EVENT, Integer.toString(packet.getType()), Vec3.atCenterOf(packet.getPos()), 1.0F, -1);
    }

    @Inject(method = "handleExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void propwash$logExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        SoundPacketLog.record(SoundPacketLog.Kind.EXPLOSION, "explosion", packet.center(), 1.0F, -1);
    }
}
