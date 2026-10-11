package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.creative.CreativeLimits;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.drone.TuneMapping;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeRobustnessTest {
    private static final double FPS = 120.0;

    private static DroneBuild installed(DroneBuild preset) {
        return new DroneBuild(preset.frame(), StackType.CREATIVE_FC, preset.motor(), preset.prop(), preset.video(), preset.battery());
    }

    private static double sample(CreativeParam param, Random random) {
        double min = param.min();
        double max = param.max();
        if (param.toggle()) {
            return random.nextBoolean() ? 1.0 : 0.0;
        }
        double u = random.nextDouble();
        double value;
        if (min > 0.0 && max / min > 20.0) {
            value = Math.exp(Math.log(min) + u * (Math.log(max) - Math.log(min)));
        } else {
            value = min + u * (max - min);
        }
        return param.clamp(value);
    }

    private static CreativeSpec randomSpec(Random random) {
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        if (random.nextBoolean()) {
            DroneBuild template = DroneBuild.PRESETS.get(random.nextInt(DroneBuild.PRESETS.size()));
            base.put(PartKind.FRAME, template.frame().key());
            base.put(PartKind.STACK, template.stack().key());
            base.put(PartKind.MOTOR, template.motor().key());
            base.put(PartKind.PROP, template.prop().key());
            base.put(PartKind.BATTERY, template.battery().key());
        }
        TreeMap<String, Double> values = new TreeMap<>();
        double chance = random.nextDouble() < 0.2 ? 1.0 : 0.35;
        boolean arms = random.nextDouble() < 0.25;
        for (CreativeParam param : CreativeParam.values()) {
            boolean arm = CreativeParam.ARMS.contains(param);
            if (arm ? arms : random.nextDouble() < chance) {
                values.put(param.key(), sample(param, random));
            }
        }
        return CreativeLimits.sanitize(new CreativeSpec(base, values)).spec();
    }

    private static List<CreativeSpec> corners() {
        List<CreativeSpec> specs = new ArrayList<>();
        TreeMap<String, Double> low = new TreeMap<>();
        TreeMap<String, Double> high = new TreeMap<>();
        for (CreativeParam param : CreativeParam.values()) {
            low.put(param.key(), param.min());
            high.put(param.key(), param.max());
        }
        specs.add(new CreativeSpec(Map.of(), low));
        specs.add(new CreativeSpec(Map.of(), high));
        TreeMap<String, Double> rocket = new TreeMap<>();
        rocket.put(CreativeParam.FRAME_MASS.key(), CreativeParam.FRAME_MASS.min());
        rocket.put(CreativeParam.BATTERY_MASS.key(), CreativeParam.BATTERY_MASS.min());
        rocket.put(CreativeParam.MOTOR_MASS.key(), CreativeParam.MOTOR_MASS.min());
        rocket.put(CreativeParam.PROP_MASS.key(), CreativeParam.PROP_MASS.min());
        rocket.put(CreativeParam.MOTOR_KV.key(), CreativeParam.MOTOR_KV.max());
        rocket.put(CreativeParam.BATTERY_CELLS.key(), CreativeParam.BATTERY_CELLS.max());
        rocket.put(CreativeParam.MOTOR_MAX_CURRENT.key(), CreativeParam.MOTOR_MAX_CURRENT.max());
        rocket.put(CreativeParam.ESC_CURRENT.key(), CreativeParam.ESC_CURRENT.max());
        rocket.put(CreativeParam.BATTERY_C_RATING.key(), CreativeParam.BATTERY_C_RATING.max());
        rocket.put(CreativeParam.PROP_THRUST_COEFFICIENT.key(), CreativeParam.PROP_THRUST_COEFFICIENT.max());
        rocket.put(CreativeParam.INERTIA_ROLL.key(), CreativeParam.INERTIA_ROLL.min());
        rocket.put(CreativeParam.INERTIA_PITCH.key(), CreativeParam.INERTIA_PITCH.min());
        rocket.put(CreativeParam.INERTIA_YAW.key(), CreativeParam.INERTIA_YAW.min());
        specs.add(new CreativeSpec(Map.of(), rocket));
        TreeMap<String, Double> brick = new TreeMap<>();
        brick.put(CreativeParam.FRAME_MASS.key(), CreativeParam.FRAME_MASS.max());
        brick.put(CreativeParam.BATTERY_MASS.key(), CreativeParam.BATTERY_MASS.max());
        brick.put(CreativeParam.PROP_DIAMETER.key(), CreativeParam.PROP_DIAMETER.min());
        brick.put(CreativeParam.MOTOR_KV.key(), CreativeParam.MOTOR_KV.min());
        brick.put(CreativeParam.INERTIA_ROLL.key(), CreativeParam.INERTIA_ROLL.max());
        brick.put(CreativeParam.INERTIA_PITCH.key(), CreativeParam.INERTIA_PITCH.max());
        brick.put(CreativeParam.INERTIA_YAW.key(), CreativeParam.INERTIA_YAW.max());
        specs.add(new CreativeSpec(Map.of(), brick));
        return specs;
    }

    private static void assertFiniteSpec(AirframeSpec spec, String what) throws IllegalAccessException {
        for (Field field : AirframeSpec.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != double.class) {
                continue;
            }
            double value = field.getDouble(spec);
            assertTrue(Double.isFinite(value), what + ": " + field.getName() + " = " + value);
        }
    }

    private static boolean finite(DroneSim sim) {
        RigidBody b = sim.body;
        double sum = b.px + b.py + b.pz + b.vx + b.vy + b.vz + b.qw + b.qx + b.qy + b.qz + b.lx + b.ly + b.lz + sim.power.busVoltage
                + sim.power.soc;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            sum += sim.power.omega[i];
        }
        return Double.isFinite(sum);
    }

    private static void fly(DroneBuild build, long seed, String what) throws IllegalAccessException {
        AirframeSpec params = AirframeSpec.of(build);
        assertFiniteSpec(params, what);
        assertTrue(params.massKg > 0.0 && params.inertiaXx > 0.0 && params.inertiaYy > 0.0 && params.inertiaZz > 0.0, what);
        DroneSim sim = new DroneSim(params, TuneMapping.toConfig(Map.of(), params), Tests.quietTuning(), seed);
        FlatWorld world = new FlatWorld(sim);
        world.place(0.0, 80.0, 0.0, 0.0);
        world.input.mode = StabMode.ANGLE;
        world.input.throttle = 0.0;
        world.arm();
        double dt = 1.0 / FPS;
        double[][] phases = {{0.6, 0.0, 0.0, StabMode.ANGLE.ordinal()}, {0.5, 1.0, 0.0, StabMode.ACRO.ordinal()},
                {0.5, 0.8, -1.0, StabMode.ACRO.ordinal()}, {0.4, 1.0, 1.0, StabMode.ANGLE.ordinal()}, {0.6, 0.0, 0.0, StabMode.ANGLE.ordinal()}};
        for (double[] phase : phases) {
            world.input.mode = StabMode.values()[(int) phase[3]];
            for (int frame = 0; frame < (int) (phase[0] * FPS); frame++) {
                world.input.throttle = phase[1] == 0.0 && phase[2] == 0.0 ? sim.telemetry().hoverThrottleStick : phase[1];
                world.input.roll = phase[2];
                world.input.pitch = -0.5 * phase[2];
                world.input.yaw = 0.3 * phase[2];
                world.frame(dt);
                assertTrue(finite(sim), what + " nach Frame " + frame);
                assertFalse(sim.events().contains(SimEventType.SIM_FAULT), what + ": SIM_FAULT");
            }
        }
    }

    @Test
    void randomEditsFlyWithoutFaults() throws IllegalAccessException {
        Random random = new Random(20261005L);
        for (int n = 0; n < 160; n++) {
            DroneBuild preset = DroneBuild.PRESETS.get(random.nextInt(DroneBuild.PRESETS.size()));
            CreativeSpec spec = randomSpec(random);
            fly(installed(preset).withCreative(spec), n, "Fall " + n + " " + spec);
        }
    }

    @Test
    void extremeCornersFlyWithoutFaults() throws IllegalAccessException {
        int n = 0;
        for (CreativeSpec spec : corners()) {
            for (DroneBuild preset : DroneBuild.PRESETS) {
                fly(installed(preset).withCreative(spec), n++, "Ecke " + preset.frame().key() + " " + spec);
            }
        }
    }
}
