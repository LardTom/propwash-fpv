package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.VideoAntenna;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class AntennaVisibility {
    public static final float QUARTER_AZIMUTH_DEG = 35.0F;
    public static final float PREVIEW_ELEVATION_DEG = 25.0F;
    public static final float SIDE_FREE = 0.8F;
    public static final float QUARTER_FREE_SINGLE = 0.75F;
    public static final float QUARTER_FREE_PAIR = 0.35F;
    public static final float MARGIN_MM = 0.5F;
    public static final List<View> QUARTER_VIEWS = List.of(View.of("3/4 hinten links", -QUARTER_AZIMUTH_DEG, PREVIEW_ELEVATION_DEG),
            View.of("3/4 hinten rechts", QUARTER_AZIMUTH_DEG, PREVIEW_ELEVATION_DEG));
    public static final List<View> SIDE_VIEWS = List.of(View.of("links", -90.0F, 0.0F), View.of("rechts", 90.0F, 0.0F));

    public record View(String name, Vector3f right, Vector3f up) {
        public static View of(String name, float azimuthDeg, float elevationDeg) {
            double a = Math.toRadians(azimuthDeg);
            double e = Math.toRadians(elevationDeg);
            Vector3f toCamera = new Vector3f((float) (Math.sin(a) * Math.cos(e)), (float) Math.sin(e), (float) (Math.cos(a) * Math.cos(e)));
            Vector3f right = new Vector3f(0.0F, 1.0F, 0.0F).cross(toCamera).normalize();
            return new View(name, right, new Vector3f(toCamera).cross(right).normalize());
        }

        public float[] project(Vector3fc p) {
            return new float[]{p.dot(this.right), p.dot(this.up)};
        }
    }

    private AntennaVisibility() {
    }

    public static List<Vector3f> corners(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        List<Vector3f> out = new ArrayList<>(8);
        for (int c = 0; c < 8; c++) {
            out.add(new Vector3f((c & 1) == 0 ? minX : maxX, (c & 2) == 0 ? minY : maxY, (c & 4) == 0 ? minZ : maxZ));
        }
        return out;
    }

    public static List<List<float[]>> silhouettes(List<List<Vector3f>> boxes, View view) {
        List<List<float[]>> out = new ArrayList<>(boxes.size());
        for (List<Vector3f> box : boxes) {
            List<float[]> projected = new ArrayList<>(box.size());
            for (Vector3f corner : box) {
                projected.add(view.project(corner));
            }
            out.add(hull(projected));
        }
        return out;
    }

    public static List<float[]> hull(List<float[]> points) {
        List<float[]> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        List<float[]> out = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = out.size();
            for (float[] p : sorted) {
                while (out.size() >= start + 2 && cross(out.get(out.size() - 2), out.get(out.size() - 1), p) <= 0.0F) {
                    out.removeLast();
                }
                out.add(p);
            }
            out.removeLast();
            sorted = sorted.reversed();
        }
        return out;
    }

    private static float cross(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    public static boolean covers(List<float[]> polygon, float[] p, float margin) {
        if (polygon.size() < 3) {
            return false;
        }
        for (int i = 0; i < polygon.size(); i++) {
            float[] a = polygon.get(i);
            float[] b = polygon.get((i + 1) % polygon.size());
            float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (length > 0.0F && cross(a, b, p) / length < -margin) {
                return false;
            }
        }
        return true;
    }

    public static float[][] freeAndTotal(VideoAntenna antenna, double tiltDeg, double wheelbaseMm, double pairLeanDeg, Vector3fc at,
                                         List<List<float[]>> silhouettes, View view) {
        float[][] out = new float[antenna.count()][2];
        for (AntennaGeometry.Piece piece : AntennaGeometry.pieces(antenna, tiltDeg, wheelbaseMm, pairLeanDeg)) {
            if (piece.name().equals("mount") || piece.name().equals("sma") || !piece.round()) {
                continue;
            }
            Vector3f from = piece.point(0.0F, -0.5F, 0.0F).add(at);
            Vector3f to = piece.point(0.0F, 0.5F, 0.0F).add(at);
            int index = antenna.count() == 1 || from.x < at.x() ? 0 : 1;
            float length = from.distance(to);
            int steps = Math.max(1, Math.round(length * 2.0F));
            for (int s = 0; s < steps; s++) {
                float[] q = view.project(new Vector3f(from).lerp(to, (s + 0.5F) / steps));
                boolean hidden = false;
                for (List<float[]> polygon : silhouettes) {
                    if (covers(polygon, q, MARGIN_MM)) {
                        hidden = true;
                        break;
                    }
                }
                out[index][1] += length / steps;
                if (!hidden) {
                    out[index][0] += length / steps;
                }
            }
        }
        return out;
    }

    public static float worstShare(VideoAntenna antenna, double tiltDeg, double wheelbaseMm, double pairLeanDeg, Vector3fc at,
                                   List<List<Vector3f>> boxes, List<View> views) {
        float worst = Float.POSITIVE_INFINITY;
        for (View view : views) {
            for (float[] one : freeAndTotal(antenna, tiltDeg, wheelbaseMm, pairLeanDeg, at, silhouettes(boxes, view), view)) {
                worst = Math.min(worst, one[1] > 0.0F ? one[0] / one[1] : 1.0F);
            }
        }
        return worst;
    }
}
