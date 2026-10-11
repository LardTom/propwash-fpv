package de.lardtom.propwash.parts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.race.GhostLook;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PartRulesTest {
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("justmoreparts", path);
    }

    private static Path data() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            Path data = candidate.resolve("src/main/resources/data/propwash");
            if (Files.isDirectory(data)) {
                return data;
            }
        }
        fail("common-Modul nicht gefunden");
        return Path.of(".");
    }

    @Test
    void builtinCombinationsGetNoNewProblems() {
        for (FrameType frame : FrameType.values()) {
            for (PropType prop : PropType.values()) {
                for (MotorType motor : MotorType.values()) {
                    DroneBuild build = new DroneBuild(frame, DroneBuild.FREESTYLE.stack(), motor, prop, DroneBuild.FREESTYLE.video(),
                            DroneBuild.FREESTYLE.battery());
                    List<DroneBuild.Issue> problems = build.problems();
                    assertFalse(problems.contains(DroneBuild.Issue.MISSING_PART));
                    assertFalse(problems.contains(DroneBuild.Issue.PROP_TOO_SMALL));
                    assertFalse(problems.contains(DroneBuild.Issue.PROP_MOUNT));
                }
            }
        }
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertTrue(preset.buildable(), preset.frame().key());
        }
    }

    @Test
    void propRangeAndShaftMountAreChecked() {
        FrameType xClass = (FrameType) PartIndex.detached(FrameDefinition.builder(id("xclass_13")).mass(1450.0).wheelbase(820).props(250, 330)
                .motorMount(id("m42")).stackMount(de.lardtom.propwash.api.part.Mounts.STACK_S30).aero(0.08, 1.0)
                .batteryFit(new de.lardtom.propwash.api.part.BatteryFitRule(6, 8, 2500.0, 8, 4000)).build());
        MotorType motor = (MotorType) PartIndex.detached(MotorDefinition.builder(id("m4214_400")).mass(128.0).stator("4214").kv(400)
                .mount(id("m42")).maxCurrent(60.0).resistance(0.045).cells(6, 12).propMount(id("m5_shaft")).build());
        PropType small = PropType.P51_3B;
        PropType big = (PropType) PartIndex.detached(PropDefinition.builder(id("p13_2b")).mass(38.0).size(330, 7.0, 2).coefficients(0.1, 0.04)
                .mount(id("m8_shaft")).build());
        DroneBuild tooSmall = new DroneBuild(xClass, DroneBuild.FREESTYLE.stack(), motor, small, DroneBuild.FREESTYLE.video(),
                DroneBuild.LONG_RANGE.battery());
        assertTrue(tooSmall.problems().contains(DroneBuild.Issue.PROP_TOO_SMALL));
        assertFalse(tooSmall.problems().contains(DroneBuild.Issue.PROP_MOUNT));
        DroneBuild wrongShaft = tooSmall.withProp(big);
        assertFalse(wrongShaft.problems().contains(DroneBuild.Issue.PROP_TOO_SMALL));
        assertTrue(wrongShaft.problems().contains(DroneBuild.Issue.PROP_MOUNT));
        List<DroneBuild.Issue> order = List.of(DroneBuild.Issue.MISSING_PART, DroneBuild.Issue.MOTOR_MOUNT, DroneBuild.Issue.PROP_TOO_LARGE,
                DroneBuild.Issue.PROP_TOO_SMALL, DroneBuild.Issue.PROP_MOUNT, DroneBuild.Issue.STACK_MOUNT, DroneBuild.Issue.BATTERY_FRAME,
                DroneBuild.Issue.ACCESSORY_MOUNT);
        DroneBuild everything = new DroneBuild((FrameType) PartIndex.missing(PartKind.FRAME, id("gone")), DroneBuild.WHOOP.stack(), motor, big,
                DroneBuild.FREESTYLE.video(), DroneBuild.LONG_RANGE.battery());
        List<DroneBuild.Issue> problems = everything.problems();
        List<DroneBuild.Issue> sorted = new ArrayList<>(problems);
        sorted.sort((a, b) -> Integer.compare(order.indexOf(a), order.indexOf(b)));
        assertEquals(sorted, problems);
        assertEquals(DroneBuild.Issue.MISSING_PART, problems.getFirst());
    }

    @Test
    void oldIssueOrdinalsAreUnchanged() {
        List<String> names = new ArrayList<>();
        for (DroneBuild.Issue issue : DroneBuild.Issue.values()) {
            names.add(issue.name());
        }
        assertEquals(List.of("MOTOR_MOUNT", "PROP_TOO_LARGE", "STACK_MOUNT", "MOTOR_OVERVOLTAGE", "MOTOR_UNDERVOLTAGE", "STACK_VOLTAGE",
                "ESC_UNDERSIZED", "BATTERY_WEAK", "BATTERY_FRAME", "BATTERY_UNDERSIZED", "ACCESSORY_MOUNT", "MISSING_PART", "PROP_TOO_SMALL",
                "PROP_MOUNT"), names);
    }

    @Test
    void partTagsListEveryBuiltinPartOnce() throws IOException {
        Path tags = data().resolve("tags/item");
        JsonArray all = JsonParser.parseString(Files.readString(tags.resolve("parts.json"))).getAsJsonObject().getAsJsonArray("values");
        assertEquals(PartKind.values().length, all.size());
        for (PartKind kind : PartKind.values()) {
            assertTrue(all.contains(JsonParser.parseString("\"#propwash:parts/" + kind.key() + "\"")), kind.key());
            JsonArray values = JsonParser.parseString(Files.readString(tags.resolve("parts/" + kind.key() + ".json"))).getAsJsonObject()
                    .getAsJsonArray("values");
            List<String> expected = new ArrayList<>();
            for (PartInfo part : PartIndex.all(kind)) {
                if (part.builtin()) {
                    expected.add(part.id().toString());
                }
            }
            List<String> actual = new ArrayList<>();
            for (JsonElement value : values) {
                actual.add(value.getAsString());
            }
            assertEquals(expected, actual, kind.key());
        }
    }

    @Test
    void everyItemTagHasATranslation() throws IOException {
        Path tags = data().resolve("tags/item");
        Path langDir = data().getParent().getParent().resolve("assets/propwash/lang");
        List<String> keys = new ArrayList<>();
        try (Stream<Path> files = Files.walk(tags)) {
            files.filter(file -> file.toString().endsWith(".json")).sorted().forEach(file -> {
                String path = tags.relativize(file).toString().replace(File.separatorChar, '/');
                keys.add("tag.item.propwash." + path.substring(0, path.length() - ".json".length()).replace('/', '.'));
            });
        }
        assertTrue(keys.contains("tag.item.propwash.parts"), keys.toString());
        for (PartKind kind : PartKind.values()) {
            assertTrue(keys.contains("tag.item.propwash.parts." + kind.key()), keys.toString());
        }
        for (String language : List.of("de_de", "en_us")) {
            JsonObject lang = JsonParser.parseString(Files.readString(langDir.resolve(language + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : keys) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), language + " fehlt " + key);
            }
        }
    }

    @Test
    void ghostsWithAddonPartsStillLoad() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(GhostLook.VERSION);
            out.writeUTF("justmoreparts:deadcat_5");
            out.writeUTF("stack_30");
            out.writeUTF("justmoreparts:m2806_1300");
            out.writeUTF("p51_3b");
            out.writeUTF("digital_hd");
            out.writeUTF("b6s_1300");
            out.writeByte(2);
            out.writeUTF("led_strip");
            out.writeUTF("justmoreparts:gopro_13");
            out.writeUTF("");
        }
        GhostLook look = GhostLook.decode(bytes.toByteArray()).orElseThrow();
        assertTrue(look.build().frame().missing());
        assertTrue(look.build().motor().missing());
        assertEquals(1, look.build().accessories().size());
        assertEquals("justmoreparts:deadcat_5", look.build().frame().key());
    }
}
