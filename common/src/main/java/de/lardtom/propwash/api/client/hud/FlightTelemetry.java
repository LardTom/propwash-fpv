package de.lardtom.propwash.api.client.hud;

import java.util.Optional;

public record FlightTelemetry(boolean armed, String mode, Optional<String> assistLabel, double x, double y, double z, double speed,
                              double verticalSpeed, double headingDeg, double pitchDeg, double rollDeg, double throttle, double batteryVoltage,
                              double cellVoltage, double batteryCharge, boolean hasHome, double homeX, double homeY, double homeZ, boolean gpsFix,
                              int satellites, double linkQuality, double videoSignal, double flightSeconds, double stickRoll, double stickPitch,
                              double stickYaw, double stickThrottle) {
    public double homeDistance() {
        return this.hasHome ? Math.hypot(this.homeX - this.x, this.homeZ - this.z) : Double.NaN;
    }

    public double homeHeight() {
        return this.hasHome ? this.y - this.homeY : Double.NaN;
    }

    public double homeBearingDeg() {
        if (!this.hasHome) {
            return Double.NaN;
        }
        double bearing = Math.toDegrees(Math.atan2(this.homeX - this.x, -(this.homeZ - this.z)));
        return bearing < 0.0 ? bearing + 360.0 : bearing;
    }
}
