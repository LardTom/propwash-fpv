package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FreeformHeadingLayoutTest {
    private static final FreeformRegion HOLE = FreeformRegion.of(FreeformRegion.AXIS_Y, 3, 2, 1, 0, new boolean[]{
            true, true, true,
            true, true, true});
    private static final FreeformRegion SMALLER = FreeformRegion.of(FreeformRegion.AXIS_Y, 3, 2, 1, 0, new boolean[]{
            true, true, true,
            false, true, true});
    private static final FreeformRegion WINDOW = FreeformRegion.of(FreeformRegion.AXIS_Z, 3, 1, 1, 0, new boolean[]{true, true, true});

    private static CourseData course(float yaw, int variant, FreeformRegion region) {
        List<CourseData.Gate> gates = new ArrayList<>();
        gates.add(new CourseData.Gate(0, 11.5, 64.5, 21.0, yaw, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.FREEFORM, variant, region));
        gates.add(new CourseData.Gate(1, 0.5, 64.0, 30.5, 0.0F, GateSize.MEDIUM));
        return new CourseData(3, "minecraft:overworld", gates);
    }

    @Test
    void turningALyingFreeformGateKeepsTheLayout() {
        int legacy = course(0.0F, CourseElement.AWAY, HOLE).layoutHash();
        for (float yaw : new float[]{90.0F, 180.0F, 270.0F}) {
            assertEquals(legacy, course(yaw, CourseElement.AWAY, HOLE).layoutHash(),
                    "Ausrichtung " + yaw + "° dreht nur den Redstone-Ausgang, der Flugweg bleibt");
            GateGeometry turned = GateGeometry.freeform(11.5, 64.5, 21.0, HOLE, CourseElement.AWAY, ScoreArea.DEFAULT, yaw);
            GateGeometry plain = GateGeometry.freeform(11.5, 64.5, 21.0, HOLE, CourseElement.AWAY, ScoreArea.DEFAULT, 0.0F);
            assertEquals(plain.stages(), turned.stages(), "Durchflugfläche hängt nicht an der Ausrichtung (" + yaw + "°)");
        }
        assertNotEquals(legacy, course(0.0F, CourseElement.TOWARD, HOLE).layoutHash(), "Flugrichtung umkehren ändert den Kurs");
        assertNotEquals(legacy, course(90.0F, CourseElement.TOWARD, HOLE).layoutHash(), "Umkehren und Drehen zusammen ändern den Kurs");
        assertNotEquals(legacy, course(90.0F, CourseElement.AWAY, SMALLER).layoutHash(), "neue Öffnung ändert den Kurs");
    }

    @Test
    void yawStillCountsForEveryOtherGate() {
        assertNotEquals(course(0.0F, CourseElement.AWAY, WINDOW).layoutHash(), course(180.0F, CourseElement.AWAY, WINDOW).layoutHash(),
                "senkrechte Freiform-Gates unverändert");
        List<CourseData.Gate> south = List.of(new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM));
        List<CourseData.Gate> west = List.of(new CourseData.Gate(0, 0.5, 64.0, 0.5, 90.0F, GateSize.MEDIUM));
        assertNotEquals(new CourseData(1, "minecraft:overworld", south).layoutHash(), new CourseData(1, "minecraft:overworld", west).layoutHash(),
                "gedrehtes Gate ändert den Kurs");
        List<CourseData.Gate> pole = List.of(new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE,
                0.0, 0.0, CourseElement.POLE, 0));
        List<CourseData.Gate> turnedPole = List.of(new CourseData.Gate(0, 0.5, 64.0, 0.5, 90.0F, GateSize.MEDIUM, GateMount.STANDING, false,
                GateTilt.NONE, 0.0, 0.0, CourseElement.POLE, 0));
        assertNotEquals(new CourseData(1, "minecraft:overworld", pole).layoutHash(), new CourseData(1, "minecraft:overworld", turnedPole).layoutHash(),
                "gedrehte Slalomstange ändert den Kurs");
    }
}
