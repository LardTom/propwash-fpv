package de.lardtom.propwash.mixin.race.client;

import de.lardtom.propwash.race.client.ReplaySession;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
    private float fov;

    @Unique
    private final Vector3d propwash$replayPosition = new Vector3d();

    @Unique
    private final Quaternionf propwash$replayRotation = new Quaternionf();

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void propwash$replayPose(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ReplaySession.INSTANCE.cameraPose(this.propwash$replayPosition, this.propwash$replayRotation)) {
            return;
        }
        this.setPosition(new Vec3(this.propwash$replayPosition.x, this.propwash$replayPosition.y, this.propwash$replayPosition.z));
        this.rotation.set(this.propwash$replayRotation);
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
    private void propwash$replayFov(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ReplaySession.INSTANCE.active()) {
            this.fov = ReplaySession.INSTANCE.fov(this.fov);
        }
    }
}
