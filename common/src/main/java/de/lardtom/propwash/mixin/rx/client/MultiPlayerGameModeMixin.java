package de.lardtom.propwash.mixin.rx.client;

import de.lardtom.propwash.rx.client.RxClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void propwash$sneakUseRxBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (RxClient.sneakUse(player, hand, hitResult)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
