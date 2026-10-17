package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.EnergyModel;

public final class LocatorBeeperPower {
    public static final double BUZZER_W = 0.25;
    public static final double TICK_SECONDS = 0.05;
    public static final double SYNC_STEP = 0.001;

    private LocatorBeeperPower() {
    }

    public static double watts(DroneBuild build) {
        return EnergyModel.standbyW(build.video()) + BUZZER_W;
    }

    public static double nominalVolts(BatteryType battery) {
        BatteryType.Chemistry chemistry = battery.chemistry;
        return Math.max(1, battery.cells) * 0.5 * (chemistry.fullCellVoltage + chemistry.emptyCellVoltage);
    }

    public static double drainPerSecond(DroneBuild build) {
        BatteryType battery = build.battery();
        double amps = watts(build) / nominalVolts(battery);
        double capacityAh = Math.max(1, battery.capacityMah) / 1000.0;
        return amps / capacityAh / 3600.0;
    }

    public static double drainPerTick(DroneBuild build) {
        return drainPerSecond(build) * TICK_SECONDS;
    }

    public static double secondsFromFull(DroneBuild build) {
        return 1.0 / drainPerSecond(build);
    }
}
