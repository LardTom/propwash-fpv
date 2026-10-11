package de.lardtom.propwash.video.record;

import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.BatteryType;

public final class CamPower {
    public static final float FULL = 1.0F;
    public static final float EMPTY_THRESHOLD = 0.002F;
    public static final double USB_CHARGE_SECONDS = 120.0;
    public static final double USB_EFFICIENCY = 0.85;
    public static final float PACK_RESERVE = 0.15F;

    private CamPower() {
    }

    public static double drainPerSecond(ActionCamSpec spec, int height) {
        if (!spec.ownBattery()) {
            return 0.0;
        }
        return spec.recordWatts(height) / (spec.ownBatteryWh() * 3600.0);
    }

    public static float drain(float charge, ActionCamSpec spec, int height, double seconds) {
        if (!spec.ownBattery() || seconds <= 0.0) {
            return clamp(charge);
        }
        return clamp((float) (charge - drainPerSecond(spec, height) * seconds));
    }

    public static double secondsLeft(float charge, ActionCamSpec spec, int height) {
        if (!spec.ownBattery()) {
            return Double.POSITIVE_INFINITY;
        }
        double perSecond = drainPerSecond(spec, height);
        return perSecond <= 0.0 ? Double.POSITIVE_INFINITY : Math.max(0.0, clamp(charge) - EMPTY_THRESHOLD) / perSecond;
    }

    public static boolean empty(float charge, ActionCamSpec spec) {
        return spec.ownBattery() && charge <= EMPTY_THRESHOLD;
    }

    public static double packWattHours(BatteryType pack) {
        double nominal = (pack.chemistry.fullCellVoltage + pack.chemistry.emptyCellVoltage) * 0.5;
        return pack.cells * nominal * pack.capacityMah / 1000.0;
    }

    public static float[] usbCharge(float cameraCharge, float packCharge, ActionCamSpec spec, BatteryType pack, double seconds) {
        float camera = clamp(cameraCharge);
        float flight = clamp(packCharge);
        if (!spec.ownBattery() || seconds <= 0.0 || camera >= FULL || flight <= PACK_RESERVE) {
            return new float[]{camera, flight};
        }
        double gain = Math.min(FULL - camera, seconds / USB_CHARGE_SECONDS);
        double costPerGain = spec.ownBatteryWh() / USB_EFFICIENCY / packWattHours(pack);
        double cost = gain * costPerGain;
        double available = flight - PACK_RESERVE;
        if (cost > available) {
            gain = available / costPerGain;
            cost = available;
        }
        return new float[]{clamp((float) (camera + gain)), clamp((float) (flight - cost))};
    }

    public static float clamp(float charge) {
        if (!Float.isFinite(charge)) {
            return FULL;
        }
        return Math.max(0.0F, Math.min(FULL, charge));
    }
}
