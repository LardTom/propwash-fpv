package de.lardtom.propwash.interact;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneVibrationsTest {
    private static final long FRAME_NANOS = Math.round(1.0E9 / 120.0);
    private static final List<DroneBuild> PRESETS = List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER,
            DroneBuild.LONG_RANGE);

    @Test
    void onlyArmedDronesAboveIdleVibrate() {
        assertFalse(DroneVibrations.flying(false, 1.0F));
        assertFalse(DroneVibrations.flying(false, 0.0F));
        assertFalse(DroneVibrations.flying(true, 0.0F));
        assertFalse(DroneVibrations.flying(true, DroneVibrations.MOTOR_THRESHOLD - 0.01F));
        assertTrue(DroneVibrations.flying(true, DroneVibrations.MOTOR_THRESHOLD));
        assertTrue(DroneVibrations.flying(true, 0.4F));
        assertFalse(DroneVibrations.flying(true, Float.NaN));
    }

    @Test
    void everyTenTicksLikeAGlidingPlayer() {
        assertEquals(10, DroneVibrations.INTERVAL_TICKS);
        assertEquals(10, IntStream.range(0, 100).filter(DroneVibrations::due).count());
        assertTrue(DroneVibrations.due(0));
        assertFalse(DroneVibrations.due(5));
        assertTrue(DroneVibrations.due(20));
    }

    @Test
    void everyPresetHoversAboveTheThresholdAndIdlesBelowIt() {
        for (DroneBuild build : PRESETS) {
            Measured m = measure(build);
            String name = build.frame().name();
            assertTrue(m.armed(), name + " nicht scharf");
            assertTrue(m.idle() < DroneVibrations.MOTOR_THRESHOLD * 0.6F, name + " Leerlauf " + m.idle());
            assertTrue(m.hover() > DroneVibrations.MOTOR_THRESHOLD * 1.5F, name + " Schweben " + m.hover());
            assertFalse(DroneVibrations.flying(true, m.idle()), name);
            assertTrue(DroneVibrations.flying(true, m.hover()), name);
        }
    }

    @Test
    void aDroneLeftAHairAboveTheGroundNeverHitsItAgain() {
        double floor = -59.0;
        for (double gap : new double[] {1.0E-9, 9.0E-7, 1.0E-5, 3.0E-4}) {
            Wake wake = wake(floor, gap);
            assertTrue(wake.reported() < 0.0, "Vanilla meldet bei " + gap + " m Abstand keine Bewegung nach unten");
            assertFalse(wake.moved(), "Vanilla setzt bei " + gap + " m Abstand die Drohne doch ab");
            double counted = DroneVibrations.fallMoved(wake.reported(), wake.fromY(), wake.toY());
            assertEquals(0.0, counted, "bei " + gap + " m Abstand zählt eine Fallhöhe, die die Drohne nie gefallen ist");
            assertFalse(hitsGround(0.0, counted), "bei " + gap + " m Abstand meldet die ruhende Drohne alle 10 Ticks hit_ground");
            assertTrue(hitsGround(0.0, (float) wake.reported()), "Ausgangslage: Vanilla allein meldet bei " + gap + " m hit_ground");
        }
    }

    @Test
    void aRealFallStillCountsAndHitsTheGround() {
        double floor = -59.0;
        for (double gap : new double[] {0.03, 0.5, 2.0}) {
            Wake wake = wake(floor, gap);
            assertTrue(wake.moved(), "Vanilla setzt bei " + gap + " m Abstand die Drohne nicht ab");
            double counted = DroneVibrations.fallMoved(wake.reported(), wake.fromY(), wake.toY());
            assertEquals(wake.reported(), counted, 1.0E-12);
            assertEquals(Math.max(-gap, -QuadcopterEntity.GRAVITY), counted, 1.0E-9);
        }
        assertTrue(hitsGround(0.5, DroneVibrations.fallMoved(-0.03, -59.0 + 0.03, -59.0)));
        assertEquals(-0.25, DroneVibrations.fallMoved(-0.25, Double.NaN, Double.NaN));
    }

    @Test
    void worldRuleIsOnByDefaultNeutralAndInEveryPreset() {
        RuleSpec spec = WorldRuleSpecs.DRONES_TRIGGER_SCULK;
        assertEquals("drones_trigger_sculk", spec.name());
        assertEquals(RuleSpec.Kind.BOOL, spec.kind());
        assertEquals(1, spec.defaultValue());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        assertFalse(spec.raceRelevant());
        assertEquals(spec, WorldRuleSpecs.byName("drones_trigger_sculk").orElseThrow());
        for (RulePreset preset : RulePreset.values()) {
            assertEquals(1, preset.value(spec), preset.id());
        }
    }

    @Test
    void worldRuleHasTextsInBothLanguages() throws IOException {
        RuleSpec spec = WorldRuleSpecs.DRONES_TRIGGER_SCULK;
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        assertEquals("Drohnen lösen Sculk aus", de.get(spec.translationKey()).getAsString());
        assertEquals("Drones trigger sculk", en.get(spec.translationKey()).getAsString());
        for (JsonObject texts : List.of(de, en)) {
            assertTrue(texts.has(spec.translationKey() + ".description"));
            assertFalse(texts.get(spec.translationKey() + ".description").getAsString().isBlank());
        }
    }

    private static JsonObject lang(String language) throws IOException {
        Path here = Path.of("").toAbsolutePath();
        Path root = Files.isDirectory(here.resolve("common/src/lang")) ? here : here.getParent();
        try (Reader reader = Files.newBufferedReader(root.resolve("common/src/lang/interact/" + language + ".json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private record Measured(boolean armed, float idle, float hover) {
    }

    private record Wake(double reported, double fromY, double toY, boolean moved) {
    }

    private static Wake wake(double floor, double gap) {
        VoxelShape ground = Shapes.block().move(0.0, floor - 1.0, 0.0);
        AABB box = new AABB(0.35, floor + gap, 0.35, 0.65, floor + gap + 0.12, 0.65);
        double requested = -QuadcopterEntity.GRAVITY;
        double collided = Shapes.collide(Direction.Axis.Y, box, List.of(ground), requested);
        double length = collided * collided;
        boolean moved = length > 1.0E-7 || requested * requested - length < 1.0E-7;
        double fromY = box.minY;
        return new Wake(collided, fromY, moved ? fromY + collided : fromY, moved);
    }

    private static boolean hitsGround(double fallDistance, double counted) {
        double fall = fallDistance;
        if (counted < 0.0) {
            fall -= (float) counted;
        }
        return fall > 0.0;
    }

    private static Measured measure(DroneBuild build) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 42L);
        sim.reset(0.0, 100.0, 0.0, 0.0);
        sim.setBatteryCharge(1.0);
        PilotInput input = new PilotInput();
        input.mode = StabMode.ANGLE;
        input.throttle = 0.0;
        input.armSwitch = false;
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        sim.step(0L, input, env, result);
        input.armSwitch = true;
        sim.step(0L, input, env, result);
        double rpmMax = sim.params().omegaMax * 60.0 / (2.0 * Math.PI);
        float idle = 0.0F;
        for (int i = 0; i < 60; i++) {
            sim.step(FRAME_NANOS, input, env, result);
            if (i >= 30) {
                idle += sent(sim.telemetry(), rpmMax) / 30.0F;
            }
        }
        float hover = 0.0F;
        for (int i = 0; i < 480; i++) {
            Telemetry t = sim.telemetry();
            input.throttle = Math.clamp(t.hoverThrottleStick - 0.08 * t.verticalSpeed, 0.0, 1.0);
            sim.step(FRAME_NANOS, input, env, result);
            if (i >= 240) {
                hover += sent(sim.telemetry(), rpmMax) / 240.0F;
            }
        }
        return new Measured(sim.telemetry().armed, idle, hover);
    }

    private static float sent(Telemetry t, double rpmMax) {
        float[] out = new float[4];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (Math.abs(t.motorRpm[i]) / rpmMax);
        }
        return StateValidation.averageMotor(StateValidation.packMotors(out[0], out[1], out[2], out[3]));
    }
}
