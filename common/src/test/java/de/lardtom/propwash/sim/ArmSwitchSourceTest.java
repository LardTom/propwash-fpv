package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.client.ArmSwitchGuard;
import de.lardtom.propwash.input.ArmSourceWatch;
import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmSwitchSourceTest {
    private static final boolean[] NO_BUTTONS = new boolean[0];

    private static final class Rig {
        final FlatWorld world = FlatWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        final DeviceProfile profile = new DeviceProfile();
        final SwitchAssignment arm = SwitchAssignment.axisMicros(4, 1700, 2100);
        final SwitchEvaluator evaluator = new SwitchEvaluator();
        final ArmSourceWatch watch = new ArmSourceWatch();
        final ArmSwitchGuard guard = new ArmSwitchGuard();
        final float[] axes = new float[8];
        RadioSource source = RadioSource.JOYSTICK;
        boolean guarded = true;

        Rig() {
            this.profile.setBinding(SwitchFunction.ARM, this.arm);
            this.world.placeOnGround(0.0, 0.0, 0.0);
            this.world.run(0.1, 144.0);
            this.position(1000);
            this.watch.observe(this.source, "boxer", this.arm);
            this.guard.sync(this.watch.revision());
            this.frame();
        }

        void position(int micros) {
            this.axes[4] = ChannelMath.fromRangeMicros(micros);
        }

        boolean frame() {
            int bits = this.source == RadioSource.KEYBOARD ? 0 : this.evaluator.evaluate(this.profile, this.axes, NO_BUTTONS);
            this.watch.observe(this.source, this.source == RadioSource.KEYBOARD ? "" : "boxer",
                    this.source == RadioSource.KEYBOARD ? SwitchAssignment.none() : this.arm);
            if (this.guarded) {
                this.guard.check(this.watch.revision(), this.world.sim);
            }
            boolean on = SwitchEvaluator.isSet(bits, SwitchFunction.ARM);
            this.world.input.armSwitch = on;
            this.world.frame(0L);
            return on;
        }

        boolean armed() {
            return this.world.sim.telemetry().armed;
        }

        int blocked() {
            EventQueue q = this.world.sim.events();
            for (int e = 0; e < q.size(); e++) {
                if (q.type(e) == SimEventType.ARM_BLOCKED) {
                    return (int) q.value(e);
                }
            }
            return -1;
        }
    }

    @Test
    void invertingArmOnNeedsTheSwitchCycledOffAndOn() {
        Rig r = new Rig();
        assertFalse(r.armed());
        r.arm.invert = true;
        assertTrue(r.frame());
        assertFalse(r.armed());
        assertEquals(ArmBlocker.ARM_SWITCH_NOT_RESET.bit(), r.blocked());
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(r.world.sim.telemetry().armBlockers));
        assertTrue(r.frame());
        assertFalse(r.armed());
        r.position(2000);
        assertFalse(r.frame());
        assertFalse(r.armed());
        assertEquals(0, r.world.sim.telemetry().armBlockers);
        r.position(1000);
        assertTrue(r.frame());
        assertTrue(r.armed());
    }

    @Test
    void withoutTheGuardTheSameChangeWouldArm() {
        Rig r = new Rig();
        r.guarded = false;
        r.arm.invert = true;
        assertTrue(r.frame());
        assertTrue(r.armed());
    }

    @Test
    void movingTheRangeOntoTheSwitchDoesNotArm() {
        Rig r = new Rig();
        r.arm.setLowMicros(900);
        assertTrue(r.frame());
        assertFalse(r.armed());
        assertEquals(ArmBlocker.ARM_SWITCH_NOT_RESET.bit(), r.blocked());
    }

    @Test
    void connectingARadioWithArmOnDoesNotArm() {
        Rig r = new Rig();
        r.source = RadioSource.KEYBOARD;
        assertFalse(r.frame());
        r.position(2000);
        r.source = RadioSource.JOYSTICK;
        assertTrue(r.frame());
        assertFalse(r.armed());
        assertEquals(ArmBlocker.ARM_SWITCH_NOT_RESET.bit(), r.blocked());
        r.position(1000);
        r.frame();
        r.position(2000);
        r.frame();
        assertTrue(r.armed());
    }

    @Test
    void changesWhileArmedOnlyActThroughTheSwitchState() {
        Rig r = new Rig();
        r.position(2000);
        r.frame();
        assertTrue(r.armed());
        r.arm.setLowMicros(1600);
        assertTrue(r.frame());
        assertTrue(r.armed());
        assertEquals(-1, r.blocked());
        r.arm.invert = true;
        assertFalse(r.frame());
        assertFalse(r.armed());
        r.position(1000);
        assertTrue(r.frame());
        assertTrue(r.armed());
    }

    @Test
    void switchOffWhileTheSourceChangesArmsNormallyLater() {
        Rig r = new Rig();
        r.arm.setHighMicros(2000);
        assertFalse(r.frame());
        assertEquals(-1, r.blocked());
        r.position(2000);
        assertTrue(r.frame());
        assertTrue(r.armed());
    }

    @Test
    void requireArmSwitchResetIsIgnoredWhileArmed() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.placeOnGround(0.0, 0.0, 0.0);
        w.run(0.1, 144.0);
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        w.sim.requireArmSwitchReset();
        w.frame(0L);
        assertTrue(w.sim.telemetry().armed);
        w.input.armSwitch = false;
        w.frame(0L);
        assertFalse(w.sim.telemetry().armed);
        w.sim.requireArmSwitchReset();
        w.frame(0L);
        w.input.armSwitch = true;
        w.frame(0L);
        assertTrue(w.sim.telemetry().armed);
    }
}
