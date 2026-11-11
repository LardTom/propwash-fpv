package de.lardtom.propwash.sim;

import com.google.gson.Gson;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoArmTest {
    private static final double FPS = 144.0;
    private static final double LOW = 0.0;
    private static final double HIGH = 0.4;

    private static FlatWorld armedOnGround() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.placeOnGround(0.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.input.throttle = LOW;
        w.run(0.1, FPS);
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.autoArmed());
        return w;
    }

    private static void frames(FlatWorld w, int count) {
        for (int i = 0; i < count; i++) {
            w.frame(1.0 / FPS);
        }
    }

    private static int blocked(FlatWorld w) {
        EventQueue q = w.sim.events();
        for (int e = 0; e < q.size(); e++) {
            if (q.type(e) == SimEventType.ARM_BLOCKED) {
                return (int) q.value(e);
            }
        }
        return -1;
    }

    private static boolean armedEvent(FlatWorld w) {
        EventQueue q = w.sim.events();
        for (int e = 0; e < q.size(); e++) {
            if (q.type(e) == SimEventType.ARMED) {
                return true;
            }
        }
        return false;
    }

    private static List<OsdWarnings.Warning> osd(FlatWorld w) {
        OsdData data = new OsdData();
        data.active = true;
        data.radioConnected = true;
        data.linkStrength = 1.0;
        data.applyTelemetry(w.sim.telemetry());
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.collect(data, out);
        return out;
    }

    private static void place(FlatWorld w, float tiltDeg) {
        SimSnapshot snapshot = new SimSnapshot();
        w.sim.copyStateTo(snapshot);
        double py = w.groundY - w.sim.params().hitboxFootOffset;
        Quaternionf q = new Quaternionf().rotationX((float) Math.toRadians(tiltDeg));
        snapshot.px = 0.0;
        snapshot.py = py;
        snapshot.pz = 0.0;
        snapshot.vx = 0.0;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.qw = q.w;
        snapshot.qx = q.x;
        snapshot.qy = q.y;
        snapshot.qz = q.z;
        w.sim.copyStateFrom(snapshot);
        w.x = 0.0;
        w.y = py;
        w.z = 0.0;
    }

    private static void resetHome(FlatWorld w, boolean autoArm) {
        place(w, 0.0F);
        w.sim.homeReset(autoArm);
    }

    @Test
    void resetWithArmStillOnWaitsForThrottleDownAndArmsBySelf() {
        FlatWorld w = armedOnGround();
        w.input.throttle = HIGH;
        frames(w, 60);
        assertTrue(w.sim.telemetry().armed);
        assertTrue(w.y > w.groundY - w.sim.params().hitboxFootOffset + 0.05, "die Drohne fliegt vor dem Reset");
        resetHome(w, true);
        assertTrue(w.sim.autoArmPending());
        assertFalse(w.sim.autoArmed());
        for (int i = 0; i < 200; i++) {
            w.frame(1.0 / FPS);
            assertEquals(-1, blocked(w), "kein Sperr-Ereignis, solange gewartet wird");
            assertFalse(w.sim.telemetry().armed, "mit Gas oben bleibt die Drohne entschärft");
        }
        Telemetry t = w.sim.telemetry();
        assertTrue(t.autoArmPending);
        assertTrue(ArmBlocker.THROTTLE_NOT_LOW.isSet(t.armBlockers));
        assertFalse(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(t.armBlockers), "der Schalter muss nicht erst aus");
        List<OsdWarnings.Warning> warnings = osd(w);
        assertTrue(warnings.contains(OsdWarnings.Warning.AUTO_ARM), warnings.toString());
        assertFalse(warnings.contains(OsdWarnings.Warning.ARM_THROTTLE), warnings.toString());
        w.input.throttle = LOW;
        w.frame(1.0 / FPS);
        assertTrue(w.sim.telemetry().armed, "Gas unten: Auto-Arm");
        assertTrue(armedEvent(w));
        assertTrue(w.sim.autoArmed());
        assertFalse(w.sim.autoArmPending());
        assertFalse(w.sim.telemetry().autoArmPending);
        assertFalse(osd(w).contains(OsdWarnings.Warning.AUTO_ARM));
        w.input.armSwitch = false;
        w.frame(1.0 / FPS);
        assertFalse(w.sim.telemetry().armed, "Schalter aus entschärft wie immer");
        assertFalse(w.sim.autoArmed());
        w.input.armSwitch = true;
        w.frame(1.0 / FPS);
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.autoArmed(), "von Hand scharf ist kein Auto-Arm");
    }

    @Test
    void resetWithThrottleAlreadyDownArmsInTheNextFrame() {
        FlatWorld w = armedOnGround();
        resetHome(w, true);
        assertTrue(w.sim.autoArmPending());
        w.frame(1.0 / FPS);
        assertTrue(w.sim.telemetry().armed);
        assertTrue(w.sim.autoArmed());
    }

    @Test
    void settingOffKeepsTheOldSwitchCycle() {
        FlatWorld w = armedOnGround();
        resetHome(w, false);
        assertFalse(w.sim.autoArmPending());
        w.frame(1.0 / FPS);
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(blocked(w)));
        frames(w, 150);
        assertFalse(w.sim.telemetry().armed, "ohne Auto-Arm bleibt die Drohne trotz Gas unten entschärft");
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
        assertFalse(w.sim.telemetry().autoArmPending);
        List<OsdWarnings.Warning> warnings = osd(w);
        assertTrue(warnings.contains(OsdWarnings.Warning.ARM_SWITCH), warnings.toString());
        assertFalse(warnings.contains(OsdWarnings.Warning.AUTO_ARM), warnings.toString());
        w.input.armSwitch = false;
        w.frame(1.0 / FPS);
        w.input.armSwitch = true;
        w.frame(1.0 / FPS);
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.autoArmed());
    }

    @Test
    void noAutoArmAfterACrash() {
        FlatWorld w = armedOnGround();
        w.sim.forceDisarm(DisarmReason.CRASH);
        assertFalse(w.sim.autoArmPending());
        frames(w, 400);
        assertFalse(w.sim.telemetry().armed, "nach einem Absturz kein Auto-Arm");
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
        assertFalse(osd(w).contains(OsdWarnings.Warning.AUTO_ARM));
    }

    @Test
    void resetAfterACrashWaitsForTheCooldownAndThenArms() {
        FlatWorld w = armedOnGround();
        w.sim.forceDisarm(DisarmReason.CRASH);
        w.frame(1.0 / FPS);
        resetHome(w, true);
        w.frame(1.0 / FPS);
        assertFalse(w.sim.telemetry().armed, "Absturz-Pause gilt weiter");
        assertTrue(ArmBlocker.CRASH_COOLDOWN.isSet(w.sim.telemetry().armBlockers));
        assertTrue(w.sim.autoArmPending());
        assertEquals(-1, blocked(w));
        int frames = 0;
        while (!w.sim.telemetry().armed && frames < 400) {
            w.frame(1.0 / FPS);
            frames++;
        }
        assertTrue(w.sim.telemetry().armed, "nach der Absturz-Pause armt der Reset von selbst");
        assertTrue(frames >= Math.floor(Arming.CRASH_COOLDOWN_S * FPS) - 3, "nicht vor Ende der Pause: " + frames);
        assertTrue(w.sim.autoArmed());
    }

    @Test
    void noAutoArmOnSpawnWithTheSwitchOn() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.placeOnGround(0.0, 0.0, 0.0);
        w.input.throttle = LOW;
        w.input.armSwitch = true;
        w.frame(1.0 / FPS);
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(blocked(w)));
        frames(w, 200);
        assertFalse(w.sim.telemetry().armed, "beim Spawn mit Schalter an kein Auto-Arm");
        assertFalse(w.sim.telemetry().autoArmPending);
    }

    @Test
    void noAutoArmAfterGpsRescueDisarms() {
        FlatWorld w = armedOnGround();
        w.sim.forceDisarm(DisarmReason.FORCED);
        frames(w, 200);
        assertFalse(w.sim.telemetry().armed, "nach der Landung der GPS-Rettung kein Auto-Arm");
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
    }

    @Test
    void activeRescueCancelsAPendingAutoArm() {
        FlatWorld w = armedOnGround();
        w.input.throttle = HIGH;
        resetHome(w, true);
        w.frame(1.0 / FPS);
        assertTrue(w.sim.autoArmPending());
        w.sim.cancelAutoArm();
        w.input.throttle = LOW;
        frames(w, 100);
        assertFalse(w.sim.telemetry().armed);
        assertFalse(w.sim.autoArmPending());
    }

    @Test
    void switchOffWhileWaitingFallsBackToTheNormalArmEdge() {
        FlatWorld w = armedOnGround();
        w.input.throttle = HIGH;
        resetHome(w, true);
        frames(w, 10);
        w.input.armSwitch = false;
        w.frame(1.0 / FPS);
        assertFalse(w.sim.autoArmPending());
        w.input.armSwitch = true;
        w.frame(1.0 / FPS);
        assertTrue(ArmBlocker.THROTTLE_NOT_LOW.isSet(blocked(w)), "Scharfschalten mit Gas oben wird wie immer abgelehnt");
        w.input.throttle = LOW;
        frames(w, 50);
        assertFalse(w.sim.telemetry().armed, "danach gilt wieder: Schalter aus und an");
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
    }

    @Test
    void emptyPackStillBlocksAndNeedsTheSwitchCycled() {
        FlatWorld w = armedOnGround();
        w.input.throttle = HIGH;
        resetHome(w, true);
        w.sim.setBatteryCharge(0.0);
        frames(w, 20);
        assertFalse(w.sim.telemetry().armed);
        assertTrue(w.sim.autoArmPending());
        w.input.throttle = LOW;
        w.frame(1.0 / FPS);
        int mask = blocked(w);
        assertTrue(ArmBlocker.PACK_DEPLETED.isSet(mask), "Akku leer sperrt weiter: " + mask);
        assertFalse(w.sim.telemetry().armed);
        assertFalse(w.sim.autoArmPending());
        w.sim.setBatteryCharge(1.0);
        frames(w, 50);
        assertFalse(w.sim.telemetry().armed, "nach einer Sperre wie bisher: Schalter aus und an");
        w.input.armSwitch = false;
        w.frame(1.0 / FPS);
        w.input.armSwitch = true;
        w.frame(1.0 / FPS);
        assertTrue(w.sim.telemetry().armed);
    }

    @Test
    void tiltAndLostLinkStillBlock() {
        FlatWorld w = armedOnGround();
        resetHome(w, true);
        place(w, 60.0F);
        w.frame(0L);
        int mask = blocked(w);
        assertTrue(ArmBlocker.TILTED.isSet(mask), "Neigung sperrt weiter: " + mask);
        assertFalse(w.sim.telemetry().armed);

        FlatWorld link = armedOnGround();
        resetHome(link, true);
        link.input.linkOk = false;
        link.frame(0L);
        assertTrue(ArmBlocker.FAILSAFE.isSet(blocked(link)), "ohne Funkverbindung kein Auto-Arm");
        assertFalse(link.sim.telemetry().armed);

        FlatWorld water = armedOnGround();
        resetHome(water, true);
        water.env.fluid = Fluid.WATER;
        water.frame(0L);
        assertTrue(ArmBlocker.IN_FLUID.isSet(blocked(water)), "im Wasser kein Auto-Arm");
        assertFalse(water.sim.telemetry().armed);
    }

    @Test
    void threeDModeWaitsForCenteredThrottle() {
        FlatWorld w = armedOnGround();
        resetHome(w, true);
        w.input.mode3d = true;
        w.input.throttle = 0.0;
        frames(w, 20);
        assertFalse(w.sim.telemetry().armed);
        assertTrue(osd(w).contains(OsdWarnings.Warning.AUTO_ARM_3D));
        w.input.throttle = 0.5;
        w.frame(1.0 / FPS);
        assertTrue(w.sim.telemetry().armed);
        assertTrue(w.sim.autoArmed());
    }

    @Test
    void changedArmSourceCancelsTheWait() {
        FlatWorld w = armedOnGround();
        w.input.throttle = HIGH;
        resetHome(w, true);
        frames(w, 5);
        w.sim.requireArmSwitchReset();
        w.input.throttle = LOW;
        frames(w, 50);
        assertFalse(w.sim.telemetry().armed, "neue Arm-Quelle verlangt wie bisher Schalter aus und an");
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
    }

    @Test
    void settingDefaultsToOnAndTravelsWithTheSwitchSettings() {
        assertTrue(new InputConfig().autoArmAfterReset);
        assertTrue(new Gson().fromJson("{\"mode3dUnlocked\":true}", InputConfig.class).autoArmAfterReset, "alte Konfiguration: Standard an");
        InputConfig off = new InputConfig();
        off.autoArmAfterReset = false;
        assertFalse(off.copy().autoArmAfterReset);
        assertFalse(new Gson().fromJson(new Gson().toJson(off), InputConfig.class).autoArmAfterReset);
        InputConfig target = new InputConfig();
        target.mergeSwitchesFrom(off);
        assertFalse(target.autoArmAfterReset);
        InputConfig receiverOnly = new InputConfig();
        receiverOnly.mergeReceiverFrom(off);
        assertTrue(receiverOnly.autoArmAfterReset);
    }
}
