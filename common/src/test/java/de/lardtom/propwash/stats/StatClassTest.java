package de.lardtom.propwash.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatClassTest {
    private static FrameType frame(FrameRole role) {
        return (FrameType) PartIndex.detached(FrameDefinition.builder(Identifier.fromNamespaceAndPath("propwash_statstest", role.key()))
                .mass(100.0).role(role).wheelbase(200).maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30)
                .accessoryMount(AccessorySlot.TOP).aero(0.01, 0.9).batteryFit(new BatteryFitRule(4, 6, 250.0, 6, 0)).build());
    }

    private static JsonObject lang(String code) throws IOException {
        return JsonParser.parseString(Files.readString(TestPaths.lang("pw-stats").resolve(code + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void builtinFramesFlyInTheirClass() {
        assertEquals(FrameRole.WHOOP, StatClass.of(FrameType.WHOOP_65));
        assertEquals(FrameRole.CINEWHOOP, StatClass.of(FrameType.CINEWHOOP_3));
        assertEquals(FrameRole.FREESTYLE, StatClass.of(FrameType.FREESTYLE_5));
        assertEquals(FrameRole.RACE, StatClass.of(FrameType.RACE_5));
        assertEquals(FrameRole.LONG_RANGE, StatClass.of(FrameType.LONG_RANGE_7));
        assertEquals(FrameRole.FREESTYLE, StatClass.of(DroneBuild.FREESTYLE));
        assertEquals(FrameRole.RACE, StatClass.of(DroneBuild.RACER));
        assertEquals(StatClass.FALLBACK, StatClass.of((FrameType) null));
        assertEquals(StatClass.FALLBACK, StatClass.of((DroneBuild) null));
    }

    @Test
    void addonFramesCountForTheirRoleAndEveryRoleHasItsOwnTime() {
        Set<String> names = new HashSet<>();
        for (FrameRole role : FrameRole.values()) {
            assertEquals(role, StatClass.of(frame(role)), role.key());
            assertTrue(names.add(StatNames.classTime(role)), "doppelte Statistik für " + role);
        }
        assertEquals("flight_time_x_class", StatNames.classTime(FrameRole.X_CLASS));
        assertEquals("flight_time_toothpick", StatNames.classTime(FrameRole.TOOTHPICK));
    }

    @Test
    void namesAreUniqueAndTranslatedInBothLanguages() throws IOException {
        List<String> all = StatNames.all();
        assertEquals(StatNames.COUNTERS.size() + FrameRole.values().length, all.size());
        assertEquals(all.size(), new HashSet<>(all).size(), "doppelte Namen: " + all);
        Set<String> expected = new TreeSet<>();
        for (String name : all) {
            assertTrue(name.matches("[a-z0-9_]+"), name);
            expected.add(StatNames.translationKey(name));
        }
        for (String code : List.of("en_us", "de_de")) {
            JsonObject json = lang(code);
            Set<String> stats = new TreeSet<>(json.keySet());
            stats.removeIf(key -> key.startsWith("advancements.propwash.fpv."));
            assertEquals(expected, stats, code);
            for (String key : expected) {
                String text = json.get(key).getAsString();
                assertTrue(text.startsWith("Propwash: "), code + " " + key + ": " + text);
            }
        }
        assertEquals("stat.propwash.flight_time_long_range", StatNames.translationKey(StatNames.classTime(FrameRole.LONG_RANGE)));
        assertTrue(lang("de_de").get("stat.propwash.top_speed").getAsString().contains("Höchstgeschwindigkeit"));
    }

    @Test
    void topSpeedReadsInKmhWithOneDecimal() {
        assertEquals("0.0 km/h", StatFormats.SPEED.format(0));
        assertEquals("123.4 km/h", StatFormats.SPEED.format(1234));
        assertEquals(360, StatFormats.tenthsKmh(10.0));
        assertEquals(0, StatFormats.tenthsKmh(-1.0));
        assertEquals(0, StatFormats.tenthsKmh(Double.NaN));
        assertEquals("1.00 min", StatFormatter.TIME.format(1200), "Flugzeit im Vanilla-Format");
        assertEquals("1.50 km", StatFormatter.DISTANCE.format(150_000), "Strecke im Vanilla-Format");
    }
}
