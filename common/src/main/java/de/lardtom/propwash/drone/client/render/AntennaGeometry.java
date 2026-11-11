package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.VideoAntenna;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntennaGeometry {
    public static final int STALK = FallbackShapes.ANTENNA;
    public static final int SLEEVE = 0xFF202022;
    public static final int MOUNT = 0xFF262628;
    public static final int SMA = 0xFFB08D3C;
    public static final int CAP = FallbackShapes.ANTENNA_CAP;
    public static final int CAP_LIGHT = 0xFFE4E4E2;
    public static final float SINGLE_LEAN_DEG = 0.0F;
    public static final float V_LEAN_DEG = 15.0F;
    public static final float MAX_OFFSET_MM = 0.0F;
    public static final float OFFSET_OF_WHEELBASE = 0.06F;
    public static final float BASE_Y_MM = 0.8F;
    public static final float BASE_Z_MM = 0.5F;
    public static final float MOUNT_REACH_MM = 16.0F;
    public static final float MOUNT_REACH_OF_WHEELBASE = 0.12F;
    public static final float MOUNT_REAR_MM = 3.5F;
    public static final float SMA_MM = 3.0F;
    private static final int MAX_CACHED = 128;
    private static final Map<Key, Shape> SHAPES = new ConcurrentHashMap<>();

    public record Piece(String name, Matrix4f transform, boolean round, int segments, int color) {
        public Vector3f point(float x, float y, float z) {
            return this.transform.transformPosition(new Vector3f(x, y, z));
        }

        public List<Vector3f> samples(int along) {
            List<Vector3f> out = new ArrayList<>();
            for (int i = 0; i <= 2; i++) {
                for (int j = 0; j <= along; j++) {
                    for (int k = 0; k <= 2; k++) {
                        out.add(this.point(-0.5F + i * 0.5F, -0.5F + (float) j / along, -0.5F + k * 0.5F));
                    }
                }
            }
            return out;
        }
    }

    private record Key(VideoAntenna antenna, float tiltDeg, float offsetMm, float reachMm, float pairLeanDeg) {
    }

    private AntennaGeometry() {
    }

    public static float offsetMm(double wheelbaseMm) {
        return Math.min(MAX_OFFSET_MM, (float) (OFFSET_OF_WHEELBASE * wheelbaseMm));
    }

    public static float reachMm(double wheelbaseMm) {
        return Math.min(MOUNT_REACH_MM, (float) (MOUNT_REACH_OF_WHEELBASE * wheelbaseMm));
    }

    public static Shape shape(VideoAntenna antenna, double tiltDeg, double wheelbaseMm) {
        return shape(antenna, tiltDeg, wheelbaseMm, 0.0, V_LEAN_DEG);
    }

    public static Shape shape(VideoAntenna antenna, double tiltDeg, double wheelbaseMm, double extraReachMm, double pairLeanDeg) {
        if (antenna.isNone()) {
            return Shape.EMPTY;
        }
        Key key = new Key(antenna, (float) tiltDeg, offsetMm(wheelbaseMm), reachMm(wheelbaseMm) + (float) Math.max(0.0, extraReachMm),
                (float) pairLeanDeg);
        Shape cached = SHAPES.get(key);
        if (cached != null) {
            return cached;
        }
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        for (Piece piece : pieces(antenna, key.tiltDeg(), key.offsetMm(), key.reachMm(), key.pairLeanDeg())) {
            if (piece.round()) {
                b.cylinder(piece.transform(), piece.segments(), piece.color());
            } else {
                b.cube(piece.transform(), piece.color());
            }
        }
        Shape shape = b.build();
        if (SHAPES.size() >= MAX_CACHED) {
            SHAPES.clear();
        }
        SHAPES.put(key, shape);
        return shape;
    }

    public static List<Piece> pieces(VideoAntenna antenna, double tiltDeg, double wheelbaseMm) {
        return pieces(antenna, tiltDeg, wheelbaseMm, V_LEAN_DEG);
    }

    public static List<Piece> pieces(VideoAntenna antenna, double tiltDeg, double wheelbaseMm, double pairLeanDeg) {
        return pieces(antenna, (float) tiltDeg, offsetMm(wheelbaseMm), reachMm(wheelbaseMm), (float) pairLeanDeg);
    }

    static List<Piece> pieces(VideoAntenna antenna, float tiltDeg, float offset, float reach, float pairLean) {
        if (antenna.isNone()) {
            return List.of();
        }
        List<Piece> out = new ArrayList<>();
        int count = antenna.count();
        float half = (count == 1 ? 0.0F : offset) + 3.5F;
        out.add(new Piece("mount", box(-half, -6.0F, -Math.max(3.0F, reach), half, -2.6F, MOUNT_REAR_MM), false, 0, MOUNT));
        out.add(new Piece("mount", box(-half, -2.6F, -3.0F, half, -0.4F, MOUNT_REAR_MM), false, 0, MOUNT));
        for (int i = 0; i < count; i++) {
            float side = side(count, i);
            Matrix4f frame = new Matrix4f().translation(side * offset, BASE_Y_MM, BASE_Z_MM)
                    .rotateX((float) Math.toRadians(tiltDeg)).rotateZ((float) Math.toRadians(-side * (count == 1 ? SINGLE_LEAN_DEG : pairLean)));
            whip(out, frame, antenna);
        }
        return out;
    }

    static float side(int count, int index) {
        return count == 1 ? 0.0F : (index == 0 ? -1.0F : 1.0F);
    }

    private static void whip(List<Piece> out, Matrix4fc frame, VideoAntenna antenna) {
        float length = (float) antenna.lengthMm();
        rod(out, "sma", frame, -1.2F, SMA_MM, 3.6F, 8, SMA);
        switch (antenna.cap()) {
            case WHIP -> {
                float sleeve = SMA_MM + 0.3F * length;
                float tip = 0.65F * length;
                rod(out, "sleeve", frame, SMA_MM, sleeve, 3.0F, 8, SLEEVE);
                rod(out, "stalk", frame, sleeve, tip, 2.0F, 6, STALK);
                rod(out, "tip", frame, tip, length, 1.6F, 6, CAP_LIGHT);
            }
            case STUBBY -> {
                float top = length - 6.0F;
                rod(out, "stalk", frame, SMA_MM, top, 6.0F, 10, STALK);
                rod(out, "cap", frame, top, length, 5.2F, 10, CAP);
            }
            case LOLLIPOP -> {
                float cap = Math.max(10.0F, Math.min(15.0F, 0.1F * length + 5.5F));
                float sleeve = SMA_MM + 0.15F * length;
                float base = length - cap;
                rod(out, "sleeve", frame, SMA_MM, sleeve, 4.4F, 8, SLEEVE);
                rod(out, "stalk", frame, sleeve, base, 2.8F, 8, STALK);
                rod(out, "cap", frame, base, base + 0.25F * cap, 0.7F * cap, 12, CAP);
                rod(out, "cap", frame, base + 0.25F * cap, base + 0.75F * cap, cap, 12, CAP);
                rod(out, "cap", frame, base + 0.75F * cap, length, 0.7F * cap, 12, CAP);
            }
            case PAGODA -> {
                float core = length - 14.0F;
                float sleeve = SMA_MM + 0.12F * length;
                rod(out, "sleeve", frame, SMA_MM, sleeve, 4.6F, 8, SLEEVE);
                rod(out, "stalk", frame, sleeve, core, 3.2F, 8, STALK);
                rod(out, "core", frame, core, length, 6.0F, 10, STALK);
                float[] widths = {19.0F, 16.5F, 14.0F};
                for (int d = 0; d < widths.length; d++) {
                    float at = core + 2.0F + d * 4.5F;
                    rod(out, "cap", frame, at, at + 2.6F, widths[d], 14, CAP_LIGHT);
                }
            }
            case PATCH -> {
                float plate = length - 20.0F;
                rod(out, "stalk", frame, SMA_MM, plate, 3.0F, 8, STALK);
                out.add(new Piece("cap", new Matrix4f(frame).translate(0.0F, plate + 10.0F, 1.5F).scale(20.0F, 20.0F, 3.5F), false, 0, CAP));
            }
        }
    }

    private static Matrix4f box(float x0, float y0, float z0, float x1, float y1, float z1) {
        return new Matrix4f().translation((x0 + x1) * 0.5F, (y0 + y1) * 0.5F, (z0 + z1) * 0.5F).scale(x1 - x0, y1 - y0, z1 - z0);
    }

    private static void rod(List<Piece> out, String name, Matrix4fc frame, float from, float to, float width, int segments, int color) {
        if (!(to > from)) {
            return;
        }
        Matrix4f transform = new Matrix4f(frame).translate(0.0F, (from + to) * 0.5F, 0.0F).scale(width, to - from, width);
        out.add(new Piece(name, transform, true, segments, color));
    }

    public static int capColor(VideoAntenna antenna) {
        return switch (antenna.cap()) {
            case WHIP, PAGODA -> CAP_LIGHT;
            case STUBBY, LOLLIPOP, PATCH -> CAP;
        };
    }

    public static Vector3f axis(VideoAntenna antenna, double tiltDeg, int index) {
        return axis(antenna, tiltDeg, index, V_LEAN_DEG);
    }

    public static Vector3f axis(VideoAntenna antenna, double tiltDeg, int index, double pairLeanDeg) {
        int count = antenna.count();
        double lean = count == 1 ? SINGLE_LEAN_DEG : pairLeanDeg;
        return new Matrix4f().rotateX((float) Math.toRadians(tiltDeg)).rotateZ((float) Math.toRadians(-side(count, index) * lean))
                .transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
    }
}
