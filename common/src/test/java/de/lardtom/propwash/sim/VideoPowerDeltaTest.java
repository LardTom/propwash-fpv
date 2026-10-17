package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPowerDeltaTest {
    private static final long FRAME_NANOS = 10_000_000L;

    private static DroneSim sim(DroneBuild build) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 42L);
        sim.reset(0.0, 64.0, 0.0, 0.0);
        sim.setBatteryCharge(1.0);
        return sim;
    }

    private static void fly(DroneSim sim, double seconds, boolean armed, double throttle) {
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        input.mode = StabMode.ANGLE;
        input.armSwitch = false;
        input.throttle = 0.0;
        env.groundDistance = Double.POSITIVE_INFINITY;
        int frames = (int) Math.round(seconds * 1.0E9 / FRAME_NANOS);
        for (int i = 0; i < frames; i++) {
            input.armSwitch = armed && i > 20;
            input.throttle = armed && i > 40 ? throttle : 0.0;
            sim.step(FRAME_NANOS, input, env, result);
        }
    }

    @Test
    void zeroDeltaIsBitIdentical() {
        DroneSim plain = sim(DroneBuild.FREESTYLE);
        DroneSim zero = sim(DroneBuild.FREESTYLE);
        zero.setVideoPowerDelta(0.0);
        fly(plain, 3.0, true, 0.45);
        fly(zero, 3.0, true, 0.45);
        Telemetry a = plain.telemetry();
        Telemetry b = zero.telemetry();
        assertEquals(Double.doubleToRawLongBits(a.batteryMahUsed), Double.doubleToRawLongBits(b.batteryMahUsed));
        assertEquals(Double.doubleToRawLongBits(a.packVoltage), Double.doubleToRawLongBits(b.packVoltage));
        assertEquals(Double.doubleToRawLongBits(a.totalCurrentA), Double.doubleToRawLongBits(b.totalCurrentA));
        assertEquals(Double.doubleToRawLongBits(a.speed), Double.doubleToRawLongBits(b.speed));
    }

    @Test
    void extraVideoPowerDrainsTheBattery() {
        DroneSim plain = sim(DroneBuild.FREESTYLE);
        DroneSim loud = sim(DroneBuild.FREESTYLE);
        loud.setVideoPowerDelta(6.0);
        fly(plain, 60.0, false, 0.0);
        fly(loud, 60.0, false, 0.0);
        double extra = loud.telemetry().batteryMahUsed - plain.telemetry().batteryMahUsed;
        double expected = 6.0 / plain.telemetry().packVoltage * 60.0 / 3.6;
        assertTrue(extra > expected * 0.7 && extra < expected * 1.3, "Mehrverbrauch " + extra + " mAh statt etwa " + expected);
        assertEquals(6.0, loud.videoPowerDelta(), 0.0);
        loud.setVideoPowerDelta(Double.NaN);
        assertEquals(0.0, loud.videoPowerDelta(), 0.0);
    }

    @Test
    void builtInVideoSupplyStaysAtThePartValue() {
        assertEquals(1.9, EnergyModel.videoSupplyW(VideoType.ANALOG_NANO), 0.0);
        assertEquals(3.5, EnergyModel.videoSupplyW(VideoType.ANALOG_RACE), 0.0);
        assertEquals(5.5, EnergyModel.videoSupplyW(VideoType.DIGITAL_LITE), 0.0);
        assertEquals(9.0, EnergyModel.videoSupplyW(VideoType.DIGITAL_HD), 0.0);
    }
}
