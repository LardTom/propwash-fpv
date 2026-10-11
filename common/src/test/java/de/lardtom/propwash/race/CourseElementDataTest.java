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

class CourseElementDataTest {
    private static List<CourseData.Gate> mixed() {
        List<CourseData.Gate> gates = new ArrayList<>();
        gates.add(new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM));
        gates.add(new CourseData.Gate(1, 0.5, 64.0, 8.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.POLE, CourseElement.RIGHT));
        gates.add(new CourseData.Gate(2, 0.5, 64.0, 14.5, 90.0F, GateSize.MEDIUM, GateMount.STANDING, false, new GateTilt(22.5F, 0.0F, 0.0F),
                6.0, 5.0, CourseElement.FLAG, CourseElement.LEFT));
        gates.add(new CourseData.Gate(3, 8.5, 64.0, 14.5, 180.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE,
                CourseElement.CUBE.outerWidth(4.0), CourseElement.CUBE.outerHeight(2.5), CourseElement.CUBE, CourseElement.EXIT_LEFT));
        return gates;
    }

    @Test
    void courseDataCarriesKindVariantAndSize() {
        CourseData data = new CourseData(4, "minecraft:overworld", mixed());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(data, decoded);
        assertEquals(data.geometry(), decoded.geometry());
        assertEquals(CourseElement.CUBE, decoded.gates().get(3).kind());
        assertEquals(CourseElement.EXIT_LEFT, decoded.gates().get(3).variant());
        assertEquals(4.5, decoded.gates().get(3).width(), 1.0E-9);
        assertEquals(6.0, decoded.gates().get(2).width(), 1.0E-9);
        assertEquals(22.5F, decoded.geometry().get(2).tilt().yaw(), 1.0E-6F);
    }

    @Test
    void elementsChangeTheLayoutButGatesKeepTheirHash() {
        List<CourseData.Gate> gates = mixed().subList(0, 1);
        int plain = new CourseData(1, "minecraft:overworld", gates).layoutHash();
        int before = new CourseData(1, "minecraft:overworld", List.of(new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING,
                false))).layoutHash();
        assertEquals(before, plain);
        List<CourseData.Gate> left = new ArrayList<>(mixed());
        List<CourseData.Gate> right = new ArrayList<>(mixed());
        right.set(1, new CourseData.Gate(1, 0.5, 64.0, 8.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.POLE, CourseElement.LEFT));
        assertNotEquals(new CourseData(1, "d", left).layoutHash(), new CourseData(1, "d", right).layoutHash());
    }

    @Test
    void elementsIgnoreMountsAndTilts() {
        CourseData.Gate gate = new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.LARGE, GateMount.WALL_DIVE, true, new GateTilt(10.0F, 30.0F, 40.0F),
                0.0, 0.0, CourseElement.POLE, 5);
        assertEquals(GateMount.STANDING, gate.mount());
        assertFalse(gate.flipped());
        assertEquals(new GateTilt(10.0F, 0.0F, 0.0F), gate.tilt());
        assertEquals(GateSize.MEDIUM, gate.size());
        assertEquals(CourseElement.RIGHT, gate.variant());
        assertEquals(CourseElement.POLE.defaultWidth(), gate.width(), 1.0E-9);
        assertEquals(CourseElement.POLE.defaultHeight(), gate.height(), 1.0E-9);
        assertTrue(gate.standardSize());
    }

    @Test
    void recordsStoreKindsOnlyForElements() {
        String json = "{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"index\":0,\"x\":4.5,\"y\":70.0,\"z\":-3.5,\"yaw\":180.0,"
                + "\"size\":\"medium\",\"slot\":-1}";
        Gson gson = new Gson();
        CourseRegistry.GateRecord gate = gson.fromJson(json, CourseRegistry.GateRecord.class);
        assertEquals(CourseElement.GATE, gate.kind());
        assertEquals(0, gate.variant());
        String written = gson.toJson(gate);
        assertFalse(written.contains("kind"), written);
        assertFalse(written.contains("variant"), written);
        CourseRegistry.GateRecord flag = gson.fromJson(json.replace("\"slot\"", "\"kind\":\"flag\",\"variant\":1,\"slot\""),
                CourseRegistry.GateRecord.class);
        assertEquals(CourseElement.FLAG, flag.kind());
        assertEquals(CourseElement.RIGHT, flag.variant());
        CourseRegistry.Course course = new CourseRegistry.Course();
        course.gates.add(gate);
        course.gates.add(flag);
        assertEquals(List.of(CourseElement.GATE, CourseElement.FLAG), course.kinds());
        assertEquals(4, course.checkpoints());
        CourseData data = course.toData();
        assertEquals(CourseElement.FLAG, data.gates().get(1).kind());
        assertEquals(CourseElement.FLAG.defaultWidth(), data.gates().get(1).width(), 1.0E-9);
        CourseRegistry.GateRecord unknown = gson.fromJson(json.replace("\"slot\"", "\"kind\":\"rocket\",\"slot\""), CourseRegistry.GateRecord.class);
        assertEquals(CourseElement.GATE, unknown.kind());
    }

    @Test
    void slalomSidesAlternate() {
        assertEquals(CourseElement.RIGHT, CourseElement.slalomSide(CourseElement.LEFT, CourseElement.LEFT));
        assertEquals(CourseElement.LEFT, CourseElement.slalomSide(CourseElement.RIGHT, CourseElement.RIGHT));
        assertEquals(CourseElement.RIGHT, CourseElement.slalomSide(CourseElement.LEFT, CourseElement.RIGHT));
        assertEquals(CourseElement.LEFT, CourseElement.slalomSide(CourseElement.RIGHT, CourseElement.LEFT));
    }

    @Test
    void placementStandsElementsOnTheFloorAndKeepsThemWhenTurning() {
        GatePlacement.Click click = new GatePlacement.Click(GateMount.Surface.FLOOR, 3, 63, 7, 0, 0, 3.4, 64.0, 7.6, 3.5, 64.0, 7.5, 100.0F);
        GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT.turnedBy(GateMount.Surface.FLOOR, 15.0F).nextVariant(CourseElement.CUBE);
        GateGeometry cube = GatePlacement.resolveElement(click, preference, CourseElement.CUBE);
        assertEquals(CourseElement.CUBE, cube.kind());
        assertEquals(1, cube.variant());
        assertEquals(90.0F, cube.yawDegrees(), 1.0E-6F);
        assertEquals(15.0F, cube.tilt().yaw(), 1.0E-6F);
        assertEquals(3.5, cube.x(), 1.0E-9);
        assertEquals(64.0, cube.y(), 1.0E-9);
        GateGeometry turned = GatePlacement.rotated(cube, false, false, 45.0F);
        assertEquals(CourseElement.CUBE, turned.kind());
        assertEquals(1, turned.variant());
        assertEquals(cube.heading() + 45.0F, turned.heading(), 1.0E-3F);
        GateGeometry switched = GatePlacement.rotated(cube, true, false, 45.0F);
        assertEquals(2, switched.variant());
        assertEquals(cube.heading(), switched.heading(), 1.0E-3F);
        GateGeometry edited = GatePlacement.edited(cube, 200.0F, 30.0F, 40.0F, CourseElement.CUBE.outerWidth(5.0), CourseElement.CUBE.outerHeight(2.0));
        assertEquals(CourseElement.CUBE, edited.kind());
        assertEquals(200.0F, edited.heading(), 1.0E-3F);
        assertFalse(edited.tilted());
        assertEquals(5.5, edited.width(), 1.0E-9);
        assertEquals(2.5, edited.height(), 1.0E-9);
        assertEquals(cube.x(), edited.x(), 1.0E-9);
        assertEquals(cube.z(), edited.z(), 1.0E-9);
    }

    @Test
    void elementsDoNotStack() {
        GateGeometry pole = GateGeometry.element(0.5, 64.0, 0.5, 0.0F, CourseElement.POLE, 0, 4.0, 3.0, GateTilt.NONE);
        GateGeometry gate = GateGeometry.of(0.5, 61.0, 0.5, 0.0F, GateSize.SMALL);
        assertFalse(GateStacking.stackable(pole));
        assertFalse(GateStacking.restsOn(pole, gate));
    }

    @Test
    void preferenceSendsTheVariant() {
        GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT.nextVariant(CourseElement.FLAG);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.GatePreferenceC2S.CODEC.encode(buf, new RacePayloads.GatePreferenceC2S(preference));
        RacePayloads.GatePreferenceC2S decoded = RacePayloads.GatePreferenceC2S.CODEC.decode(buf);
        assertEquals(preference, decoded.preference());
        assertEquals(CourseElement.RIGHT, decoded.preference().variant(CourseElement.FLAG));
        RacePayloads.GateEditC2S edit = new RacePayloads.GateEditC2S(5, RacePayloads.GateEditAction.APPLY, 90.0F, 0.0F, 0.0F, 3.0F, 3.0F, 3);
        FriendlyByteBuf editBuf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.GateEditC2S.CODEC.encode(editBuf, edit);
        assertEquals(edit, RacePayloads.GateEditC2S.CODEC.decode(editBuf));
    }
}
