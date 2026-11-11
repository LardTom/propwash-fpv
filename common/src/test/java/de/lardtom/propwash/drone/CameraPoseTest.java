package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraPoseTest {
    private static final Vec3 COM = new Vec3(10.0, 70.0, -4.0);

    @Test
    void levelDroneLooksForwardAndUpByTheUptilt() {
        DroneBuild build = DroneBuild.FREESTYLE;
        Map<String, Float> tune = Map.of(TuneMapping.CAMERA_UPTILT, 30.0F);
        CameraPose pose = CameraPoses.of(build, tune, COM, new Quaternionf()).orElseThrow();
        double uptilt = CameraMount.uptilt(build, tune);
        assertEquals(30.0, uptilt, 1.0E-6);
        assertEquals(Math.sin(Math.toRadians(30.0)), pose.look().y, 1.0E-5);
        assertEquals(-Math.cos(Math.toRadians(30.0)), pose.look().z, 1.0E-5);
        assertEquals(0.0, pose.look().x, 1.0E-6);
        assertEquals(1.0, pose.look().length(), 1.0E-6);
        assertEquals(0.0, pose.look().dot(pose.up()), 1.0E-5);
        assertEquals(Math.cos(Math.toRadians(30.0)), pose.up().y, 1.0E-5);
    }

    @Test
    void eyeSitsAtTheLensOfTheBuild() {
        DroneBuild build = DroneBuild.FREESTYLE;
        AirframeSpec spec = AirframeSpec.of(build);
        CameraPose pose = CameraPoses.of(build, Map.of(), COM, new Quaternionf()).orElseThrow();
        assertEquals(COM.x + spec.lensRight, pose.eye().x, 1.0E-5);
        assertEquals(COM.y + spec.lensUp, pose.eye().y, 1.0E-5);
        assertEquals(COM.z - spec.lensForward, pose.eye().z, 1.0E-5);
        assertTrue(spec.lensForward > 0.0, "Linse vor dem Schwerpunkt");
        Quaternionf east = new Quaternionf().rotateY((float) Math.toRadians(-90.0));
        CameraPose turned = CameraPoses.of(build, Map.of(), COM, east).orElseThrow();
        assertEquals(COM.x + spec.lensForward, turned.eye().x, 1.0E-5);
        assertTrue(turned.look().x > 0.5, "nach Osten gedreht schaut die Kamera nach Osten: " + turned.look());
    }

    @Test
    void attitudeTurnsTheView() {
        DroneBuild build = DroneBuild.RACER;
        Map<String, Float> tune = Map.of(TuneMapping.CAMERA_UPTILT, 40.0F);
        double tilt = CameraMount.uptilt(build, tune);
        Quaternionf noseDown = new Quaternionf().rotateX((float) Math.toRadians(-tilt));
        CameraPose pose = CameraPoses.of(build, tune, COM, noseDown).orElseThrow();
        assertEquals(0.0, pose.look().y, 1.0E-5);
        assertEquals(-1.0, pose.look().z, 1.0E-5);
        Quaternionf rolled = new Quaternionf().rotateZ((float) Math.toRadians(-90.0));
        CameraPose side = CameraPoses.of(build, tune, COM, rolled).orElseThrow();
        assertEquals(0.0, side.up().y, 1.0E-5);
        assertEquals(Math.cos(Math.toRadians(tilt)), side.up().x, 1.0E-5);
        assertEquals(0.0, side.look().y, 1.0E-5);
    }

    @Test
    void fieldOfViewComesFromTheCameraView() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            CameraPose pose = CameraPoses.of(build, Map.of(), COM, new Quaternionf()).orElseThrow();
            double expected = BuildAccessImpl.cameraView(build).verticalFovDeg();
            assertEquals(expected, pose.verticalFovDegrees(), 1.0E-6, build.frame().key());
            assertTrue(pose.verticalFovDegrees() > 20.0 && pose.verticalFovDegrees() < 170.0);
        }
        assertEquals(90.0, CameraPoses.verticalFovDeg(90.0, 1.0), 1.0E-9);
        assertTrue(CameraPoses.verticalFovDeg(120.0, 16.0 / 9.0) < 120.0);
    }

    @Test
    void emptyWithoutCamera() {
        VideoType missing = (VideoType) PartIndex.missing(PartKind.VIDEO, Identifier.fromNamespaceAndPath("gone", "camera"));
        assertTrue(missing.missing());
        DroneBuild build = DroneBuild.FREESTYLE;
        DroneBuild blind = new DroneBuild(build.frame(), build.stack(), build.motor(), build.prop(), missing, build.battery());
        assertEquals(Optional.empty(), CameraPoses.of(blind, Map.of(), COM, new Quaternionf()));
        assertEquals(Optional.empty(), CameraPoses.of(build, Map.of(), new Vec3(Double.NaN, 0.0, 0.0), new Quaternionf()));
    }
}
