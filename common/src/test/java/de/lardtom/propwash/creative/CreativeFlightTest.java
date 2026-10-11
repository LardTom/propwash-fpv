package de.lardtom.propwash.creative;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.sim.AirframeSpec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeFlightTest {
    private static final Set<String> NOT_PHYSICS = Set.of("build", "buildKey", "buildable", "assemblable", "escMinCells", "escMaxCells",
            "escOvervoltage");

    static DroneBuild creative(DroneBuild installed) {
        return new DroneBuild(installed.frame(), StackType.CREATIVE_FC, installed.motor(), installed.prop(), installed.video(),
                installed.battery(), installed.accessories());
    }

    static CreativeSpec template(DroneBuild preset) {
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        base.put(PartKind.FRAME, preset.frame().key());
        base.put(PartKind.STACK, preset.stack().key());
        base.put(PartKind.MOTOR, preset.motor().key());
        base.put(PartKind.PROP, preset.prop().key());
        base.put(PartKind.BATTERY, preset.battery().key());
        return new CreativeSpec(base, Map.of());
    }

    private static void assertSamePhysics(AirframeSpec expected, AirframeSpec actual, String what) throws IllegalAccessException {
        for (Field field : AirframeSpec.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || NOT_PHYSICS.contains(field.getName())) {
                continue;
            }
            Object a = field.get(expected);
            Object b = field.get(actual);
            assertEquals(a, b, what + ": " + field.getName());
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertEquals(expected.motorX(i), actual.motorX(i), what + " motorX " + i);
            assertEquals(expected.motorY(i), actual.motorY(i), what + " motorY " + i);
            assertEquals(expected.motorZ(i), actual.motorZ(i), what + " motorZ " + i);
            assertEquals(expected.spin(i), actual.spin(i), what + " spin " + i);
        }
        for (int k = 0; k < expected.hullCount(); k++) {
            assertEquals(expected.hullX(k), actual.hullX(k), what + " hullX " + k);
            assertEquals(expected.hullY(k), actual.hullY(k), what + " hullY " + k);
            assertEquals(expected.hullZ(k), actual.hullZ(k), what + " hullZ " + k);
        }
    }

    @Test
    void creativeControllerFitsEveryFrame() {
        for (FrameType frame : FrameType.all()) {
            assertTrue(StackType.CREATIVE_FC.fits(frame), frame.key());
        }
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild build = creative(preset);
            assertFalse(build.problems().contains(DroneBuild.Issue.STACK_MOUNT), preset.frame().key());
            assertTrue(build.isCreative());
            assertFalse(build.edited());
            assertSame(build, build.flight());
        }
        assertFalse(DroneBuild.FREESTYLE.isCreative());
    }

    @Test
    void specOnlyStaysOnCreativeController() {
        CreativeSpec spec = CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 2600.0);
        DroneBuild build = creative(DroneBuild.FREESTYLE).withCreative(spec);
        assertEquals(spec, build.creative());
        assertEquals(CreativeSpec.EMPTY, DroneBuild.FREESTYLE.withCreative(spec).creative());
        DroneBuild swapped = PartCategory.STACK.with(build, StackType.STACK_30);
        assertEquals(CreativeSpec.EMPTY, swapped.creative());
        assertEquals(spec, PartCategory.PROP.with(build, DroneBuild.RACER.prop()).creative());
        assertEquals(spec, build.withBattery(DroneBuild.RACER.battery()).creative());
        assertEquals(spec, build.withAccessory(AccessoryType.LED_STRIP).creative());
    }

    @Test
    void templateOfAPresetFliesExactlyLikeThePreset() throws IllegalAccessException {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild installed = new DroneBuild(DroneBuild.WHOOP.frame(), StackType.CREATIVE_FC, DroneBuild.WHOOP.motor(),
                    DroneBuild.WHOOP.prop(), preset.video(), DroneBuild.WHOOP.battery());
            DroneBuild build = installed.withCreative(template(preset));
            assertTrue(build.edited());
            AirframeSpec expected = AirframeSpec.of(preset);
            AirframeSpec actual = AirframeSpec.of(build);
            assertSamePhysics(expected, actual, preset.frame().key());
            assertEquals(1, actual.escMinCells);
            assertEquals(CreativeFlight.STACK_MAX_CELLS, actual.escMaxCells);
            assertSame(build, actual.build);
            assertNotEquals(expected.buildKey, actual.buildKey);
        }
    }

    @Test
    void valuesEqualToTheBaseChangeNothing() throws IllegalAccessException {
        DroneBuild plain = creative(DroneBuild.RACER);
        TreeMap<String, Double> values = new TreeMap<>();
        for (CreativeParam param : CreativeParam.values()) {
            if (!param.auto()) {
                values.put(param.key(), CreativeFlight.baseValue(plain, param));
            }
        }
        DroneBuild build = plain.withCreative(new CreativeSpec(Map.of(), values));
        assertSamePhysics(AirframeSpec.of(plain), AirframeSpec.of(build), "racer");
    }

    private record Effect(CreativeParam param, double factor, ToDoubleFunction<AirframeSpec> derived) {
    }

    @Test
    void everyParameterReachesTheFlightModel() {
        DroneBuild plain = creative(DroneBuild.FREESTYLE);
        AirframeSpec before = AirframeSpec.of(plain);
        List<Effect> effects = List.of(
                new Effect(CreativeParam.FRAME_MASS, 1.5, s -> s.massKg),
                new Effect(CreativeParam.MOTOR_MASS, 1.5, s -> s.massKg),
                new Effect(CreativeParam.PROP_MASS, 1.5, s -> s.rotorInertia),
                new Effect(CreativeParam.BATTERY_MASS, 1.5, s -> s.massKg),
                new Effect(CreativeParam.STACK_MASS, 1.5, s -> s.massKg),
                new Effect(CreativeParam.VIDEO_MASS, 1.5, s -> s.massKg),
                new Effect(CreativeParam.WHEELBASE, 1.3, s -> s.armLength),
                new Effect(CreativeParam.PROP_DIAMETER, 1.2, s -> s.propDiameter),
                new Effect(CreativeParam.PROP_PITCH, 1.2, s -> s.propPitch),
                new Effect(CreativeParam.PROP_THRUST_COEFFICIENT, 1.2, s -> s.kThrust),
                new Effect(CreativeParam.PROP_POWER_COEFFICIENT, 1.2, s -> s.kTorque),
                new Effect(CreativeParam.MOTOR_KV, 1.2, s -> s.kv),
                new Effect(CreativeParam.MOTOR_RESISTANCE, 1.5, s -> s.motorResistance),
                new Effect(CreativeParam.MOTOR_MAX_CURRENT, 1.5, s -> s.idleCurrent),
                new Effect(CreativeParam.BATTERY_CELLS, 0.5, s -> -s.cells),
                new Effect(CreativeParam.BATTERY_CAPACITY, 1.5, s -> s.capacityMah),
                new Effect(CreativeParam.BATTERY_RESISTANCE, 1.5, s -> s.packResistance),
                new Effect(CreativeParam.BATTERY_C_RATING, 1.5, s -> s.continuousCurrent),
                new Effect(CreativeParam.ESC_CURRENT, 1.5, s -> s.escCurrentLimit),
                new Effect(CreativeParam.FRONTAL_AREA, 1.5, s -> s.areaZ),
                new Effect(CreativeParam.DRAG_COEFFICIENT, 1.5, s -> s.dragCoefficient),
                new Effect(CreativeParam.SIDE_AREA, 1.5, s -> s.areaX),
                new Effect(CreativeParam.TOP_AREA, 1.5, s -> s.areaY),
                new Effect(CreativeParam.IMPACT_TOLERANCE, 1.5, s -> s.impactTolerance),
                new Effect(CreativeParam.CAMERA_FOV, 0.8, s -> -s.cameraFovDeg),
                new Effect(CreativeParam.INERTIA_ROLL, 2.0, s -> s.inertiaZz),
                new Effect(CreativeParam.INERTIA_PITCH, 2.0, s -> s.inertiaXx),
                new Effect(CreativeParam.INERTIA_YAW, 2.0, s -> s.inertiaYy),
                new Effect(CreativeParam.CAMERA_FORWARD, 1.5, s -> s.lensForward),
                new Effect(CreativeParam.CAMERA_UP, 1.5, s -> s.lensUp));
        for (Effect effect : effects) {
            double base = CreativeFlight.baseValue(plain, effect.param());
            double target = effect.param().clamp(base * effect.factor());
            assertNotEquals(base, target, effect.param().key());
            DroneBuild build = plain.withCreative(CreativeSpec.EMPTY.with(effect.param(), target));
            assertEquals(target, CreativeFlight.value(build, effect.param()), effect.param().key());
            AirframeSpec after = AirframeSpec.of(build);
            assertTrue(effect.derived().applyAsDouble(after) > effect.derived().applyAsDouble(before),
                    effect.param().key() + ": " + effect.derived().applyAsDouble(before) + " -> " + effect.derived().applyAsDouble(after));
        }
        DroneBuild blades = plain.withCreative(CreativeSpec.EMPTY.with(CreativeParam.PROP_BLADES, 5.0));
        assertEquals(5, blades.flight().prop().blades);
        DroneBuild ducted = plain.withCreative(CreativeSpec.EMPTY.with(CreativeParam.DUCTS, 1.0));
        assertTrue(AirframeSpec.of(ducted).ducted);
        assertFalse(before.ducted);
        DroneBuild whoop = creative(DroneBuild.WHOOP).withCreative(CreativeSpec.EMPTY.with(CreativeParam.DUCTS, 0.0));
        assertFalse(AirframeSpec.of(whoop).ducted);
        double roll = Math.rint(2.0 * before.inertiaZz * CreativeFlight.GRAM_CM2_PER_KG_M2);
        DroneBuild inertia = plain.withCreative(CreativeSpec.EMPTY.with(CreativeParam.INERTIA_ROLL, roll));
        assertEquals(roll / CreativeFlight.GRAM_CM2_PER_KG_M2, AirframeSpec.of(inertia).inertiaZz, 1.0E-15);
        assertEquals(before.inertiaXx, AirframeSpec.of(inertia).inertiaXx);
        assertTrue(AirframeSpec.of(inertia).authorityRoll < before.authorityRoll);
    }

    @Test
    void freeArmsMoveTheMotors() {
        DroneBuild plain = creative(DroneBuild.FREESTYLE);
        CreativeSpec spec = CreativeSpec.EMPTY;
        for (CreativeParam param : CreativeParam.ARMS) {
            double base = CreativeFlight.baseValue(plain, param);
            spec = spec.with(param, param.key().endsWith(".up") ? base : base * 1.5);
        }
        AirframeSpec before = AirframeSpec.of(plain);
        AirframeSpec after = AirframeSpec.of(plain.withCreative(spec));
        assertTrue(after.explicitArms);
        assertEquals(1.5 * before.armLength, after.armLength, 1.0E-3);
        assertTrue(after.inertiaZz > before.inertiaZz);
        CreativeSpec stretched = spec.with(CreativeParam.ARM_FRONT_LEFT_FORWARD, 300.0).with(CreativeParam.ARM_FRONT_RIGHT_FORWARD, 300.0);
        AirframeSpec forward = AirframeSpec.of(plain.withCreative(stretched));
        assertTrue(forward.inertiaXx > after.inertiaXx);
        assertTrue(Math.abs(forward.comZ) > 1.0E-3);
    }

    @Test
    void cameraAutoPositionKeepsTheLens() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild plain = creative(preset);
            CreativeSpec spec = CreativeSpec.EMPTY;
            for (CreativeParam param : CreativeParam.CAMERA_POSITION) {
                spec = spec.with(param, CreativeFlight.baseValue(plain, param));
            }
            AirframeSpec before = AirframeSpec.of(plain);
            AirframeSpec after = AirframeSpec.of(plain.withCreative(spec));
            assertEquals(before.lensForward, after.lensForward, 1.0E-4, preset.frame().key());
            assertEquals(before.lensUp, after.lensUp, 1.0E-4, preset.frame().key());
            assertEquals(before.lensRight, after.lensRight, 1.0E-4, preset.frame().key());
        }
    }

    @Test
    void sanitizeClampsAndDropsUnknownEntries() {
        TreeMap<String, Double> values = new TreeMap<>();
        values.put(CreativeParam.MOTOR_KV.key(), 1.0E9);
        values.put(CreativeParam.PROP_BLADES.key(), 3.4);
        values.put(CreativeParam.FRAME_MASS.key(), -5.0);
        values.put("laser.power", 3.0);
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        base.put(PartKind.FRAME, "freestyle_5");
        base.put(PartKind.MOTOR, "no_such_motor");
        base.put(PartKind.PROP, "freestyle_5");
        CreativeLimits.Result result = CreativeLimits.sanitize(new CreativeSpec(base, values));
        CreativeSpec clean = result.spec();
        assertEquals(CreativeParam.MOTOR_KV.max(), clean.value(CreativeParam.MOTOR_KV).getAsDouble());
        assertEquals(3.0, clean.value(CreativeParam.PROP_BLADES).getAsDouble());
        assertEquals(CreativeParam.FRAME_MASS.min(), clean.value(CreativeParam.FRAME_MASS).getAsDouble());
        assertFalse(clean.values().containsKey("laser.power"));
        assertEquals(Map.of(PartKind.FRAME, "freestyle_5"), clean.base());
        assertEquals(5, result.notes().size(), result.notes().toString());
        assertEquals(clean, CreativeLimits.sanitize(clean).spec());
        assertTrue(CreativeLimits.sanitize(clean).notes().isEmpty());
    }

    @Test
    void specIgnoresBrokenEntries() {
        TreeMap<String, Double> values = new TreeMap<>();
        values.put(CreativeParam.MOTOR_KV.key(), Double.NaN);
        values.put(CreativeParam.FRAME_MASS.key(), Double.POSITIVE_INFINITY);
        values.put(CreativeParam.PROP_MASS.key(), -0.0);
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        base.put(PartKind.VIDEO, "digital_hd");
        base.put(PartKind.ACCESSORY, "led_strip");
        CreativeSpec spec = new CreativeSpec(base, values);
        assertTrue(spec.base().isEmpty());
        assertEquals(Map.of(CreativeParam.PROP_MASS.key(), 0.0), spec.values());
    }

    @Test
    void shareCodeRoundTrip() {
        CreativeSpec spec = template(DroneBuild.LONG_RANGE).with(CreativeParam.MOTOR_KV, 1450.0).with(CreativeParam.PROP_THRUST_COEFFICIENT, 0.123)
                .with(CreativeParam.INERTIA_YAW, 23456.5).withBase(PartKind.PROP, "justmoreparts:p13_2b");
        String code = CreativeShareCode.encode(spec);
        assertTrue(code.startsWith(CreativeShareCode.PREFIX), code);
        assertTrue(code.length() < 400, code);
        assertEquals(spec, CreativeShareCode.decode(code).orElseThrow());
        assertEquals(spec, CreativeShareCode.decode("  " + code.toLowerCase(java.util.Locale.ROOT).substring(0, 5) + code.substring(5) + "\n").orElseThrow());
        assertEquals(CreativeSpec.EMPTY, CreativeShareCode.decode(CreativeShareCode.encode(CreativeSpec.EMPTY)).orElseThrow());
        assertTrue(CreativeShareCode.decode("PWD1-!!!").isEmpty());
        assertTrue(CreativeShareCode.decode("hello").isEmpty());
        assertTrue(CreativeShareCode.decode(null).isEmpty());
        assertTrue(CreativeShareCode.decode(CreativeShareCode.PREFIX + "A".repeat(CreativeShareCode.MAX_CODE_LENGTH)).isEmpty());
        assertTrue(CreativeShareCode.decode(CreativeShareCode.PREFIX + "AAAA").isEmpty() || CreativeShareCode.decode(CreativeShareCode.PREFIX + "AAAA").isPresent());
    }

    @Test
    void codecsKeepTheSpecAndLeavePlainBuildsAlone() {
        CreativeSpec spec = template(DroneBuild.RACER).with(CreativeParam.MOTOR_KV, 2100.0).with(CreativeParam.ARM_FRONT_LEFT_FORWARD, 90.0);
        DroneBuild build = creative(DroneBuild.CINEWHOOP).withAccessories(Accessories.of(AccessoryType.LED_STRIP)).withCreative(spec);
        JsonElement json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow();
        assertEquals(build, DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), json.toString());
        assertTrue(json.getAsJsonObject().has("creative"));
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, build);
        assertEquals(build, DroneBuildCodecs.STREAM_CODEC.decode(buffer));
        assertFalse(buffer.isReadable());
        DroneBuild empty = creative(DroneBuild.CINEWHOOP);
        JsonElement plain = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow();
        assertFalse(plain.getAsJsonObject().has("creative"), plain.toString());
        assertFalse(DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.FREESTYLE).getOrThrow().getAsJsonObject().has("creative"));
        JsonElement broken = json.deepCopy();
        broken.getAsJsonObject().getAsJsonObject("creative").addProperty("values", "kaputt");
        DroneBuild recovered = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, broken).getOrThrow();
        assertEquals(spec.base(), recovered.creative().base());
    }

    @Test
    void flightBuildIsCachedPerSpec() {
        DroneBuild build = creative(DroneBuild.FREESTYLE).withCreative(CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 2000.0));
        DroneBuild flight = build.flight();
        assertSame(flight, build.flight());
        assertNotSame(build, flight);
        assertFalse(flight.edited());
        assertEquals(2000, flight.motor().kv);
        assertEquals(DroneBuild.FREESTYLE.motor().key(), flight.motor().key());
        DroneBuild equal = creative(DroneBuild.FREESTYLE).withCreative(CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 2000.0));
        assertSame(AirframeSpec.of(build), AirframeSpec.of(equal));
    }
}
