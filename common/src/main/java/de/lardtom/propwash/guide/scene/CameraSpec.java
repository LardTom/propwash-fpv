package de.lardtom.propwash.guide.scene;

import java.util.Objects;

public record CameraSpec(ScenePos target, float yaw, float pitch, float zoom) {
    public static final float MIN_PITCH = -10.0F;
    public static final float MAX_PITCH = 89.0F;
    public static final float MIN_ZOOM = 0.35F;
    public static final float MAX_ZOOM = 6.0F;

    public CameraSpec {
        Objects.requireNonNull(target, "target");
        pitch = Math.clamp(pitch, MIN_PITCH, MAX_PITCH);
        zoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    public CameraSpec lerp(CameraSpec to, float t) {
        return new CameraSpec(this.target.lerp(to.target, t), this.yaw + wrapDegrees(to.yaw - this.yaw) * t,
                this.pitch + (to.pitch - this.pitch) * t, (float) Math.exp(Math.log(this.zoom) + (Math.log(to.zoom) - Math.log(this.zoom)) * t));
    }

    public CameraSpec withOffsets(float yawOffset, float pitchOffset, float zoomFactor) {
        return new CameraSpec(this.target, this.yaw + yawOffset, this.pitch + pitchOffset, this.zoom * zoomFactor);
    }

    public static float wrapDegrees(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }
}
