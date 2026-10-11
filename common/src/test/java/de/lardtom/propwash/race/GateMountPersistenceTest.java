package de.lardtom.propwash.race;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateMountPersistenceTest {
    private static List<CourseData.Gate> mixed() {
        List<CourseData.Gate> gates = new ArrayList<>();
        int index = 0;
        for (GateMount mount : GateMount.values()) {
            for (boolean flipped : new boolean[]{false, true}) {
                gates.add(new CourseData.Gate(index, index * 3.5, 64.25 + index, -index * 1.5, (index % 4) * 90.0F,
                        GateSize.values()[index % 3], mount, flipped));
                index++;
            }
        }
        return gates;
    }

    @Test
    void courseDataCarriesMountAndDirection() {
        CourseData data = new CourseData(7, "minecraft:overworld", mixed());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(data, decoded);
        assertEquals(data.geometry(), decoded.geometry());
        for (CourseData.Gate gate : decoded.gates()) {
            assertEquals(gate.mount().reversible() && gate.index() % 2 == 1, gate.flipped(), gate.toString());
        }
    }

    @Test
    void standingGatesKeepTheirLayoutHash() {
        List<CourseData.Gate> legacy = List.of(new CourseData.Gate(0, 1.5, 64.0, 2.5, 90.0F, GateSize.MEDIUM),
                new CourseData.Gate(1, 9.5, 64.0, 2.5, 270.0F, GateSize.LARGE));
        int hash = 17;
        for (CourseData.Gate gate : legacy) {
            hash = hash * 31 + gate.index();
            hash = hash * 31 + Long.hashCode(Math.round(gate.x() * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.y() * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.z() * 100.0));
            hash = hash * 31 + Math.round(gate.yaw() * 10.0F);
            hash = hash * 31 + gate.size().ordinal();
        }
        assertEquals(hash, new CourseData(1, "minecraft:overworld", legacy).layoutHash());
        List<CourseData.Gate> dive = new ArrayList<>(legacy);
        dive.set(1, new CourseData.Gate(1, 9.5, 64.0, 2.5, 270.0F, GateSize.LARGE, GateMount.WALL_DIVE, false));
        assertNotEquals(hash, new CourseData(1, "minecraft:overworld", dive).layoutHash());
        List<CourseData.Gate> launch = new ArrayList<>(legacy);
        launch.set(1, new CourseData.Gate(1, 9.5, 64.0, 2.5, 270.0F, GateSize.LARGE, GateMount.WALL_DIVE, true));
        assertNotEquals(new CourseData(1, "minecraft:overworld", dive).layoutHash(), new CourseData(1, "minecraft:overworld", launch).layoutHash());
    }

    @Test
    void oldCourseFilesLoadAsStandingGates() {
        String json = "{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"index\":2,\"x\":4.5,\"y\":70.0,\"z\":-3.5,\"yaw\":180.0,"
                + "\"size\":\"large\",\"slot\":-1}";
        CourseRegistry.GateRecord record = new Gson().fromJson(json, CourseRegistry.GateRecord.class);
        assertEquals(GateMount.STANDING, record.mount());
        assertFalse(record.flipped);
        CourseRegistry.Course course = new CourseRegistry.Course();
        course.id = 3;
        course.gates.add(record);
        CourseData data = course.toData();
        assertEquals(GateGeometry.of(4.5, 70.0, -3.5, 180.0F, GateSize.LARGE), data.gates().getFirst().geometry());
        CourseRegistry.GateRecord dive = new Gson().fromJson(json.replace("\"slot\"", "\"mount\":\"wall_dive\",\"flipped\":true,\"slot\""),
                CourseRegistry.GateRecord.class);
        assertEquals(GateMount.WALL_DIVE, dive.mount());
        assertTrue(dive.flipped);
        String written = new Gson().toJson(dive);
        assertTrue(written.contains("\"mount\":\"wall_dive\""), written);
        CourseRegistry.GateRecord unknown = new Gson().fromJson(json.replace("\"slot\"", "\"mount\":\"sideways\",\"slot\""),
                CourseRegistry.GateRecord.class);
        assertEquals(GateMount.STANDING, unknown.mount());
    }

    @Test
    void packedMountSurvivesTheByte() {
        for (GateMount mount : GateMount.values()) {
            for (boolean flipped : new boolean[]{false, true}) {
                int packed = GateMount.pack(mount, flipped) & 0xFF;
                assertEquals(mount, GateMount.unpackMount(packed));
                assertEquals(mount.reversible() && flipped, GateMount.unpackFlipped(packed));
                assertEquals(mount, GateMount.unpackMount((byte) packed));
            }
            assertEquals(mount, GateMount.byKey(mount.key()));
        }
        assertEquals(GateMount.STANDING, GateMount.byOrdinal(99));
    }
}
