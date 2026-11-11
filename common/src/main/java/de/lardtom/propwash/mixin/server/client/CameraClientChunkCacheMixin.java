package de.lardtom.propwash.mixin.server.client;

import de.lardtom.propwash.capture.CaptureRules;
import de.lardtom.propwash.capture.client.CaptureStorage;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class CameraClientChunkCacheMixin implements CaptureStorage {
    @Unique
    private int propwash$vanillaRange = 2;
    @Unique
    private int propwash$cameraStorage;

    @Shadow
    public abstract void updateViewRadius(int viewRange);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void propwash$rememberRange(ClientLevel level, int serverChunkRadius, CallbackInfo ci) {
        this.propwash$vanillaRange = serverChunkRadius;
    }

    @ModifyVariable(method = "updateViewRadius", at = @At("HEAD"), argsOnly = true)
    private int propwash$cameraRange(int viewRange) {
        this.propwash$vanillaRange = viewRange;
        return Math.max(viewRange, this.propwash$cameraStorage - CaptureRules.STORAGE_MARGIN);
    }

    @Override
    public void propwash$cameraStorage(int storageRadius) {
        int radius = Math.max(0, storageRadius);
        if (radius == this.propwash$cameraStorage) {
            return;
        }
        this.propwash$cameraStorage = radius;
        this.updateViewRadius(this.propwash$vanillaRange);
    }

    @Override
    public int propwash$cameraStorage() {
        return this.propwash$cameraStorage;
    }

    @Override
    public int propwash$vanillaRange() {
        return this.propwash$vanillaRange;
    }
}
