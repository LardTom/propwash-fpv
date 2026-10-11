package de.lardtom.propwash.video.record.path;

public record PathKeyframe(int tick, double x, double y, double z, float yaw, float pitch, float roll) {
    public CameraAngles angles() {
        return new CameraAngles(this.yaw, this.pitch, this.roll);
    }
}
