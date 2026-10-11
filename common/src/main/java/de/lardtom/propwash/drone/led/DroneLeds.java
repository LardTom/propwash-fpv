package de.lardtom.propwash.drone.led;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;

import java.util.Optional;

public final class DroneLeds {
    private DroneLeds() {
    }

    public static LedSettings settings(QuadcopterEntity drone) {
        return LedSettings.of(drone.tune().values());
    }

    public static Optional<AccessoryType> strip(QuadcopterEntity drone) {
        return LedSettings.strip(drone.build());
    }

    public static LedAnimator.Input input(QuadcopterEntity drone, double seconds) {
        boolean powered = LedAnimator.powered(drone.hasBattery(), drone.batteryCharge(), drone.ledsOff());
        return new LedAnimator.Input(drone.isArmed(), powered, drone.batteryCharge(), seconds);
    }

    public static boolean switchedOff(QuadcopterEntity drone) {
        return drone.ledsOff() && strip(drone).isPresent();
    }

    public static int luminance(QuadcopterEntity drone) {
        Optional<AccessoryType> strip = strip(drone);
        if (strip.isEmpty()) {
            return 0;
        }
        return LedAnimator.luminance(settings(drone), strip.get().ledCount, input(drone, drone.tickCount / 20.0));
    }
}
