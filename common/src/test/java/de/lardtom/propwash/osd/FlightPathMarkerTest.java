package de.lardtom.propwash.osd;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightPathMarkerTest {
    private static final float EPS = 1.0E-2F;
    private static final float CX = 240.0F;
    private static final float CY = 135.0F;
    private static final float W = 480.0F;
    private static final float H = 270.0F;
    private static final float MARGIN = 25.0F;
    private static final float LEFT = MARGIN;
    private static final float TOP = MARGIN;
    private static final float RIGHT = W - MARGIN;
    private static final float BOTTOM = H - MARGIN;

    private static OsdProjection flat(double uptiltDeg) {
        return new OsdProjection().setup(OsdProjection.cameraRotation(0.0, -uptiltDeg, 0.0, new Quaternionf()), 90.0F, CX, CY, H);
    }

    private static FlightPathMarker.State compute(OsdProjection projection, double vx, double vy, double vz) {
        return new FlightPathMarker().compute(vx, vy, vz, projection, LEFT, TOP, RIGHT, BOTTOM, new FlightPathMarker.State());
    }

    @Test
    void hoveringHidesTheMarker() {
        OsdProjection projection = flat(30.0);
        assertFalse(compute(projection, 0.3, -0.2, 0.4).visible);
        assertFalse(compute(projection, 0.0, 0.0, 0.0).visible);
        assertFalse(compute(projection, Double.NaN, 0.0, 20.0).visible);
        assertFalse(compute(projection, 0.0, 0.0, 0.99).visible);
    }

    @Test
    void straightLevelFlightSitsBelowTheCentreByTheUptilt() {
        OsdProjection projection = flat(30.0);
        FlightPathMarker.State state = compute(projection, 0.0, 0.0, 20.0);
        assertTrue(state.visible);
        assertTrue(state.inView);
        assertEquals(1.0F, state.alpha, 1.0E-6F);
        assertEquals(CX, state.x, EPS);
        assertEquals(CY + projection.focal() * (float) Math.tan(Math.toRadians(30.0)), state.y, EPS);
        FlightPathMarker.State level = compute(flat(0.0), 0.0, 0.0, 20.0);
        assertEquals(CX, level.x, EPS);
        assertEquals(CY, level.y, EPS);
    }

    @Test
    void sidewaysDriftMovesTheMarkerLeftOrRight() {
        OsdProjection projection = flat(0.0);
        FlightPathMarker.State right = compute(projection, -5.0, 0.0, 20.0);
        FlightPathMarker.State left = compute(projection, 5.0, 0.0, 20.0);
        assertTrue(right.inView && left.inView);
        assertTrue(right.x > CX + 1.0F, "Drift nach -X liegt rechts: " + right.x);
        assertTrue(left.x < CX - 1.0F, "Drift nach +X liegt links: " + left.x);
        assertEquals(CX - right.x, left.x - CX, EPS);
        assertEquals(projection.focal() * 0.25F, right.x - CX, EPS);
        FlightPathMarker.State climbing = compute(projection, 0.0, 5.0, 20.0);
        assertTrue(climbing.y < CY - 1.0F, "Steigen liegt über der Mitte: " + climbing.y);
    }

    @Test
    void backwardsFlightGoesToTheEdgeAsOutline() {
        OsdProjection projection = flat(0.0);
        FlightPathMarker.State back = compute(projection, 3.0, -1.0, -20.0);
        assertTrue(back.visible);
        assertFalse(back.inView);
        assertOnEdge(back);
        assertEquals(projection.screenAngle(3.0, -1.0, -20.0), back.edgeAngle, 1.0E-5F);
        FlightPathMarker.State wide = compute(projection, 0.0, 0.0, 0.0);
        assertFalse(wide.visible);
        FlightPathMarker.State steep = compute(projection, 0.0, -30.0, 2.0);
        assertTrue(steep.visible);
        assertFalse(steep.inView, "fast senkrechter Sinkflug liegt außerhalb des Bilds");
        assertOnEdge(steep);
        assertTrue(steep.y > CY, "Sinkflug liegt am unteren Rand: " + steep.y);
    }

    private static void assertOnEdge(FlightPathMarker.State state) {
        float rx = (RIGHT - LEFT) * 0.5F;
        float ry = (BOTTOM - TOP) * 0.5F;
        float ex = (state.x - (LEFT + RIGHT) * 0.5F) / rx;
        float ey = (state.y - (TOP + BOTTOM) * 0.5F) / ry;
        assertEquals(1.0F, ex * ex + ey * ey, 1.0E-3F, "Marker liegt auf der Rand-Ellipse");
    }

    @Test
    void fisheyeMatchesTheProjection() {
        Quaternionf rotation = OsdProjection.cameraRotation(20.0, -25.0, 10.0, new Quaternionf());
        OsdProjection fisheye = new OsdProjection().setupFisheye(rotation, 150.0F, 0.4F, CX, CY, W);
        Vector2f expected = new Vector2f();
        double[][] cases = {{0.0, 0.0, 20.0}, {6.0, -2.0, 18.0}, {-9.0, 3.0, 12.0}, {4.0, -8.0, 15.0}};
        for (double[] v : cases) {
            assertTrue(fisheye.project(v[0], v[1], v[2], expected));
            FlightPathMarker.State state = compute(fisheye, v[0], v[1], v[2]);
            if (state.inView) {
                assertEquals(expected.x, state.x, EPS);
                assertEquals(expected.y, state.y, EPS);
            } else {
                assertOnEdge(state);
            }
        }
        OsdProjection straight = new OsdProjection().setupFisheye(new Quaternionf(), 150.0F, 0.4F, CX, CY, W);
        FlightPathMarker.State ahead = compute(straight, 0.0, 0.0, -20.0);
        assertTrue(ahead.inView);
        assertEquals(CX, ahead.x, EPS);
        assertEquals(CY, ahead.y, EPS);
        FlightPathMarker.State behind = compute(straight, 0.0, -2.0, 20.0);
        assertFalse(behind.inView);
        assertOnEdge(behind);
    }

    @Test
    void fadesInBetweenOneAndTwoMetresPerSecond() {
        assertEquals(0.0F, FlightPathMarker.alpha(0.0));
        assertEquals(0.0F, FlightPathMarker.alpha(0.99));
        assertEquals(0.0F, FlightPathMarker.alpha(1.0));
        assertEquals(0.25F, FlightPathMarker.alpha(1.25), 1.0E-6F);
        assertEquals(0.5F, FlightPathMarker.alpha(1.5), 1.0E-6F);
        assertEquals(1.0F, FlightPathMarker.alpha(2.0));
        assertEquals(1.0F, FlightPathMarker.alpha(40.0));
        assertEquals(0.0F, FlightPathMarker.alpha(Double.NaN));
        OsdProjection projection = flat(0.0);
        FlightPathMarker.State half = compute(projection, 0.0, 0.0, 1.5);
        assertTrue(half.visible);
        assertEquals(0.5F, half.alpha, 1.0E-6F);
        assertEquals(CX, half.x, EPS);
    }

    @Test
    void invalidProjectionOrBoundsHideTheMarker() {
        OsdProjection broken = new OsdProjection().setup(new Quaternionf(0.0F, 0.0F, 0.0F, 0.0F), 90.0F, CX, CY, H);
        assertFalse(broken.valid());
        assertFalse(compute(broken, 0.0, 0.0, 20.0).visible);
        FlightPathMarker.State state = new FlightPathMarker().compute(0.0, 0.0, 20.0, flat(0.0), 10.0F, 10.0F, 10.0F, 50.0F,
                new FlightPathMarker.State());
        assertFalse(state.visible);
    }

    @Test
    void velocityIsTheDerivativeOfTheShownPoses() {
        FlightPathMarker.Velocity velocity = new FlightPathMarker.Velocity();
        assertFalse(velocity.known());
        long span = FlightPathMarker.SPAN_NANOS;
        double seconds = span * 1.0E-9;
        assertTrue(velocity.update(1_000_000_000L, 12.0 * seconds, -1.0 * seconds, -5.0 * seconds, span));
        assertTrue(velocity.known());
        assertEquals(12.0, velocity.x(), 1.0E-9);
        assertEquals(-1.0, velocity.y(), 1.0E-9);
        assertEquals(-5.0, velocity.z(), 1.0E-9);
        for (int frame = 1; frame <= 60; frame++) {
            assertTrue(velocity.update(1_000_000_000L + frame * 16_666_667L, 12.0 * seconds, -1.0 * seconds, -5.0 * seconds, span));
        }
        assertEquals(12.0, velocity.x(), 1.0E-9);
        assertEquals(-5.0, velocity.z(), 1.0E-9);
    }

    @Test
    void smoothingIsAFirstOrderLagOfFortyMilliseconds() {
        FlightPathMarker.Velocity velocity = new FlightPathMarker.Velocity();
        long span = FlightPathMarker.SPAN_NANOS;
        double seconds = span * 1.0E-9;
        long start = 5_000_000_000L;
        velocity.update(start, 0.0, 0.0, 0.0, span);
        long step = 1_000_000L;
        long tauNanos = Math.round(FlightPathMarker.SMOOTHING_SECONDS * 1.0E9);
        for (long t = step; t <= tauNanos; t += step) {
            velocity.update(start + t, 10.0 * seconds, 0.0, 0.0, span);
        }
        assertEquals(10.0 * (1.0 - Math.exp(-1.0)), velocity.x(), 1.0E-6);
        for (long t = tauNanos + step; t <= 10 * tauNanos; t += step) {
            velocity.update(start + t, 10.0 * seconds, 0.0, 0.0, span);
        }
        assertEquals(10.0, velocity.x(), 1.0E-3);
        double before = velocity.x();
        velocity.update(start + 10 * tauNanos, 0.0, 0.0, 0.0, span);
        assertEquals(before, velocity.x(), 1.0E-12, "gleicher Bildzeitpunkt ändert nichts");
    }

    @Test
    void jumpsAndGapsRestartTheEstimate() {
        FlightPathMarker.Velocity velocity = new FlightPathMarker.Velocity();
        long span = FlightPathMarker.SPAN_NANOS;
        double seconds = span * 1.0E-9;
        assertTrue(velocity.update(0L, 10.0 * seconds, 0.0, 0.0, span));
        assertFalse(velocity.update(16_000_000L, 9.0, 0.0, 0.0, span), "9 Blöcke in 50 ms sind ein Sprung, keine Bewegung");
        assertFalse(velocity.known());
        assertTrue(Double.isNaN(velocity.x()));
        assertTrue(velocity.update(32_000_000L, 4.0 * seconds, 0.0, 0.0, span));
        assertEquals(4.0, velocity.x(), 1.0E-9);
        long later = 32_000_000L + Math.round((FlightPathMarker.RESTART_SECONDS + 0.1) * 1.0E9);
        assertTrue(velocity.update(later, -6.0 * seconds, 0.0, 0.0, span));
        assertEquals(-6.0, velocity.x(), 1.0E-9, "nach einer Pause neu, ohne alte Glättung");
        assertTrue(velocity.update(later - 1_000L, 2.0 * seconds, 0.0, 0.0, span));
        assertEquals(2.0, velocity.x(), 1.0E-9, "rückwärts laufende Zeit startet neu");
        assertFalse(velocity.update(later, 1.0, 0.0, 0.0, 0L));
        assertFalse(velocity.known());
        assertFalse(velocity.update(later, Double.NaN, 0.0, 0.0, span));
    }

    @Test
    void osdDataCarriesTheVelocity() {
        OsdData sample = OsdData.sample();
        assertTrue(sample.hasVelocity());
        double horizontal = Math.hypot(sample.velocityX, sample.velocityZ);
        assertEquals(18.0, horizontal, 0.05);
        assertTrue(sample.velocityY < 0.0 && sample.velocityY > -2.0, "leicht sinkend");
        assertEquals(sample.compassDeg, OsdFormat.bearing(sample.velocityX, sample.velocityZ), 0.5);
        OsdData copy = new OsdData();
        assertFalse(copy.hasVelocity());
        sample.copyTo(copy);
        assertEquals(sample.velocityX, copy.velocityX);
        assertEquals(sample.velocityY, copy.velocityY);
        assertEquals(sample.velocityZ, copy.velocityZ);
        copy.clearVelocity();
        assertFalse(copy.hasVelocity());
        sample.reset();
        assertFalse(sample.hasVelocity());
    }

    @Test
    void elementIsOffExceptInTheFullPreset() {
        OsdElement element = OsdElement.FLIGHT_PATH_MARKER;
        assertEquals("flight_path_marker", element.key());
        assertEquals("propwash.osd.element.flight_path_marker", element.translationKey());
        assertEquals(element, OsdElement.byKey("flight_path_marker"));
        assertEquals(OsdElement.Group.FLIGHT, element.group());
        assertEquals(OsdElement.Align.CENTER, element.align());
        assertEquals(15, element.baseCol());
        assertEquals(8, element.baseRow());
        assertFalse(element.classicEnabled());
        assertFalse(element.movable());
        assertTrue(element.usesLineOpacity());
        for (OsdPreset preset : OsdPreset.values()) {
            assertEquals(preset == OsdPreset.FULL, preset.enabled(element), preset.key());
        }
        assertFalse(OsdConfig.defaults().enabled(element));
    }

    @Test
    void oldLayoutsWithoutTheKeyKeepItOff() {
        for (OsdPreset preset : OsdPreset.values()) {
            OsdConfig config = OsdConfig.defaults();
            config.applyPreset(preset);
            Map<String, OsdConfig.Slot> old = new LinkedHashMap<>(config.elements);
            old.remove(OsdElement.FLIGHT_PATH_MARKER.key());
            config.elements = old;
            OsdConfig.sanitize(config);
            assertEquals(preset == OsdPreset.FULL, config.enabled(OsdElement.FLIGHT_PATH_MARKER), preset.key());
            assertNotNull(config.elements.get(OsdElement.FLIGHT_PATH_MARKER.key()));
        }
        OsdConfig on = OsdConfig.defaults();
        on.slot(OsdElement.FLIGHT_PATH_MARKER).enabled = true;
        OsdConfig decoded = OsdLayoutCodec.decode(OsdLayoutCodec.encode(on), OsdConfig.defaults());
        assertTrue(decoded.enabled(OsdElement.FLIGHT_PATH_MARKER));
        OsdConfig legacy = OsdLayoutCodec.decode("{\"v\":1,\"preset\":\"standard\",\"elements\":{\"crosshair\":{\"on\":true}}}", OsdConfig.defaults());
        assertFalse(legacy.enabled(OsdElement.FLIGHT_PATH_MARKER));
    }
}
