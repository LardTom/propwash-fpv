package de.lardtom.propwash.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.client.WorkbenchReport;
import de.lardtom.propwash.core.PartCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TranslationKeysTest {
    private static Path lang() {
        for (Path candidate : List.of(Path.of("common/src/lang"), Path.of("src/lang"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        fail("Sprachquellen nicht gefunden");
        return Path.of(".");
    }

    private static Set<String> keys(String language) throws IOException {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> modules = Files.list(lang())) {
            for (Path module : modules.toList()) {
                Path file = module.resolve(language + ".json");
                if (Files.exists(file)) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        keys.addAll(json.keySet());
                    }
                }
            }
        }
        return keys;
    }

    private static void collect(Component component, Set<String> out) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            out.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested) {
                    collect(nested, out);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collect(sibling, out);
        }
    }

    private static void assertKnown(Set<String> used) throws IOException {
        for (String language : List.of("de_de", "en_us")) {
            Set<String> available = keys(language);
            Set<String> missing = new TreeSet<>(used);
            missing.removeAll(available);
            assertTrue(missing.isEmpty(), language + " fehlen: " + missing);
        }
    }

    @Test
    void partTooltipsOnlyUseKnownKeys() throws IOException {
        Set<String> used = new HashSet<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                List<Component> lines = new ArrayList<>();
                PartTooltips.append(part, lines::add);
                assertTrue(lines.size() >= 4, part.key() + " hat nur " + lines.size() + " Tooltip-Zeilen");
                lines.forEach(line -> collect(line, used));
                used.add(part.translationKey());
            }
        }
        for (VideoSystem system : VideoSystem.values()) {
            collect(PartTooltips.videoSystemName(system), used);
        }
        for (float charge : new float[]{0.0F, 0.35F, 0.9F, 1.5F}) {
            collect(PartTooltips.charge(charge), used);
        }
        assertKnown(used);
    }

    private static List<DroneBuild> sampleBuilds() {
        List<DroneBuild> builds = new ArrayList<>(DroneBuild.PRESETS);
        builds.add(new DroneBuild(FrameType.WHOOP_65, StackType.STACK_30, MotorType.M2807_1300, PropType.P7_2B, VideoType.DIGITAL_HD,
                BatteryType.B6S_4000_LION));
        builds.add(new DroneBuild(FrameType.FREESTYLE_5, StackType.AIO_WHOOP, MotorType.M2207_1750, PropType.P51_3B, VideoType.ANALOG_NANO,
                BatteryType.B1S_300_HV));
        builds.add(new DroneBuild(FrameType.LONG_RANGE_7, StackType.AIO_20, MotorType.M2807_1300, PropType.P31_4B, VideoType.ANALOG_RACE,
                BatteryType.B4S_650));
        builds.add(new DroneBuild(FrameType.CINEWHOOP_3, StackType.AIO_20, MotorType.M1404_4600, PropType.P3_3B, VideoType.DIGITAL_HD,
                BatteryType.B6S_1300));
        builds.add(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP));
        builds.add(DroneBuild.CINEWHOOP.withAccessory(AccessoryType.ACTION_CAM_LIGHT));
        builds.add(DroneBuild.WHOOP.withAccessory(AccessoryType.ACTION_CAM_LIGHT));
        return builds;
    }

    @Test
    void workbenchReportOnlyUsesKnownKeys() throws IOException {
        Set<String> used = new HashSet<>();
        Set<DroneBuild.Issue> seenIssues = new HashSet<>();
        for (DroneBuild build : sampleBuilds()) {
            for (boolean droneItem : new boolean[]{true, false}) {
                WorkbenchReport report = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(build)), droneItem);
                assertEquals(build, report.build());
                assertEquals(6, report.rows().size());
                for (WorkbenchReport.Row row : report.rows()) {
                    collect(row.label(), used);
                    collect(row.value(), used);
                }
                report.details().forEach(line -> collect(line, used));
                report.issues().forEach(line -> collect(line.text(), used));
                if (!droneItem) {
                    assertFalse(report.issues().isEmpty());
                }
            }
            seenIssues.addAll(build.problems());
            seenIssues.addAll(build.warnings());
        }
        List<PartInfo> mixed = new ArrayList<>(BuildSlots.layout(DroneBuild.FREESTYLE));
        mixed.set(BuildSlots.MOTOR, MotorType.M2306_1860);
        mixed.set(BuildSlots.PROP, PropType.P3_3B);
        WorkbenchReport mixedReport = WorkbenchReport.of(BuildSlots.check(mixed), true);
        assertEquals(2, mixedReport.issues().size());
        mixedReport.issues().forEach(line -> collect(line.text(), used));
        mixedReport.rows().forEach(row -> collect(row.label(), used));
        assertTrue(seenIssues.size() >= 5, "zu wenige Probleme abgedeckt: " + seenIssues);
        assertKnown(used);
    }

    @Test
    void freestyleReportIsReadyToFly() {
        WorkbenchReport report = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(DroneBuild.FREESTYLE)), true);
        assertTrue(report.issues().isEmpty(), "Freestyle-Preset meldet " + report.issues());
        assertTrue(report.analysis() != null);
        for (PartCategory category : PartCategory.values()) {
            assertEquals(0, report.highlight(category));
        }
    }

    @Test
    void problemsHighlightTheInvolvedSlots() {
        DroneBuild broken = new DroneBuild(FrameType.WHOOP_65, StackType.AIO_WHOOP, MotorType.M2207_1750, PropType.P31_4B,
                VideoType.ANALOG_NANO, BatteryType.B1S_300_HV);
        WorkbenchReport report = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(broken)), true);
        assertEquals(WorkbenchReport.COLOR_BAD, report.highlight(PartCategory.FRAME));
        assertEquals(WorkbenchReport.COLOR_BAD, report.highlight(PartCategory.MOTOR));
    }
}
