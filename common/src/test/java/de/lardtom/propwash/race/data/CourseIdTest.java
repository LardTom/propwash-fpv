package de.lardtom.propwash.race.data;

import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseId;
import de.lardtom.propwash.race.CourseRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseIdTest {
    private static final String A = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    private static CourseRegistry.Course course(int id, String uuid) {
        CourseRegistry.Course course = new CourseRegistry.Course();
        course.id = id;
        course.uuid = uuid;
        return course;
    }

    @Test
    void parsesWithAndWithoutDashes() {
        UUID id = UUID.fromString(A);
        assertEquals(id, CourseId.parse(A));
        assertEquals(id, CourseId.parse(A.toUpperCase()));
        assertEquals(id, CourseId.parse("  " + A + " "));
        assertEquals(id, CourseId.parse(A.replace("-", "")));
        assertNull(CourseId.parse("3f2504e0-4f89-41d3-9a0c"));
        assertNull(CourseId.parse("Kurs 3"));
        assertNull(CourseId.parse("12"));
        assertNull(CourseId.parse(null));
        assertEquals(A, CourseId.text(id));
    }

    @Test
    void freshIdsAreRandomVersionFour() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            UUID id = CourseId.random();
            assertEquals(4, id.version());
            assertEquals(2, id.variant());
            assertTrue(seen.add(id));
        }
        assertFalse(CourseId.usable(new UUID(0L, 0L)));
        assertFalse(CourseId.usable(null));
    }

    @Test
    void assignKeepsValidIdsAndRepairsMissingBrokenAndDuplicates() {
        List<CourseRegistry.Course> courses = new ArrayList<>(List.of(course(1, null), course(2, "kein-uuid"), course(3, A),
                course(4, A.toUpperCase()), course(5, "00000000-0000-0000-0000-000000000000"), course(6, A.replace("-", ""))));
        int assigned = CourseId.assign(courses, CourseId::random);
        assertEquals(5, assigned);
        assertEquals(UUID.fromString(A), courses.get(2).key());
        assertEquals(A, courses.get(2).uuid);
        Set<UUID> keys = new HashSet<>();
        for (CourseRegistry.Course course : courses) {
            assertTrue(keys.add(course.key()), "doppelte Kurs-ID bei Kurs " + course.id);
            assertEquals(course.key().toString(), course.uuid);
        }
        assertEquals(0, CourseId.assign(courses, CourseId::random), "zweites Laden vergibt nichts neu");
        assertEquals(UUID.fromString(A), courses.get(2).key());
    }

    @Test
    void assignSkipsCollisionsFromTheSource() {
        UUID taken = UUID.fromString(A);
        UUID next = UUID.fromString("9a7b3c2d-1e0f-4a5b-8c6d-7e8f9a0b1c2d");
        List<UUID> source = new ArrayList<>(List.of(taken, new UUID(0L, 0L), next));
        List<CourseRegistry.Course> courses = List.of(course(1, A), course(2, null));
        assertEquals(1, CourseId.assign(courses, source::removeFirst));
        assertEquals(next, courses.get(1).key());
    }

    @Test
    void networkCarriesTheCourseKey() {
        UUID key = UUID.fromString(A);
        CourseData data = new CourseData(7, "minecraft:overworld", List.of(new CourseData.Gate(0, 1.5, 64.0, 2.5, 0.0F,
                de.lardtom.propwash.race.GateSize.MEDIUM)), "Canyon", 3, null, "", key);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(data, decoded);
        assertEquals(key, decoded.key());
        assertEquals(key, decoded.withGates(List.of()).key(), "withGates behält die Kurs-ID");
        CourseData without = new CourseData(8, "minecraft:overworld", List.of());
        FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(second, without);
        assertNull(CourseData.STREAM_CODEC.decode(second).key());
        assertNotEquals(data.layoutHash(), 0);
        assertEquals(data.layoutHash(), new CourseData(7, "minecraft:overworld", data.gates(), "Canyon", 3, null, "").layoutHash(),
                "Die Kurs-ID gehört nicht zur Layout-Prüfsumme");
    }
}
