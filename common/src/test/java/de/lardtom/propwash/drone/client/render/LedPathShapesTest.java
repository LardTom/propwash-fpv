package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.PartIndex;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedPathShapesTest {
    private static final float MM = DroneVisualScale.MM_TO_UNITS;
    private static final float EPS = 1.0E-4F;

    private static AccessoryType type(int leds, List<Vec3Mm> path) {
        return (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("rendertest", "led_" + leds))
                .basics(PartBasics.of(8.0)).slot(AccessorySlot.TOP).size(50.0, 4.0, 50.0).leds(leds).ledPath(path).build());
    }

    private static List<Vec3Mm> ring(int segments, double radius, double y) {
        List<Vec3Mm> out = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            out.add(new Vec3Mm(radius * Math.cos(angle), y, radius * Math.sin(angle)));
        }
        return out;
    }

    private static float center(Shape shape, int axis) {
        return (shape.min().get(axis) + shape.max().get(axis)) * 0.5F;
    }

    @Test
    void everyLedOfThePathGlowsWhereItSits() {
        AccessoryType ring = type(24, ring(48, 20.0, 7.0));
        Shape[] glows = FallbackShapes.buildStripLeds(ring, false);
        Shape[] halos = FallbackShapes.buildStripLeds(ring, true);
        assertEquals(24, glows.length);
        assertEquals(24, halos.length);
        for (int i = 0; i < glows.length; i++) {
            double[] led = ring.ledMm(i, ring.widthMm);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals((float) led[axis] * MM, center(glows[i], axis), EPS, "Leuchten LED " + i + " Achse " + axis);
                assertEquals((float) led[axis] * MM, center(halos[i], axis), EPS, "Halo LED " + i + " Achse " + axis);
            }
            assertEquals(FallbackShapes.STRIP_HALO_MM * 2.0F * MM, halos[i].max().x() - halos[i].min().x(), EPS);
        }
    }

    @Test
    void sixtyFourLedsAllLightUp() {
        AccessoryType full = type(64, List.of(new Vec3Mm(-200.0, 0.0, 0.0), new Vec3Mm(200.0, 0.0, 0.0)));
        assertEquals(64, FallbackShapes.buildStripLeds(full, false).length);
        AccessoryDefinition longStrip = AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("rendertest", "long_strip"))
                .basics(PartBasics.of(8.0)).slot(AccessorySlot.BOTTOM).size(120.0, 3.0, 8.0).leds(40).build();
        AccessoryType straight = (AccessoryType) PartIndex.detached(longStrip);
        Shape[] glows = FallbackShapes.buildStripLeds(straight, false);
        assertEquals(40, glows.length);
        assertEquals((float) straight.ledMm(39, straight.widthMm)[0] * MM, center(glows[39], 0), EPS, "letzte LED am Ende des Streifens");
    }

    @Test
    void stripBodyFollowsThePath() {
        List<Vec3Mm> arch = List.of(new Vec3Mm(-30.0, 6.0, 0.0), new Vec3Mm(0.0, 30.0, -10.0), new Vec3Mm(30.0, 6.0, 0.0));
        AccessoryType type = type(10, arch);
        Shape body = FallbackShapes.buildAccessory(type);
        assertFalse(body.isEmpty());
        float half = FallbackShapes.LED_PATH_STRIP_WIDTH_MM * 2.0F;
        Vector3fc min = body.min();
        Vector3fc max = body.max();
        assertTrue(min.x() > (-30.0F - half) * MM && max.x() < (30.0F + half) * MM, "Breite folgt dem Pfad");
        assertTrue(max.y() > 29.0F * MM && max.y() < (30.0F + half) * MM, "Scheitel des Bogens");
        assertTrue(min.y() > (6.0F - half) * MM, "keine Kiste unter dem Pfad: " + min.y() / MM);
        assertTrue(min.z() > (-10.0F - half) * MM && max.z() < half * MM, "Tiefe folgt dem Pfad, ohne Quader size_mm");
    }

    @Test
    void straightStripsLookAsBefore() {
        AccessoryType strip = AccessoryType.LED_STRIP;
        Shape[] glows = FallbackShapes.buildStripLeds(strip, false);
        Shape[] halos = FallbackShapes.buildStripLeds(strip, true);
        float pitch = (float) (strip.widthMm / strip.ledCount);
        assertEquals(strip.ledCount, glows.length);
        for (int i = 0; i < glows.length; i++) {
            double[] led = strip.ledMm(i, strip.widthMm);
            assertEquals((float) led[0] * MM, center(glows[i], 0), EPS);
            assertEquals(((float) led[1] - 0.2F) * MM, center(glows[i], 1), EPS);
            assertEquals(3.0F * MM, glows[i].max().x() - glows[i].min().x(), EPS);
            assertEquals(0.8F * MM, glows[i].max().y() - glows[i].min().y(), EPS);
            assertEquals(((float) led[1] - 0.6F) * MM, center(halos[i], 1), EPS);
            assertEquals(Math.max(FallbackShapes.STRIP_HALO_MM, pitch * 1.6F) * 2.0F * MM, halos[i].max().x() - halos[i].min().x(), EPS);
        }
        Shape body = FallbackShapes.buildAccessory(strip);
        double[] boxMin = strip.boxMinMm();
        double[] boxMax = strip.boxMaxMm();
        assertEquals((float) boxMin[0] * MM, body.min().x(), EPS);
        assertEquals((float) boxMax[0] * MM, body.max().x(), EPS);
        assertEquals(((float) -strip.heightMm - 0.4F) * MM, body.min().y(), EPS);
        assertEquals((float) boxMax[1] * MM, body.max().y(), EPS);
    }
}
