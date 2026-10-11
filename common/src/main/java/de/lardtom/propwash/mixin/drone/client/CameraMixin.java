package de.lardtom.propwash.mixin.drone.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.drone.client.FpvEyeGuard;
import de.lardtom.propwash.drone.client.FpvView;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    private boolean detached;

    @Shadow
    private int matrixPropertiesDirty;

    @Shadow
    private @Nullable Level level;

    @Shadow
    private Vec3 position;

    @Shadow
    private float fov;

    @Unique
    private boolean propwash$fpv;

    @Unique
    private final Vector3d propwash$position = new Vector3d();

    @Unique
    private final Quaternionf propwash$orientation = new Quaternionf();

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "update", at = @At("HEAD"))
    private void propwash$frameStart(DeltaTracker deltaTracker, CallbackInfo ci) {
        this.propwash$fpv = false;
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void propwash$fpvPose(float partialTicks, CallbackInfo ci) {
        if (!FpvView.cameraPose(this.propwash$position, this.propwash$orientation)) {
            return;
        }
        this.propwash$fpv = true;
        this.setPosition(new Vec3(this.propwash$position.x, this.propwash$position.y, this.propwash$position.z));
        this.rotation.set(this.propwash$orientation);
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
        float horizontal = (float) Math.sqrt(this.forwards.x * this.forwards.x + this.forwards.z * this.forwards.z);
        this.yRot = (float) Math.toDegrees(Math.atan2(-this.forwards.x, this.forwards.z));
        this.xRot = (float) Math.toDegrees(-Math.atan2(this.forwards.y, horizontal));
        this.detached = false;
        this.matrixPropertiesDirty |= 3;
    }

    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;fov:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void propwash$fpvClearance(DeltaTracker deltaTracker, CallbackInfo ci) {
        Level world = this.level;
        if (!this.propwash$fpv || world == null) {
            FpvEyeGuard.INSTANCE.idle();
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        float aspect = window.getHeight() > 0 ? (float) window.getWidth() / window.getHeight() : 16.0F / 9.0F;
        this.propwash$position.set(this.position.x, this.position.y, this.position.z);
        if (FpvEyeGuard.INSTANCE.apply(world, this.propwash$position, FpvView.anchor(), this.forwards, this.up, this.left, this.fov, aspect)) {
            this.setPosition(new Vec3(this.propwash$position.x, this.propwash$position.y, this.propwash$position.z));
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void propwash$steadyFpvImage(CameraRenderState cameraState, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!this.propwash$fpv) {
            return;
        }
        cameraState.entityRenderState.hurtTime = -1.0F;
        cameraState.entityRenderState.bob = 0.0F;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 0)
    private float propwash$fpvNear(float zNear) {
        return FpvEyeGuard.INSTANCE.near(zNear);
    }
}
