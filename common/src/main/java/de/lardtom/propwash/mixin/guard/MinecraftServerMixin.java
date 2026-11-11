package de.lardtom.propwash.mixin.guard;

import de.lardtom.propwash.guard.GuardServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void propwash$checkAfterReload(Collection<String> packs, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        cir.getReturnValue().thenRunAsync(() -> GuardServer.reloaded(server), server);
    }
}
