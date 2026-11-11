package de.lardtom.propwash.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.capture.CaptureServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class CameraTrackedEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @Shadow
    protected abstract int getEffectiveRange();

    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private boolean propwash$cameraArea(boolean visible, @Local(argsOnly = true) ServerPlayer player) {
        return visible || CaptureServer.seesEntity(player, this.entity, this.getEffectiveRange());
    }
}
