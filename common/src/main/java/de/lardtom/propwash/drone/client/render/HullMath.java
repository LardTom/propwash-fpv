package de.lardtom.propwash.drone.client.render;

import java.util.Arrays;

public final class HullMath {
    private HullMath() {
    }

    public static float[] reduce(float[] points, int maxPoints, int directions) {
        float[] unique = unique(points);
        if (unique.length / 3 <= maxPoints) {
            return unique;
        }
        return support(unique, directions);
    }

    public static float[] unique(float[] points) {
        int count = points.length / 3;
        long[] keys = new long[count];
        for (int i = 0; i < count; i++) {
            keys[i] = (key(points[3 * i]) << 42) | (key(points[3 * i + 1]) << 21) | key(points[3 * i + 2]);
        }
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Long.compare(keys[a], keys[b]));
        float[] out = new float[points.length];
        int size = 0;
        long last = 0L;
        for (int n = 0; n < count; n++) {
            int i = order[n];
            if (n > 0 && keys[i] == last) {
                continue;
            }
            last = keys[i];
            out[size++] = points[3 * i];
            out[size++] = points[3 * i + 1];
            out[size++] = points[3 * i + 2];
        }
        return Arrays.copyOf(out, size);
    }

    private static long key(float value) {
        long q = Math.round((double) value * 50.0) + (1L << 20);
        return Math.max(0L, Math.min((1L << 21) - 1L, q));
    }

    public static float[] support(float[] points, int directions) {
        int count = points.length / 3;
        if (count == 0) {
            return points;
        }
        boolean[] keep = new boolean[count];
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        int total = directions + 6;
        for (int d = 0; d < total; d++) {
            float dx;
            float dy;
            float dz;
            if (d < directions) {
                double y = 1.0 - 2.0 * (d + 0.5) / directions;
                double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double a = golden * d;
                dx = (float) (Math.cos(a) * r);
                dy = (float) y;
                dz = (float) (Math.sin(a) * r);
            } else {
                int axis = (d - directions) / 2;
                float sign = (d - directions) % 2 == 0 ? 1.0F : -1.0F;
                dx = axis == 0 ? sign : 0.0F;
                dy = axis == 1 ? sign : 0.0F;
                dz = axis == 2 ? sign : 0.0F;
            }
            int best = 0;
            float bestDot = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                float dot = dx * points[3 * i] + dy * points[3 * i + 1] + dz * points[3 * i + 2];
                if (dot > bestDot) {
                    bestDot = dot;
                    best = i;
                }
            }
            keep[best] = true;
        }
        float[] out = new float[points.length];
        int size = 0;
        for (int i = 0; i < count; i++) {
            if (keep[i]) {
                out[size++] = points[3 * i];
                out[size++] = points[3 * i + 1];
                out[size++] = points[3 * i + 2];
            }
        }
        return Arrays.copyOf(out, size);
    }

    public static float[] outline(float[] profile, int directions) {
        int count = profile.length / 3;
        if (count == 0) {
            return new float[0];
        }
        boolean[] keep = new boolean[count];
        for (int d = 0; d < directions; d++) {
            double angle = directions == 1 ? 0.0 : -Math.PI / 2.0 + Math.PI * d / (directions - 1);
            float dr = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            int best = 0;
            float bestDot = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                float dot = dr * profile[3 * i] + dy * profile[3 * i + 1];
                if (dot > bestDot) {
                    bestDot = dot;
                    best = i;
                }
            }
            keep[best] = true;
        }
        float[] out = new float[2 * count];
        int size = 0;
        for (int i = 0; i < count; i++) {
            if (keep[i]) {
                out[size++] = profile[3 * i];
                out[size++] = profile[3 * i + 1];
            }
        }
        return Arrays.copyOf(out, size);
    }

    public static float[] rings(float[] outline, int ringPoints) {
        int count = outline.length / 2;
        float[] out = new float[count * ringPoints * 3];
        int size = 0;
        double stretch = 1.0 / Math.cos(Math.PI / ringPoints);
        for (int i = 0; i < count; i++) {
            float radius = outline[2 * i];
            float y = outline[2 * i + 1];
            if (radius < 1.0E-3F) {
                out[size++] = 0.0F;
                out[size++] = y;
                out[size++] = 0.0F;
                continue;
            }
            for (int k = 0; k < ringPoints; k++) {
                double a = Math.PI * 2.0 * k / ringPoints;
                out[size++] = (float) (Math.cos(a) * radius * stretch);
                out[size++] = y;
                out[size++] = (float) (Math.sin(a) * radius * stretch);
            }
        }
        return Arrays.copyOf(out, size);
    }
}
