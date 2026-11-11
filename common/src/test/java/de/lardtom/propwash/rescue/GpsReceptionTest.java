package de.lardtom.propwash.rescue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpsReceptionTest {
    private static final double X = 0.5;
    private static final double Y = 70.0;
    private static final double Z = 0.5;

    private static GpsReception.Rays roof(double above, double radius) {
        return (from, to) -> {
            double top = Y + above;
            if (to.y < top) {
                return true;
            }
            double t = (top - from.y) / (to.y - from.y);
            double x = from.x + (to.x - from.x) * t - X;
            double z = from.z + (to.z - from.z) * t - Z;
            return Math.hypot(x, z) > radius;
        };
    }

    @Test
    void openSkyHasAFix() {
        double sky = GpsReception.skyView((from, to) -> true, X, Y, Z);
        assertEquals(1.0, sky, 1e-9);
        assertTrue(GpsReception.fix(sky));
    }

    @Test
    void aStoneCeilingAboveTheDroneTakesTheFix() {
        double sky = GpsReception.skyView(roof(3.0, 12.0), X, Y, Z);
        assertEquals(0.0, sky, 1e-9, "Decke 3 m über der Drohne, 12 m nach allen Seiten");
        assertFalse(GpsReception.fix(sky));
        double small = GpsReception.skyView(roof(3.0, 3.0), X, Y, Z);
        assertTrue(small > 0.3 && small < 0.4, "nur der flache Ring sieht den Himmel: " + small);
        assertFalse(GpsReception.fix(small), "eine Decke von 6 m Breite reicht schon: " + small);
    }

    @Test
    void aWallOnOneSideKeepsTheFix() {
        double sky = GpsReception.skyView((from, to) -> to.y > from.y + 90.0 || to.x <= from.x + 1e-9, X, Y, Z);
        assertTrue(sky > 0.5 && sky < 0.7, "Wand im Osten: " + sky);
        assertTrue(GpsReception.fix(sky));
    }

    @Test
    void theFixThresholdMatchesThePilotsGps() {
        assertTrue(GpsReception.fix(1.0));
        assertTrue(GpsReception.fix(0.6), "der Empfänger des Piloten sieht hier mindestens 6 Satelliten");
        assertFalse(GpsReception.fix(0.3), "der Empfänger des Piloten sieht hier weniger als 5 Satelliten");
        assertFalse(GpsReception.fix(0.0));
        assertFalse(GpsReception.fix(Double.NaN));
        assertTrue(GpsReception.fix(Math.pow(5.0 / 18.0, 1.0 / 1.6) + 1e-6));
        assertFalse(GpsReception.fix(Math.pow(5.0 / 18.0, 1.0 / 1.6) - 1e-6));
    }

    @Test
    void raysStartAtTheDroneAndReachNinetySixMetres() {
        double[] longest = {0.0};
        int[] count = {0};
        GpsReception.skyView((from, to) -> {
            assertEquals(new Vec3(X, Y, Z), from);
            longest[0] = Math.max(longest[0], from.distanceTo(to));
            assertTrue(to.y > from.y, "Strahl zeigt nicht nach oben: " + to);
            count[0]++;
            return true;
        }, X, Y, Z);
        assertEquals(17, count[0]);
        assertEquals(GpsReception.RAY_LENGTH, longest[0], 1e-9);
    }
}
