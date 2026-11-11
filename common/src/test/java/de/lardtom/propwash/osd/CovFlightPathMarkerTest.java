package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.FovMath;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovFlightPathMarkerTest {
    private static final float EPS = 1.0E-2F;
    private static final float H = 270.0F;
    private static final float MARGIN = 25.0F;
    private static final double HORIZONTAL_FOV = 150.0;
    private static final double CRUISE_PITCH = 20.0;

    private record Frame(float width, float height) {
        float cx() {
            return this.width * 0.5F;
        }

        float cy() {
            return this.height * 0.5F;
        }

        float left() {
            return MARGIN;
        }

        float top() {
            return MARGIN;
        }

        float right() {
            return this.width - MARGIN;
        }

        float bottom() {
            return this.height - MARGIN;
        }
    }

    private static final Frame WIDE = new Frame(480.0F, H);
    private static final Frame CLASSIC = new Frame(360.0F, H);

    private static OsdProjection flat(Frame frame, double uptiltDeg, float verticalFov) {
        return new OsdProjection().setup(OsdProjection.cameraRotation(0.0, -uptiltDeg, 0.0, new Quaternionf()), verticalFov, frame.cx(),
                frame.cy(), frame.height());
    }

    private static OsdProjection lens(Frame frame, Quaternionf rotation, double strength) {
        FisheyeLens lens = FisheyeLens.of(HORIZONTAL_FOV, strength, frame.width(), frame.height(), frame.width(), frame.height());
        return new OsdProjection().setupFisheye(rotation, (float) lens.horizontalDeg(), (float) lens.bend(), frame.cx(), frame.cy(), frame.width());
    }

    private static FlightPathMarker.State compute(Frame frame, OsdProjection projection, double vx, double vy, double vz) {
        return new FlightPathMarker().compute(vx, vy, vz, projection, frame.left(), frame.top(), frame.right(), frame.bottom(),
                new FlightPathMarker.State());
    }

    private static void assertOnEdge(Frame frame, FlightPathMarker.State state) {
        float rx = (frame.right() - frame.left()) * 0.5F;
        float ry = (frame.bottom() - frame.top()) * 0.5F;
        float ex = (state.x - (frame.left() + frame.right()) * 0.5F) / rx;
        float ey = (state.y - (frame.top() + frame.bottom()) * 0.5F) / ry;
        assertEquals(1.0F, ex * ex + ey * ey, 1.0E-3F, "Marker liegt auf der Rand-Ellipse");
    }

    private static double freestyleFov() {
        return FovMath.cameraHorizontalDegrees(DroneBuild.FREESTYLE.video());
    }

    private static OsdProjection camera(Frame frame, double uptiltDeg, double strength) {
        Quaternionf rotation = OsdProjection.cameraRotation(0.0, -uptiltDeg, 0.0, new Quaternionf());
        double fov = freestyleFov();
        if (strength <= 0.0) {
            float vertical = FovMath.verticalFromHorizontal(fov, frame.width() / frame.height());
            return new OsdProjection().setup(rotation, vertical, frame.cx(), frame.cy(), frame.height());
        }
        FisheyeLens lens = FisheyeLens.of(fov, strength, frame.width(), frame.height(), frame.width(), frame.height());
        return new OsdProjection().setupFisheye(rotation, (float) lens.horizontalDeg(), (float) lens.bend(), frame.cx(), frame.cy(), frame.width());
    }

    @Test
    void straightLevelFlightSitsLowerAtFortyFiveThanAtThirtyDegrees() {
        for (double strength : new double[]{0.0, 0.8, 1.0}) {
            for (Frame frame : new Frame[]{CLASSIC, WIDE}) {
                for (double bodyPitch : new double[]{0.0, CRUISE_PITCH}) {
                    FlightPathMarker.State level = compute(frame, camera(frame, -bodyPitch, strength), 0.0, 0.0, 20.0);
                    FlightPathMarker.State at30 = compute(frame, camera(frame, 30.0 - bodyPitch, strength), 0.0, 0.0, 20.0);
                    FlightPathMarker.State at45 = compute(frame, camera(frame, 45.0 - bodyPitch, strength), 0.0, 0.0, 20.0);
                    String label = String.format(Locale.ROOT, "Freestyle-Kamera %.0f°, Fisheye %.0f %%, Bild %.0f × %.0f, Rahmen %.0f° nach vorn: "
                                    + "0° %.1f%s, 30° %.1f%s, 45° %.1f%s", freestyleFov(), strength * 100.0, frame.width(), frame.height(), bodyPitch,
                            level.y, edge(level), at30.y, edge(at30), at45.y, edge(at45));
                    assertTrue(level.visible && at30.visible && at45.visible, label);
                    assertEquals(frame.cx(), at30.x, 0.5F, label);
                    assertEquals(frame.cx(), at45.x, 0.5F, label);
                    if (bodyPitch == 0.0) {
                        assertTrue(level.inView, label);
                        assertEquals(frame.cy(), level.y, EPS, "0° sitzt auf dem Fadenkreuz: " + label);
                    } else {
                        assertTrue(level.inView && at30.inView && at45.inView, "im schnellen Vorwärtsflug alle drei im Bild: " + label);
                    }
                    assertTrue(at30.y > level.y + 0.2F * (frame.bottom() - frame.cy()), "30° deutlich darunter: " + label);
                    if (at30.inView) {
                        assertTrue(at45.y > at30.y + 5.0F, "45° noch tiefer: " + label);
                    } else {
                        assertEquals(frame.bottom(), at30.y, 0.5F, label);
                    }
                    if (!at45.inView) {
                        assertEquals(frame.bottom(), at45.y, 0.5F, "außerhalb des Bilds wartet er unten am Rand: " + label);
                    }
                }
            }
        }
    }

    private static String edge(FlightPathMarker.State state) {
        return state.inView ? "" : " (Rand)";
    }

    @Test
    void steepDiveSitsLowAndWaitsDashedAtTheBottom() {
        for (Frame frame : new Frame[]{CLASSIC, WIDE}) {
            OsdProjection fisheye = camera(frame, 30.0, FisheyeLens.DEFAULT_STRENGTH);
            FlightPathMarker.State level = compute(frame, fisheye, 0.0, 0.0, 20.0);
            float previous = level.y;
            for (double diveDeg : new double[]{2.0, 4.0}) {
                double dive = Math.toRadians(diveDeg);
                FlightPathMarker.State steep = compute(frame, fisheye, 0.0, -20.0 * Math.sin(dive), 20.0 * Math.cos(dive));
                String label = String.format(Locale.ROOT, "Bild %.0f × %.0f, Sinkflug %.0f° bei 30° Uptilt: y %.1f (Mitte %.1f, unten %.1f)",
                        frame.width(), frame.height(), diveDeg, steep.y, frame.cy(), frame.bottom());
                assertTrue(steep.visible, label);
                assertTrue(steep.y > previous || !steep.inView && steep.y >= previous - 0.5F, "steiler liegt tiefer: " + label);
                if (steep.inView) {
                    assertTrue(steep.y > frame.cy() + 0.6F * (frame.bottom() - frame.cy()), "weit unten im Bild: " + label);
                } else {
                    assertEquals(frame.bottom(), steep.y, 0.5F, label);
                }
                previous = steep.y;
            }
            for (OsdProjection projection : new OsdProjection[]{fisheye, camera(frame, 30.0, 0.0), flat(frame, 30.0, 90.0F)}) {
                FlightPathMarker.State vertical = compute(frame, projection, 0.0, -20.0, 1.0);
                assertTrue(vertical.visible, "fast senkrecht nach unten sichtbar");
                assertFalse(vertical.inView, "Richtung außerhalb des Bilds wartet am Rand (gestrichelt)");
                assertOnEdge(frame, vertical);
                assertEquals(frame.cx(), vertical.x, 0.5F, "unten in der Mitte");
                assertEquals(frame.bottom(), vertical.y, 0.5F, "am unteren Rand");
                assertTrue(Math.sin(vertical.edgeAngle) > 0.99, "Richtung nach unten: " + vertical.edgeAngle);
                assertEquals(projection.screenAngle(0.0, -20.0, 1.0), vertical.edgeAngle, 1.0E-5F);
                FlightPathMarker.State straightDown = compute(frame, projection, 0.0, -20.0, 0.0);
                assertTrue(straightDown.visible && !straightDown.inView);
                assertEquals(frame.bottom(), straightDown.y, 0.5F);
            }
        }
    }

    static Stream<Arguments> lenses() {
        List<Arguments> out = new ArrayList<>();
        for (double strength : new double[]{0.0, 0.8, 1.0}) {
            for (Frame frame : new Frame[]{CLASSIC, WIDE}) {
                out.add(Arguments.of(strength, frame));
            }
        }
        return out.stream();
    }

    private static OsdProjection rendered(Frame frame, Quaternionf rotation, double strength) {
        if (strength <= 0.0) {
            double vertical = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(HORIZONTAL_FOV * 0.5)) * frame.height() / frame.width()));
            return new OsdProjection().setup(rotation, (float) Math.min(vertical, 120.0), frame.cx(), frame.cy(), frame.height());
        }
        return lens(frame, rotation, strength);
    }

    @ParameterizedTest
    @MethodSource("lenses")
    void fisheyeMatchesTheProjectionAlsoNearTheEdge(double strength, Frame frame) {
        Quaternionf rotation = OsdProjection.cameraRotation(20.0, -25.0, 10.0, new Quaternionf());
        OsdProjection projection = rendered(frame, rotation, strength);
        Vector2f expected = new Vector2f();
        Vector3f forward = rotation.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
        Vector3f right = rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f());
        Vector3f up = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f());
        int inside = 0;
        int edge = 0;
        for (double side = -0.95; side <= 0.951; side += 0.19) {
            for (double lift = -0.95; lift <= 0.951; lift += 0.19) {
                Vector2f target = new Vector2f(frame.cx() + (float) side * (frame.right() - frame.cx()),
                        frame.cy() - (float) lift * (frame.cy() - frame.top()));
                Vector3f direction = direction(projection, frame, forward, right, up, target);
                if (direction == null) {
                    continue;
                }
                double speed = 15.0;
                double vx = direction.x * speed;
                double vy = direction.y * speed;
                double vz = direction.z * speed;
                assertTrue(projection.project(vx, vy, vz, expected));
                FlightPathMarker.State state = compute(frame, projection, vx, vy, vz);
                String label = String.format(Locale.ROOT, "Stärke %.0f %%, Bild %.0f × %.0f, Ziel %.1f/%.1f", strength * 100.0, frame.width(),
                        frame.height(), target.x, target.y);
                assertTrue(state.visible, label);
                if (state.inView) {
                    inside++;
                    assertEquals(expected.x, state.x, EPS, label);
                    assertEquals(expected.y, state.y, EPS, label);
                    assertEquals(target.x, state.x, 0.5F, label);
                    assertEquals(target.y, state.y, 0.5F, label);
                } else {
                    edge++;
                    assertOnEdge(frame, state);
                }
            }
        }
        assertTrue(inside >= 80, "randnahe Punkte im Bild: " + inside + ", am Rand " + edge);
        if (strength > 0.0) {
            FisheyeLens lens = FisheyeLens.of(HORIZONTAL_FOV, strength, frame.width(), frame.height(), frame.width(), frame.height());
            assertEquals(lens.focal(), projection.focal(), 1.0E-2, "Brennweite des Overlays = Brennweite des gerenderten Objektivs");
            for (double thetaDeg : new double[]{10.0, 40.0, 70.0}) {
                double theta = Math.toRadians(thetaDeg);
                Vector3f ray = new Vector3f(forward).mul((float) Math.cos(theta)).add(new Vector3f(right).mul((float) Math.sin(theta)));
                assertTrue(projection.project(ray.x, ray.y, ray.z, expected));
                double radius = Math.hypot(expected.x - frame.cx(), expected.y - frame.cy());
                assertEquals(lens.focal() * FisheyeLens.map(theta, lens.bend()), radius, 0.05,
                        String.format(Locale.ROOT, "Abbildung bei %.0f° wie im Objektiv", thetaDeg));
            }
        }
    }

    private static Vector3f direction(OsdProjection projection, Frame frame, Vector3f forward, Vector3f right, Vector3f up, Vector2f target) {
        Vector2f probe = new Vector2f();
        double yaw = 0.0;
        double pitch = 0.0;
        for (int i = 0; i < 60; i++) {
            Vector3f d = ray(forward, right, up, yaw, pitch);
            if (!projection.project(d.x, d.y, d.z, probe)) {
                return null;
            }
            double ex = target.x - probe.x;
            double ey = target.y - probe.y;
            if (Math.abs(ex) < 1.0E-3 && Math.abs(ey) < 1.0E-3) {
                return d;
            }
            double h = 1.0E-4;
            Vector2f px = new Vector2f();
            Vector2f py = new Vector2f();
            Vector3f dx = ray(forward, right, up, yaw + h, pitch);
            Vector3f dy = ray(forward, right, up, yaw, pitch + h);
            if (!projection.project(dx.x, dx.y, dx.z, px) || !projection.project(dy.x, dy.y, dy.z, py)) {
                return null;
            }
            double a = (px.x - probe.x) / h;
            double b = (py.x - probe.x) / h;
            double c = (px.y - probe.y) / h;
            double e = (py.y - probe.y) / h;
            double det = a * e - b * c;
            if (Math.abs(det) < 1.0E-9) {
                return null;
            }
            yaw += (e * ex - b * ey) / det;
            pitch += (-c * ex + a * ey) / det;
            yaw = Math.clamp(yaw, -1.5, 1.5);
            pitch = Math.clamp(pitch, -1.5, 1.5);
        }
        return null;
    }

    private static Vector3f ray(Vector3f forward, Vector3f right, Vector3f up, double yaw, double pitch) {
        return new Vector3f(forward).add(new Vector3f(right).mul((float) Math.tan(yaw))).add(new Vector3f(up).mul((float) Math.tan(pitch))).normalize();
    }
}
