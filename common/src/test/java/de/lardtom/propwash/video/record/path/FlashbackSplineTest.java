package de.lardtom.propwash.video.record.path;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashbackSplineTest {
    private static PathKeyframe key(int tick, double x, double y, double z, float yaw, float pitch, float roll) {
        return new PathKeyframe(tick, x, y, z, yaw, pitch, roll);
    }

    @Test
    void passesThroughKeyframes() {
        List<PathKeyframe> keys = List.of(key(0, 0, 0, 0, 0, 0, 0), key(5, 3, 1, 2, 30, 10, 5), key(9, 4, 4, 1, 60, -5, 0),
                key(20, 9, 0, 0, 90, 0, -20));
        for (PathKeyframe k : keys) {
            FlashbackSpline.Pose pose = FlashbackSpline.at(keys, k.tick());
            assertEquals(k.x(), pose.x(), 0.0);
            assertEquals(k.yaw(), pose.yaw(), 0.0F);
        }
        FlashbackSpline.Pose after = FlashbackSpline.at(keys, 30.0F);
        assertEquals(9.0, after.x(), 0.0);
        FlashbackSpline.Pose before = FlashbackSpline.at(keys, -3.0F);
        assertEquals(0.0, before.x(), 0.0);
    }

    @Test
    void evenStraightFlightIsLinear() {
        List<PathKeyframe> keys = List.of(key(0, 0, 0, 0, 0, 0, 0), key(10, 0, 0, 5, 10, 0, 0), key(20, 0, 0, 10, 20, 0, 0),
                key(30, 0, 0, 15, 30, 0, 0));
        for (float tick = 10.0F; tick <= 20.0F; tick += 0.25F) {
            FlashbackSpline.Pose pose = FlashbackSpline.at(keys, tick);
            assertEquals(tick * 0.5, pose.z(), 1.0E-6, "Tick " + tick);
            assertEquals(tick, pose.yaw(), 1.0E-4F, "Tick " + tick);
        }
        FlashbackSpline.Pose eased = FlashbackSpline.at(keys, 2.5F);
        assertEquals(true, eased.z() < 1.25, "Anfang der Spur läuft langsam an: " + eased.z());
    }

    @Test
    void anglesTakeTheShortWayAcrossTheSeam() {
        List<PathKeyframe> keys = List.of(key(0, 0, 0, 0, 170, 0, 0), key(10, 0, 0, 1, -170, 0, 0));
        FlashbackSpline.Pose middle = FlashbackSpline.at(keys, 5.0F);
        assertEquals(180.0, CameraAngles.wrap(middle.yaw()) + (CameraAngles.wrap(middle.yaw()) < 0 ? 360.0 : 0.0), 1.0E-3);
    }

    @Test
    void floorIndexFindsSegments() {
        List<PathKeyframe> keys = List.of(key(2, 0, 0, 0, 0, 0, 0), key(5, 0, 0, 0, 0, 0, 0), key(9, 0, 0, 0, 0, 0, 0));
        assertEquals(-1, FlashbackSpline.floorIndex(keys, 1));
        assertEquals(0, FlashbackSpline.floorIndex(keys, 2));
        assertEquals(0, FlashbackSpline.floorIndex(keys, 4));
        assertEquals(1, FlashbackSpline.floorIndex(keys, 5));
        assertEquals(2, FlashbackSpline.floorIndex(keys, 100));
    }

    @Test
    void matchesValuesFromFlashbacksCatmullRom() throws IOException {
        Path file = null;
        for (Path candidate : List.of(Path.of("common/src/test/resources/flashback/catmull_rom_golden.txt"),
                Path.of("src/test/resources/flashback/catmull_rom_golden.txt"))) {
            if (Files.isRegularFile(candidate)) {
                file = candidate;
            }
        }
        assertNotNull(file);
        int cases = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.trim().split(" ");
                assertEquals(24, f.length, line);
                Vector3d[] p = new Vector3d[4];
                for (int k = 0; k < 4; k++) {
                    p[k] = new Vector3d(Double.parseDouble(f[k * 3]), Double.parseDouble(f[k * 3 + 1]), Double.parseDouble(f[k * 3 + 2]));
                }
                float t1 = Float.parseFloat(f[12]);
                float t2 = Float.parseFloat(f[13]);
                float t3 = Float.parseFloat(f[14]);
                float amount = Float.parseFloat(f[15]);
                Vector3d position = FlashbackSpline.position(p[0], p[1], p[2], p[3], t1, t2, t3, amount);
                assertEquals(Double.parseDouble(f[16]), position.x, 1.0E-12, line);
                assertEquals(Double.parseDouble(f[17]), position.y, 1.0E-12, line);
                assertEquals(Double.parseDouble(f[18]), position.z, 1.0E-12, line);
                float angle = FlashbackSpline.degrees(Float.parseFloat(f[19]), Float.parseFloat(f[20]), Float.parseFloat(f[21]), Float.parseFloat(f[22]),
                        t1, t2, t3, amount);
                assertEquals(Float.parseFloat(f[23]), angle, 0.0F, line);
                cases++;
            }
        }
        assertTrue(cases >= 100, "Fälle " + cases);
    }
}
