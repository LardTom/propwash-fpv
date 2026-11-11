package de.lardtom.propwash.race.data;

import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RacePayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseRaceLineTest {
    @TempDir
    Path temp;

    private static CourseData roundTrip(CourseData course) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, course);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        return decoded;
    }

    @Test
    void courseSettingIsSavedAndUntouchedFilesStayAsTheyWere() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        assertTrue(course.raceLine(), "Alte Welten zeigen die Ideallinie");
        assertTrue(course.toData().raceLine());
        assertFalse(registry.setRaceLine(course, true), "Keine Änderung");
        assertTrue(registry.setRaceLine(course, false));
        assertFalse(course.toData().raceLine(), "Abgleich zum Client trägt die Einstellung");
        registry.save();
        String saved = Files.readString(registry.path(), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"raceLine\": false"), saved);
        CourseRegistry again = TestWorlds.reopen(registry.path());
        assertFalse(again.course(1).raceLine());
        assertTrue(again.course(2).raceLine(), "Andere Kurse bleiben, wie sie waren");
        assertTrue(again.setRaceLine(again.course(1), true));
        again.save();
        assertFalse(Files.readString(again.path(), StandardCharsets.UTF_8).contains("raceLine"), "Standard steht nicht in der Datei");
    }

    @Test
    void flagTravelsWithTheCourseAndKeepsTheLayout() {
        CourseData plain = new CourseData(4, "minecraft:overworld", java.util.List.of());
        assertTrue(plain.raceLine());
        CourseData hidden = plain.withRaceLine(false);
        assertFalse(hidden.raceLine());
        assertEquals(plain.layoutHash(), hidden.layoutHash(), "Die Ideallinie gehört nicht zum Layout: Bestenliste bleibt");
        assertEquals(hidden, roundTrip(hidden));
        assertEquals(plain, roundTrip(plain));
        assertFalse(hidden.withMode(hidden.mode()).raceLine());
        assertFalse(hidden.withGates(hidden.gates()).raceLine());
        assertFalse(hidden.withCreativeFc(hidden.creativeFc()).raceLine());
    }

    @Test
    void newEditActionKeepsTheOldNumbers() {
        assertEquals(8, CourseEdits.Action.MODE.ordinal());
        assertEquals(9, CourseEdits.Action.RACE_LINE.ordinal());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.CourseEditC2S edit = new RacePayloads.CourseEditC2S(4, CourseEdits.Action.RACE_LINE, 0, 0);
        RacePayloads.CourseEditC2S.CODEC.encode(buf, edit);
        assertEquals(edit, RacePayloads.CourseEditC2S.CODEC.decode(buf));
    }
}
