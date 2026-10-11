package de.lardtom.propwash.mixin.join.client;

import de.lardtom.propwash.join.client.JoinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ClientPacketListener connection;

    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void propwash$keepInactiveItemsOut(ItemStack clicked, int slot, CallbackInfo ci) {
        if (JoinClient.blockCreativeAdd(this.minecraft, this.connection, clicked, slot)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleCreativeModeItemDrop", at = @At("HEAD"), cancellable = true)
    private void propwash$keepInactiveDropsOut(ItemStack clicked, CallbackInfo ci) {
        if (JoinClient.blockCreativeDrop(this.minecraft, clicked)) {
            ci.cancel();
        }
    }
}
