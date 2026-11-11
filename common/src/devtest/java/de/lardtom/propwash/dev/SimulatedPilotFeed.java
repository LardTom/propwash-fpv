package de.lardtom.propwash.dev;

import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdPalette;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.PilotLook;

public final class SimulatedPilotFeed {
    public static final OsdPreset PRESET = OsdPreset.RACE;
    public static final OsdPalette COLOR = OsdPalette.ORANGE;
    public static final SpeedUnit UNIT = SpeedUnit.MPH;
    public static final StickMode STICK_MODE = StickMode.MODE_1;
    public static final double LAP_SECONDS = 12.0;
    public static final long COUNTDOWN_NANOS = 3_000_000_000L;

    private SimulatedPilotFeed() {
    }

    public static float stickRoll(int tick) {
        return (float) Math.sin(tick / 20.0 * 2.0);
    }

    public static PilotFeed racing(int tick, int courseId, int[] gateIndices, int position) {
        OsdData d = base(tick);
        double t = tick / 20.0;
        int gates = gateIndices.length;
        int next = Math.floorMod(position, Math.max(1, gates));
        d.racing = true;
        d.lap = 1 + (int) (t / LAP_SECONDS);
        d.currentLapNanos = (long) ((t % LAP_SECONDS) * 1.0E9);
        d.lastLapNanos = d.lap > 1 ? (long) (LAP_SECONDS * 1.0E9) : -1L;
        d.bestLapNanos = d.lastLapNanos;
        d.nextGate = next;
        d.gateCount = gates;
        return PilotFeed.capture(d).withRace(courseId, LapTracker.Phase.RACING, 3, gates == 0 ? -1 : gateIndices[next], gates == 0 ? -1 : gateIndices[0],
                false, LapTracker.NONE, COUNTDOWN_NANOS, (long) (t * 1.0E9));
    }

    public static PilotFeed countdown(int tick, int courseId, int[] gateIndices, long remainingNanos) {
        OsdData d = base(tick);
        d.gateCount = gateIndices.length;
        return PilotFeed.capture(d).withRace(courseId, LapTracker.Phase.COUNTDOWN, 3, -1, gateIndices.length == 0 ? -1 : gateIndices[0], false,
                remainingNanos, COUNTDOWN_NANOS, Long.MAX_VALUE);
    }

    private static OsdData base(int tick) {
        OsdData d = new OsdData();
        double t = tick / 20.0;
        d.hasSim = true;
        d.armed = true;
        d.mode = StabMode.ANGLE;
        d.stickRoll = stickRoll(tick);
        d.stickPitch = (float) (0.6 * Math.cos(t * 1.3));
        d.stickYaw = (float) (0.4 * Math.sin(t * 0.7));
        d.stickThrottle = (float) (0.55 + 0.25 * Math.sin(t * 1.7));
        d.throttle = d.stickThrottle;
        d.cells = 6;
        d.capacityMah = 1300;
        d.chargeFraction = 0.82;
        d.packVoltage = 23.9;
        d.cellVoltage = 3.98;
        d.warnCellVoltage = 3.5;
        d.criticalCellVoltage = 3.3;
        d.mAhUsed = 234.0;
        d.currentA = 21.5;
        d.powerW = 514.0;
        d.speed = 12.0;
        d.altitude = 2.5;
        d.verticalSpeed = 0.0;
        d.radioConnected = true;
        d.linkStrength = 0.96;
        d.gpsKnown = true;
        d.gpsSatellites = 12;
        d.gpsFix = true;
        d.flightSeconds = 30.0 + t;
        d.armedSeconds = 30.0 + t;
        return d;
    }

    public static PilotLook look() {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(PRESET);
        config.color = COLOR.hex();
        config.slot(OsdElement.STICK_OVERLAY).enabled = true;
        config.slot(OsdElement.FLIGHT_TIME).enabled = true;
        config.slot(OsdElement.FLIGHT_PATH_MARKER).enabled = true;
        return PilotLook.of(config, UNIT, STICK_MODE);
    }
}
