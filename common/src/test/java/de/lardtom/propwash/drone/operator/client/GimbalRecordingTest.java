package de.lardtom.propwash.drone.operator.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.video.record.CameraLook;
import de.lardtom.propwash.video.record.Stabilizer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalRecordingTest {
    @Test
    void gimbalPoseReplacesTheRolledBodyInTheCameraPath() {
        Quaternionf body = new Quaternionf().rotateY((float) Math.toRadians(40.0)).rotateZ((float) Math.toRadians(30.0));
        Quaternionf rigid = CameraLook.pathRotation(body, 20.0F, null, new Quaternionf());
        assertTrue(CameraLook.imageRollDegrees(rigid) > 25.0, "ohne Gimbal rollt das Bild mit der Drohne: " + CameraLook.imageRollDegrees(rigid));
        Quaternionf level = new Quaternionf().rotateY((float) Math.toRadians(40.0)).rotateX((float) Math.toRadians(-15.0));
        Quaternionf stable = CameraLook.pathRotation(body, 20.0F, level, new Quaternionf());
        assertTrue(CameraLook.imageRollDegrees(stable) < 2.0, "Gimbal hält den Horizont: " + CameraLook.imageRollDegrees(stable));
        Quaternionf same = new Quaternionf(body);
        CameraLook.pathRotation(same, 0.0F, null, same);
        assertEquals(body.w, same.w, 1.0E-6F);
    }

    @Test
    void gimbalCameraFeedsThePathWhateverTheScreenShows() {
        Quaternionf body = new Quaternionf().rotateY((float) Math.toRadians(40.0)).rotateZ((float) Math.toRadians(30.0));
        Quaternionf level = new Quaternionf().rotateY((float) Math.toRadians(40.0)).rotateX((float) Math.toRadians(-15.0));
        Quaternionf seen = new Quaternionf();
        GimbalProvider provider = new Fixed(true) {
            @Override
            public boolean gimbalRotation(GimbalContext context, Quaternionf rotation) {
                seen.set(context.frame());
                rotation.set(level).scale(2.0F);
                return true;
            }
        };
        Quaternionf camera = GimbalRecording.camera(provider, context(body));
        assertNotNull(camera);
        assertEquals(body, seen, "der Gimbal bekommt die Rahmenlage des Kamerapfads");
        assertTrue(degrees(camera, level) < 0.01, "Lage der Gimbal-Kamera, normiert: " + degrees(camera, level));
        Quaternionf path = CameraLook.pathRotation(body, 20.0F, camera, new Quaternionf());
        assertTrue(CameraLook.imageRollDegrees(path) < 2.0, "Kamerapfad hält den Horizont: " + CameraLook.imageRollDegrees(path));
    }

    @Test
    void withoutAGimbalCameraThePathKeepsItsOldSource() {
        Quaternionf body = new Quaternionf().rotateZ((float) Math.toRadians(30.0));
        assertNull(GimbalRecording.camera(new Fixed(true), context(body)), "Standard: keine eigene Lage");
        assertNull(GimbalRecording.camera(rotation(false, new Quaternionf().rotateX(0.3F)), context(body)), "false gilt auch mit gesetzter Lage");
        assertNull(GimbalRecording.camera(rotation(true, new Quaternionf(Float.NaN, 0.0F, 0.0F, 1.0F)), context(body)), "nicht endlich");
        assertNull(GimbalRecording.camera(rotation(true, new Quaternionf(0.0F, 0.0F, 0.0F, 0.0F)), context(body)), "Länge 0");
        assertNull(GimbalRecording.camera(new Fixed(true) {
            @Override
            public boolean gimbalRotation(GimbalContext context, Quaternionf rotation) {
                throw new IllegalStateException("Test");
            }
        }, context(body)), "Ausnahme im Add-on");
        Quaternionf rigid = CameraLook.pathRotation(body, 20.0F, null, new Quaternionf());
        assertTrue(CameraLook.imageRollDegrees(rigid) > 25.0, "ohne Gimbal-Kamera rollt der Pfad wie bisher mit");
    }

    @Test
    void gimbalFocalLengthFeedsThePathWhateverTheScreenShows() {
        Quaternionf body = new Quaternionf().rotateY((float) Math.toRadians(40.0)).rotateZ((float) Math.toRadians(30.0));
        Quaternionf seen = new Quaternionf();
        GimbalProvider zoomed = new Fixed(true) {
            @Override
            public double fovDegrees(GimbalContext context) {
                return Double.NaN;
            }

            @Override
            public double gimbalFovDegrees(GimbalContext context) {
                seen.set(context.frame());
                return 35.0;
            }
        };
        assertEquals(35.0, GimbalFov.camera(zoomed, context(body)), "Brennweite der Gimbal-Kamera, auch wenn der Schirm das FPV-Bild zeigt");
        assertEquals(body, seen, "der Gimbal bekommt die Rahmenlage des Kamerapfads");
        assertEquals(GimbalProvider.MIN_FOV_DEGREES, GimbalFov.camera(focal(5.0), context(body)));
        assertEquals(GimbalProvider.MAX_FOV_DEGREES, GimbalFov.camera(focal(179.0), context(body)));
    }

    @Test
    void withoutAGimbalFocalLengthThePathKeepsTheScreenFov() {
        GimbalContext context = context(new Quaternionf());
        assertTrue(Double.isNaN(GimbalFov.camera(new Fixed(true), context)), "Standard: Sichtfeld wie bisher");
        assertTrue(Double.isNaN(GimbalFov.camera(new Fixed(true) {
            @Override
            public double fovDegrees(GimbalContext ignored) {
                return 40.0;
            }
        }, context)), "fovDegrees allein gilt nur für das Bild auf dem Schirm");
        assertTrue(Double.isNaN(GimbalFov.camera(focal(Double.NaN), context)), "NaN");
        assertTrue(Double.isNaN(GimbalFov.camera(focal(Double.POSITIVE_INFINITY), context)), "nicht endlich");
        assertTrue(Double.isNaN(GimbalFov.camera(new Fixed(true) {
            @Override
            public double gimbalFovDegrees(GimbalContext ignored) {
                throw new IllegalStateException("Test");
            }
        }, context)), "Ausnahme im Add-on");
    }

    @Test
    void propsStayUnlessTheGimbalViewHidesThem() {
        GimbalContext context = context(new Quaternionf());
        assertTrue(GimbalProps.shown(new Fixed(true), context), "Standard: Props wie bisher");
        assertFalse(GimbalProps.shown(new Fixed(false), context), "Gimbal-Bild ohne Props");
        assertTrue(GimbalProps.shown(new Fixed(true) {
            @Override
            public boolean showsProps(GimbalContext ignored) {
                throw new IllegalStateException("Test");
            }
        }, context), "Ausnahme im Add-on: Props wie bisher");
    }

    @Test
    void gimbalStabilizedRecordingSkipsTheSoftwareStabilizer() {
        assertEquals(Stabilizer.Mode.OFF, CameraLook.recordingMode(Stabilizer.Mode.HORIZON, true));
        assertEquals(Stabilizer.Mode.OFF, CameraLook.recordingMode(Stabilizer.Mode.SMOOTH, true));
        assertEquals(Stabilizer.Mode.HORIZON, CameraLook.recordingMode(Stabilizer.Mode.HORIZON, false));
        assertEquals(Stabilizer.Mode.OFF, CameraLook.recordingMode(Stabilizer.Mode.OFF, false));
    }

    @Test
    void focalLengthIsClampedAndNanMeansTheBuild() {
        assertEquals(40.0, GimbalFov.clamp(40.0));
        assertEquals(GimbalProvider.MIN_FOV_DEGREES, GimbalFov.clamp(5.0));
        assertEquals(GimbalProvider.MAX_FOV_DEGREES, GimbalFov.clamp(179.0));
        assertTrue(Double.isNaN(GimbalFov.clamp(Double.NaN)));
        assertTrue(Double.isNaN(GimbalFov.clamp(Double.POSITIVE_INFINITY)));
    }

    @Test
    void gimbalHeadElementIsFoundWithItsPivot() {
        JsonObject model = JsonParser.parseString("""
                {"elements": [
                  {"name": "plate", "from": [4, 6, 4], "to": [12, 7, 12]},
                  {"name": "gimbal_head", "from": [6.5, 3.5, 3.25], "to": [9.5, 5.75, 5.75],
                   "rotation": {"angle": 0, "axis": "x", "origin": [8, 4.5, 4.5]}}
                ]}""").getAsJsonObject();
        Optional<GimbalHead.Head> head = GimbalHead.parse(model);
        assertTrue(head.isPresent());
        assertEquals(6.5F / 16.0F, head.get().minX(), 1.0E-6F);
        assertEquals(5.75F / 16.0F, head.get().maxZ(), 1.0E-6F);
        assertEquals(4.5F / 16.0F, head.get().pivotY(), 1.0E-6F);
        assertTrue(head.get().contains(0.5F, 4.6F / 16.0F, 4.5F / 16.0F));
        assertFalse(head.get().contains(0.5F, 6.5F / 16.0F, 0.5F));
        JsonObject centred = JsonParser.parseString("""
                {"elements": [{"name": "gimbal_head", "from": [0, 0, 0], "to": [4, 2, 8]}]}""").getAsJsonObject();
        assertEquals(2.0F / 16.0F, GimbalHead.parse(centred).orElseThrow().pivotX(), 1.0E-6F);
        assertTrue(GimbalHead.parse(JsonParser.parseString("{\"elements\": [{\"name\": \"lens\", \"from\": [0,0,0], \"to\": [1,1,1]}]}")
                .getAsJsonObject()).isEmpty());
        assertTrue(GimbalHead.parse(new JsonObject()).isEmpty());
    }

    private static double degrees(Quaternionfc a, Quaternionfc b) {
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    private static GimbalProvider rotation(boolean supplied, Quaternionfc value) {
        return new Fixed(true) {
            @Override
            public boolean gimbalRotation(GimbalContext context, Quaternionf rotation) {
                rotation.set(value);
                return supplied;
            }
        };
    }

    private static GimbalProvider focal(double degrees) {
        return new Fixed(true) {
            @Override
            public double gimbalFovDegrees(GimbalContext context) {
                return degrees;
            }
        };
    }

    private static GimbalContext context(Quaternionfc frame) {
        return new GimbalContext() {
            @Override
            public Entity drone() {
                return null;
            }

            @Override
            public Quaternionfc frame() {
                return frame;
            }

            @Override
            public float uptiltDegrees() {
                return 20.0F;
            }

            @Override
            public double deltaSeconds() {
                return 0.0;
            }

            @Override
            public boolean spectating() {
                return false;
            }
        };
    }

    private static class Fixed implements GimbalProvider {
        private final boolean props;

        Fixed(boolean props) {
            this.props = props;
        }

        @Override
        public boolean appliesTo(Entity drone) {
            return true;
        }

        @Override
        public void orient(GimbalContext context, Quaternionf rotation) {
        }

        @Override
        public boolean showsProps(GimbalContext context) {
            return this.props;
        }
    }
}
