package de.lardtom.propwash.parts;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MassSplit;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PackSize;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.RecycleYield;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.BuiltinParts;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PartJsonTest {
    private static final String SNAPSHOT = "de/lardtom/propwash/build/builtin_definitions.txt";
    private static final Set<String> ADDED_AFTER_032 = Set.of("gripper", "action_cam_flashback", "creative_fc", "p51_29_3b");
    private static final List<String> NEUTRAL_ADDITIONS = List.of(", rarity=Optional.empty]", ", reverseThrust=OptionalDouble.empty]", ", FRONT]");
    private static final Pattern FRONT_ANCHOR = Pattern.compile(", FRONT=AnchorSpec\\[[^\\]]*\\]");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("justmoreparts", path);
    }

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static PartDefinition roundTrip(PartDefinition definition) {
        String text = JsonText.pretty(PartJson.encode(definition));
        PartDataProblems problems = new PartDataProblems(definition.id().toString());
        JsonObject json = PartJson.parse(text, problems);
        assertNotNull(json, problems.errors().toString());
        PartDefinition decoded = PartJson.decode(definition.kind(), definition.id(), json, problems);
        assertEquals(List.of(), problems.errors(), text);
        assertEquals(List.of(), problems.warnings(), text);
        return decoded;
    }

    private static PartDataProblems decodeFails(PartKind kind, String text) {
        PartDataProblems problems = new PartDataProblems("test.json");
        JsonObject json = PartJson.parse(text, problems);
        if (json != null) {
            assertNull(PartJson.decode(kind, id("broken"), json, problems));
        }
        assertTrue(problems.hasErrors());
        return problems;
    }

    private static void assertMentions(PartDataProblems problems, String... fragments) {
        String all = String.join("\n", problems.errors());
        for (String fragment : fragments) {
            assertTrue(all.contains(fragment), "erwartet \"" + fragment + "\" in:\n" + all);
        }
    }

    @Test
    void builtinDefinitionsAreIdenticalTo032() throws IOException {
        List<String> expected = Files.readAllLines(common().resolve("src/test/resources").resolve(SNAPSHOT));
        List<String> actual = new ArrayList<>();
        for (BatteryType.Chemistry chemistry : BatteryType.Chemistry.values()) {
            actual.add(chemistry.name() + " " + chemistry.definition());
        }
        for (PartInfo part : PartIndex.all()) {
            if (part.builtin() && !ADDED_AFTER_032.contains(part.key())) {
                actual.add(withoutNeutralAdditions(part.name() + " " + part.definition()));
            }
        }
        assertEquals(expected, actual);
    }

    private static String withoutNeutralAdditions(String line) {
        String out = line;
        for (String addition : NEUTRAL_ADDITIONS) {
            out = out.replace(addition, "]");
        }
        return FRONT_ANCHOR.matcher(out).replaceAll("");
    }

    @Test
    void everyBuiltinDefinitionSurvivesEncodeAndDecode() {
        for (PartInfo part : PartIndex.all()) {
            assertEquals(part.definition(), roundTrip(part.definition()), part.key());
        }
        for (BatteryType.Chemistry chemistry : BatteryType.Chemistry.values()) {
            String text = JsonText.pretty(PartJson.encode(chemistry.definition()));
            PartDataProblems problems = new PartDataProblems(chemistry.key());
            assertEquals(chemistry.definition(), PartJson.decodeChemistry(chemistry.id(), PartJson.parse(text, problems), problems));
        }
    }

    @Test
    void everyBuiltinHasItsJsonFileAndNoOtherFileLiesThere() throws IOException {
        Path root = common().resolve("src/main/resources").resolve(BuiltinParts.ROOT);
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> files.add(root.relativize(file).toString().replace('\\', '/')));
        }
        List<String> expected = new ArrayList<>();
        for (BatteryType.Chemistry chemistry : BatteryType.Chemistry.values()) {
            expected.add(PartJson.CHEMISTRY + "/" + chemistry.id().getPath() + ".json");
        }
        for (PartInfo part : PartIndex.all()) {
            if (part.builtin()) {
                expected.add(part.definition().kind().key() + "/" + part.id().getPath() + ".json");
            }
        }
        files.sort(String::compareTo);
        expected.sort(String::compareTo);
        assertEquals(expected, files);
    }

    @Test
    void addonShapesRoundTrip() {
        FrameDefinition deadcat = FrameDefinition.builder(id("deadcat_5"))
                .basics(PartBasics.of(118.0).withStackSize(8).withCreativeListed(false).withDurability(1.3)
                        .withRecycle(new RecycleYield(Identifier.fromNamespaceAndPath("propwash", "carbon_plate"), 2))
                        .withVisual(PartVisual.accent(0x123abc).withRenderModel(Identifier.fromNamespaceAndPath("propwash", "drone_render/frame/freestyle_5"))))
                .role(FrameRole.FREESTYLE).shape(FrameShape.DEADCAT).wheelbase(230).props(76, 130)
                .motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20).accessoryMount(AccessorySlot.TOP)
                .arms(List.of(MotorArm.of(-95.0, 0.0, -60.0, MotorArm.Spin.CW), MotorArm.of(95.0, 0.0, -60.0, MotorArm.Spin.CCW),
                        MotorArm.of(-80.0, 0.0, 80.0, MotorArm.Spin.CCW), MotorArm.of(80.0, 0.0, 80.0, MotorArm.Spin.CW)))
                .battery(true, 30.0).electronics(12.0).camera(new Vec3Mm(0.0, 14.0, -58.0)).cameraTilt(30.0)
                .centerOfMass(new Vec3Mm(0.0, 0.0, 6.0)).massSplit(new MassSplit(0.5, 0.5, 0.0))
                .anchor(AccessorySlot.TOP, new de.lardtom.propwash.api.part.AnchorSpec(0.0, 30.0, -60.0, 20.0, 5.0))
                .aero(0.012, 0.9).sideArea(0.02).topArea(0.03)
                .duct(new DuctSpec(OptionalDouble.of(1.1), OptionalDouble.empty(), OptionalDouble.of(0.3), OptionalDouble.empty(),
                        OptionalDouble.of(0.2), OptionalDouble.empty(), OptionalDouble.of(0.6)))
                .impactTolerance(9.0).batteryFit(new BatteryFitRule(4, 6, 260.0, 6, 1000))
                .build();
        StackDefinition stack = StackDefinition.builder(id("stack_8s")).mass(12.0).mount(Mounts.STACK_S30).escContinuous(60.0).cells(3, 8)
                .escBurst(80.0).escResistance(0.004).brownout(6.0).boardSize(30.5).build();
        MotorDefinition motor = MotorDefinition.builder(id("m4214_400")).mass(128.0).stator("4214").statorSize(42.5, 14.0).kv(400)
                .mount(id("m42")).maxCurrent(60.0).resistance(0.045).cells(6, 12).propMount(id("m5_shaft"))
                .thermal(new MotorThermal(OptionalDouble.of(70.0), OptionalDouble.empty(), OptionalDouble.of(2.0E-4), 140.0, 160.0, 0.0))
                .build();
        PropDefinition prop = PropDefinition.builder(id("p13_2b_carbon")).mass(38.0).size(330, 7.0, 2).coefficients(0.1, 0.04)
                .material(PropMaterial.CARBON).mount(id("m5_shaft")).build();
        PropDefinition stiffProp = PropDefinition.builder(id("p5_gfn")).mass(4.4).durability(0.95).size(127, 4.3, 3).coefficients(0.12, 0.06)
                .material(PropMaterial.GLASS_FIBER_NYLON).stiffness(1.3).build();
        BatteryDefinition battery = BatteryDefinition.builder(id("b8s_4500_lion")).mass(560.0).cells(8).capacity(4500).cellResistance(0.014)
                .cRating(12).chemistry(BatteryDefinition.LION).packSize(new PackSize(140.0, 45.0, 70.0)).connector("XT60").build();
        VideoDefinition video = VideoDefinition.builder(id("hd_pro")).mass(20.0).link(VideoLink.DIGITAL).latency(22).range(900).imageQuality(0.95)
                .cameraWidth(19.0).cameraFov(155.0).vtxPower(1000).build();
        for (PartDefinition definition : List.of(deadcat, stack, motor, prop, stiffProp, battery, video)) {
            assertEquals(definition, roundTrip(definition), definition.id().toString());
        }
    }

    @Test
    void symmetric3dPropsCarryTheirReverseThrust() {
        PropDefinition symmetric = PropDefinition.builder(id("p5_3d")).mass(4.2).size(127, 3.0, 3).coefficients(0.1, 0.055).reverseThrust(0.95)
                .build();
        PropDefinition decoded = (PropDefinition) roundTrip(symmetric);
        assertEquals(symmetric, decoded);
        assertEquals(OptionalDouble.of(0.95), decoded.reverseThrust());
        assertTrue(JsonText.pretty(PartJson.encode(symmetric)).contains("\"reverse_thrust\": 0.95"));
        PropDefinition standard = PropDefinition.builder(id("p5")).mass(4.2).size(127, 4.3, 3).coefficients(0.12, 0.06).build();
        assertEquals(OptionalDouble.empty(), standard.reverseThrust());
        assertTrue(!JsonText.pretty(PartJson.encode(standard)).contains("reverse_thrust"));
        assertMentions(decodeFails(PartKind.PROP, "{\"mass_g\": 4, \"diameter_mm\": 127, \"pitch_in\": 3.0, \"blades\": 3, \"ct\": 0.1,"
                + " \"cp\": 0.05, \"reverse_thrust\": 1.4}"), "Umkehrschub muss zwischen 0.2 und 1.0");
    }

    @Test
    void deadcatExampleFromTheContractDecodes() {
        String text = """
                {
                  "format": 1,
                  "mass_g": 118.0,
                  "role": "freestyle",
                  "shape": "deadcat",
                  "wheelbase_mm": 230,
                  "max_prop_mm": 130,
                  "motor_mounts": ["propwash:m22"],
                  "stack_mounts": ["propwash:s30", "propwash:s20"],
                  "accessory_mounts": ["top", "bottom"],
                  "arms": {
                    "front_left":  {"pos": [-95.0, 0.0, -60.0], "spin": "cw"},
                    "front_right": {"pos": [95.0, 0.0, -60.0], "spin": "ccw"},
                    "rear_left":   {"pos": [-80.0, 0.0, 80.0], "spin": "ccw"},
                    "rear_right":  {"pos": [80.0, 0.0, 80.0], "spin": "cw"}
                  },
                  "battery": {"on_top": true, "plate_mm": 30.0},
                  "electronics_mm": 12.0,
                  "camera": {"pos": [0.0, 14.0, -58.0], "tilt_deg": 30.0},
                  "center_of_mass_mm": [0.0, 0.0, 6.0],
                  "aero": {"frontal_area_m2": 0.012, "drag_coefficient": 0.9},
                  "impact_tolerance": 8.0,
                  "battery_fit": {"cells": [4, 6], "max_mass_g": 260.0, "preferred_min_cells": 6, "preferred_min_capacity_mah": 0}
                }
                """;
        PartDataProblems problems = new PartDataProblems("deadcat_5.json");
        FrameDefinition frame = (FrameDefinition) PartJson.decode(PartKind.FRAME, id("deadcat_5"), PartJson.parse(text, problems), problems);
        assertEquals(List.of(), problems.errors());
        assertNotNull(frame);
        assertEquals(FrameShape.DEADCAT, frame.shape());
        assertEquals(4, frame.geometry().arms().size());
        assertEquals(MotorArm.Spin.CCW, frame.geometry().arms().get(MotorArm.FRONT_RIGHT).spin());
        assertEquals(PartBasics.DEFAULT_SORT_ORDER, frame.basics().sortOrder());
        assertEquals(MassSplit.OPEN, frame.geometry().massSplit());
    }

    @Test
    void shortFormsAndDefaultsFollowTheContract() {
        PartDataProblems problems = new PartDataProblems("m4214_400.json");
        MotorDefinition motor = (MotorDefinition) PartJson.decode(PartKind.MOTOR, id("m4214_400"), PartJson.parse("""
                {"format": 1, "mass_g": 128.0, "stator": "4214", "kv": 400, "mount": "justmoreparts:m42", "max_current_a": 60.0,
                 "resistance_ohm": 0.045, "cells": [6, 12], "thermal": {"derate_start_c": 140.0, "max_temp_c": 160.0}}
                """, problems), problems);
        assertEquals(List.of(), problems.errors());
        assertNotNull(motor);
        assertEquals(42.0, motor.statorDiameterMm());
        assertEquals(14.0, motor.statorHeightMm());
        assertEquals(MotorThermal.limits(140.0, 160.0), motor.thermal());
        assertEquals(Mounts.PROP_UNIVERSAL, motor.propMount());

        PartDataProblems stackProblems = new PartDataProblems("stack.json");
        StackDefinition stack = (StackDefinition) PartJson.decode(PartKind.STACK, id("stack"), PartJson.parse("""
                {"mass_g": 7.0, "mount": "s20", "esc_continuous_a": 25, "cells": 4}
                """, stackProblems), stackProblems);
        assertEquals(List.of(), stackProblems.errors());
        assertNotNull(stack);
        assertEquals(Mounts.STACK_S20, stack.mount());
        assertEquals(4, stack.minCells());
        assertEquals(4, stack.maxCells());

        PartDataProblems propProblems = new PartDataProblems("prop.json");
        PropDefinition prop = (PropDefinition) PartJson.decode(PartKind.PROP, id("prop"), PartJson.parse("""
                {"mass_g": 38.0, "diameter_mm": 330, "pitch_in": 7.0, "blades": 2, "ct": 0.1, "cp": 0.04, "material": "carbon"}
                """, propProblems), propProblems);
        assertNotNull(prop);
        assertEquals(PropMaterial.CARBON.defaultDurability(), prop.basics().durability());
        assertEquals(PropMaterial.CARBON.defaultStiffness(), prop.stiffness());

        PartDataProblems batteryProblems = new PartDataProblems("b8s_4500_lion.json");
        BatteryDefinition battery = (BatteryDefinition) PartJson.decode(PartKind.BATTERY, id("b8s_4500_lion"), PartJson.parse("""
                {"format": 1, "mass_g": 560.0, "cells": 8, "capacity_mah": 4500, "cell_resistance_ohm": 0.014, "c_rating": 12,
                 "chemistry": "propwash:lion", "pack_mm": [140.0, 45.0, 70.0], "connector": "XT60", "visual": {"accent": "#1a8f8b"}}
                """, batteryProblems), batteryProblems);
        assertNotNull(battery);
        assertEquals(0x1A8F8B, battery.basics().visual().accentRgb().orElseThrow());
        assertEquals(BatteryDefinition.LION, battery.chemistry());
    }

    @Test
    void brokenFilesNameEveryFieldAtOnce() {
        assertMentions(decodeFails(PartKind.MOTOR, "{\"mass_g\": 20.0, \"kv\": \"viel\"}"),
                "stator: Pflichtfeld fehlt", "kv: ganze Zahl erwartet", "mount: Pflichtfeld fehlt", "max_current_a: Pflichtfeld fehlt",
                "resistance_ohm: Pflichtfeld fehlt", "cells: Pflichtfeld fehlt");
        assertMentions(decodeFails(PartKind.FRAME, "{\"mass_g\": 100, \"wheelbase_mm\": 220, \"max_prop_mm\": 130, \"motor_mounts\": [],"
                        + " \"stack_mounts\": [\"S30\"], \"role\": \"racer\", \"aero\": {\"frontal_area_m2\": 0.01}}"),
                "motor_mounts: mindestens eine Id", "stack_mounts[0]: ungültige Id", "role: unbekannter Wert \"racer\"",
                "aero.drag_coefficient: Pflichtfeld fehlt", "battery_fit: Pflichtfeld fehlt");
        assertMentions(decodeFails(PartKind.BATTERY, "{\"mass_g\": 50, \"cells\": 20, \"capacity_mah\": 1000, \"cell_resistance_ohm\": 0.01,"
                + " \"c_rating\": 50}"), "Zellzahl muss zwischen 1 und 12");
        assertMentions(decodeFails(PartKind.PROP, "{\"mass_g\": 4, \"diameter_mm\": 127, \"pitch_in\": 4.3, \"blades\": 3, \"ct\": 0.1,"
                + " \"cp\": 0.05, \"format\": 2}"), "Format 2 wird nicht unterstützt");
        assertMentions(decodeFails(PartKind.VIDEO, "{ nicht json"), "ungültiges JSON");
        assertMentions(decodeFails(PartKind.ACCESSORY, "{\"mass_g\": 40, \"slot\": \"bottom\", \"size_mm\": [40, 30, 20],"
                        + " \"action_cam\": {\"max_height\": 1080, \"max_fps\": 60, \"record_w\": 4, \"fov_deg\": 150, \"image_quality\": 0.8,"
                        + " \"low_light_noise\": 0.5, \"sensor_in\": \"halb\"}}"),
                "action_cam.sensor_in: Zahl oder Bruch");
    }

    @Test
    void unknownFieldsOnlyWarn() {
        PartDataProblems problems = new PartDataProblems("video.json");
        PartDefinition video = PartJson.decode(PartKind.VIDEO, id("video"), PartJson.parse("""
                {"mass_g": 8.0, "link": "analog", "latency_ms": 12, "range_blocks": 380, "image_quality": 0.65, "colour": "blau",
                 "visual": {"accent": "#ffffff", "glow": true}}
                """, problems), problems);
        assertNotNull(video);
        assertEquals(List.of("colour: unbekanntes Feld wird ignoriert", "visual.glow: unbekanntes Feld wird ignoriert"),
                problems.warnings().stream().sorted().toList());
    }

    @Test
    void chemistryJsonNamesBrokenCurves() {
        PartDataProblems problems = new PartDataProblems("chemistry.json");
        CellChemistry broken = PartJson.decodeChemistry(id("lihv_plus"), PartJson.parse("""
                {"full_v": 4.45, "nominal_v": 3.85, "empty_v": 3.5, "cutoff_v": 3.3, "storage_v": 3.9, "density_g_cm3": 2.1,
                 "ocv_shape": [0.0, 0.2, 0.1, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0]}
                """, problems), problems);
        assertNull(broken);
        assertMentions(problems, "monoton");
    }

    @Test
    void numbersArePrintedWithoutExponents() {
        assertEquals("0.0001", JsonText.number(0.0001));
        assertEquals("4.0", JsonText.number(4.0));
        assertEquals("-3.0", JsonText.number(-3.0));
        assertEquals("0.35714285714285715", JsonText.number(1.0 / 2.8));
        assertEquals(1.0 / 2.8, Double.parseDouble(JsonText.number(1.0 / 2.8)));
    }
}
