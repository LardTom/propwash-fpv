package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadeTest {
    @Test
    void buildChangesOnlyWhileDisarmedAndRederivePids() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.arm();
        assertFalse(w.sim.setBuild(DroneBuild.RACER));
        assertEquals(AirframeSpec.of(DroneBuild.FREESTYLE).buildKey, w.sim.params().buildKey);
        w.input.armSwitch = false;
        w.frame(0L);
        assertTrue(w.sim.setBuild(DroneBuild.RACER));
        assertEquals(AirframeSpec.of(DroneBuild.RACER).buildKey, w.sim.params().buildKey);
        assertEquals(AirframeSpec.of(DroneBuild.RACER).buildKey, w.sim.config().pid.derivedForBuild);
        assertEquals(33, w.sim.config().pid.roll.p);

        FlightControllerConfig custom = new FlightControllerConfig();
        custom.pid.source = PidSource.CUSTOM;
        custom.pid.roll.p = 77;
        DroneSim sim = new DroneSim(DroneBuild.WHOOP, custom, Tests.quietTuning(), 1L);
        assertEquals(77, sim.config().pid.roll.p);
        assertTrue(sim.setBuild(DroneBuild.CINEWHOOP));
        assertEquals(77, sim.config().pid.roll.p);
        assertEquals(PidSource.CUSTOM, sim.config().pid.source);
        FlightControllerConfig defaults = FlightControllerConfig.defaultsFor(AirframeSpec.of(DroneBuild.LONG_RANGE));
        assertEquals(55, defaults.pid.roll.p);
        assertEquals(PidSource.DERIVED, defaults.pid.source);
    }

    @Test
    void filterChangesWaitForDisarmButGainsApplyImmediately() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        FlightControllerConfig cfg = w.sim.config().copy();
        cfg.filters.pidLoop = PidLoopRate.HZ_500;
        cfg.pid.source = PidSource.CUSTOM;
        cfg.pid.roll.p = 60;
        w.sim.setConfig(cfg);
        assertEquals(1, w.sim.fc.divider);
        assertEquals(60 * PidAutoTune.P_SCALE, w.sim.fc.axes[FcAxes.ROLL].kp, 1e-12);
        w.input.armSwitch = false;
        w.frame(0L);
        assertEquals(4, w.sim.fc.divider);
        assertNotEquals(cfg, w.sim.config());
        cfg.pid.roll.p = 10;
        assertEquals(60, w.sim.config().pid.roll.p);
    }

    @Test
    void snapshotRoundTripReproducesState() {
        FlatWorld a = Tests.hovering(DroneBuild.RACER, new SimTuning(), 20.0, StabMode.ANGLE, 1.0);
        a.sim.body.setBodyRates(1.0, -2.0, 0.5);
        SimSnapshot snap = new SimSnapshot();
        a.sim.copyStateTo(snap);
        DroneSim b = new DroneSim(DroneBuild.RACER, new FlightControllerConfig(), new SimTuning(), 99L);
        b.copyStateFrom(snap);
        SimSnapshot back = new SimSnapshot();
        b.copyStateTo(back);
        assertEquals(snap.px, back.px);
        assertEquals(snap.qz, back.qz, 1.0e-15);
        assertEquals(snap.lx, back.lx);
        assertEquals(snap.soc, back.soc);
        assertEquals(snap.rngState, back.rngState);
        assertEquals(snap.armed, back.armed);
        assertEquals(snap.accumulatorNanos, back.accumulatorNanos);
        assertTrue(b.telemetry().armed);
        SimSnapshot copy = new SimSnapshot();
        snap.copyTo(copy);
        assertEquals(snap.rotorOmega[2], copy.rotorOmega[2]);
    }

    @Test
    void teleportMovesStateAndOptionallyKeepsVelocity() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        w.sim.body.vx = 3.0;
        w.sim.teleport(100.0, 40.0, -5.0, true);
        Vector3d pos = new Vector3d();
        Vector3d vel = new Vector3d();
        w.sim.getPosition(pos);
        w.sim.getVelocity(vel);
        assertEquals(100.0, pos.x);
        assertEquals(40.0, pos.y);
        assertEquals(-5.0, pos.z);
        assertEquals(3.0, vel.x);
        w.sim.teleport(0.0, 40.0, 0.0, false);
        w.sim.getVelocity(vel);
        assertEquals(0.0, vel.length());
        Quaterniond q = new Quaterniond();
        w.sim.getAttitude(q);
        assertEquals(1.0, q.lengthSquared(), 1e-12);
        w.x = 0.0;
        w.y = 40.0;
        w.z = 0.0;
        w.frame(1.0 / 60.0);
        assertEquals(1.0, w.result.orientation.lengthSquared(), 1e-12);
        assertTrue(w.result.armed);
        assertTrue(Math.abs(w.result.dy) < 0.05);
    }

    @Test
    void propsOutReversesSpinAndYawColumnConsistently() {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.propsOut = true;
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, cfg, Tests.quietTuning(), 42L));
        w.place(0.0, 30.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(30.0);
        double dt = 1.0 / 144.0;
        for (int f = 0; f < 432; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.mode = StabMode.ACRO;
        w.input.yaw = RateCurves.stickFor(RatesType.ACTUAL, cfg.rates.yaw, 300.0);
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.run(0.4, 144.0);
        assertEquals(300.0, Tests.yawRateDps(w.sim), 15.0);
    }

    @Test
    void batteryChargeAndTelemetryCopy() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 1L);
        sim.setBatteryCharge(0.25);
        Telemetry t = sim.telemetry();
        assertEquals(0.25, t.chargeFraction, 1e-12);
        assertEquals(0.75 * 1300.0, t.mAhUsed, 1e-9);
        assertEquals(CellCurve.ocv(sim.params().chemistry, 0.25), t.cellVoltageResting, 1e-12);
        Telemetry copy = new Telemetry();
        t.copyTo(copy);
        assertEquals(t.mAhUsed, copy.mAhUsed);
        assertEquals(t.hoverThrottleStick, copy.hoverThrottleStick);
        assertTrue(copy.hoverThrottleStick > 0.2 && copy.hoverThrottleStick < 0.3);
        sim.forceDisarm(DisarmReason.NONE);
        assertEquals(DisarmReason.NONE, sim.telemetry().lastDisarmReason);
    }

    @Test
    void waterDisarmsAndLavaDisarmsImmediately() {
        FlatWorld water = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        water.env.fluid = Fluid.WATER;
        water.run(0.2, 100.0);
        assertTrue(water.sim.telemetry().armed);
        water.run(0.2, 100.0);
        assertFalse(water.sim.telemetry().armed);
        assertEquals(DisarmReason.FLUID, water.sim.telemetry().lastDisarmReason);
        FlatWorld lava = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        lava.env.fluid = Fluid.LAVA;
        lava.frame(0L);
        assertFalse(lava.sim.telemetry().armed);
        assertTrue(ArmBlocker.IN_FLUID.isSet(lava.sim.telemetry().armBlockers));
    }
}
