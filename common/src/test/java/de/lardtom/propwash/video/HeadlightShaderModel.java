package de.lardtom.propwash.video;

final class HeadlightShaderModel {
    static final float HUE_TRUST_FROM = 0.03F;
    static final float HUE_TRUST_TO = 0.14F;
    static final float HOTSPOT_COS = 0.992546F;
    static final float HOTSPOT_BOOST = 1.1F;
    static final float[] WARM = {1.0F, 0.9F, 0.74F};
    static final float WARM_EDGE = 0.0F;
    static final float WARM_CORE = 0.5F;
    static final float MIN_DEPTH = 1.0e-6F;
    static final float MIN_W = 1.0e-9F;
    static final float MAX_COORD = 1.0e7F;
    static final float MAX_GAIN = 1000.0F;

    private HeadlightShaderModel() {
    }

    record Uniforms(float[] invProj, float[] light, float[] cone, float[] range) {
        static Uniforms of(float[] invProj, float red, float green, float blue, float gain, boolean zeroToOne) {
            float cosInner = (float) Math.cos(Math.toRadians(HeadlightMath.CONE_INNER_DEG));
            float cosOuter = (float) Math.cos(Math.toRadians(HeadlightMath.CONE_OUTER_DEG));
            return new Uniforms(invProj, new float[]{red, green, blue, gain},
                    new float[]{cosInner, cosOuter, (float) HeadlightMath.SPILL, zeroToOne ? 1.0F : 0.0F},
                    new float[]{(float) HeadlightMath.RANGE_BLOCKS, (float) HeadlightMath.FALLOFF_BLOCKS, (float) HeadlightMath.LAMBERT_WEIGHT,
                            (float) HeadlightMath.LIFT_PER_GAIN});
        }
    }

    static float[] render(Uniforms u, float[] colors, float[] depth, int width, int height, boolean guarded) {
        float[][] positions = new float[width * height][];
        boolean[] solid = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float u0 = (x + 0.5F) / width;
                float v0 = (y + 0.5F) / height;
                float d = depth[i];
                float[] p = viewPoint(u, u0, v0, guarded ? clamp(d, MIN_DEPTH, 1.0F) : Math.max(d, MIN_DEPTH));
                if (guarded) {
                    boolean ok = Math.abs(p[3]) > MIN_W;
                    float[] pos = ok ? divide(p) : new float[3];
                    ok = ok && finite3(pos);
                    positions[i] = ok ? pos : new float[3];
                    solid[i] = ok;
                } else {
                    positions[i] = divide(p);
                    solid[i] = true;
                }
            }
        }
        float[] out = new float[colors.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int qx = x & ~1;
                int qy = y & ~1;
                float[] ddx = sub(positions[y * width + Math.min(qx + 1, width - 1)], positions[y * width + qx]);
                float[] ddy = sub(positions[Math.min(qy + 1, height - 1) * width + x], positions[qy * width + x]);
                float[] normal = cross(ddx, ddy);
                float[] col = {colors[i * 3], colors[i * 3 + 1], colors[i * 3 + 2]};
                float[] shaded = guarded ? shadeGuarded(u, col, depth[i], positions[i], solid[i], normal) : shadeLegacy(u, col, depth[i], positions[i], normal);
                System.arraycopy(shaded, 0, out, i * 3, 3);
            }
        }
        return out;
    }

    static float[] viewPoint(Uniforms u, float uvx, float uvy, float depth) {
        float z = u.cone()[3] > 0.5F ? depth : depth * 2.0F - 1.0F;
        float[] v = {uvx * 2.0F - 1.0F, uvy * 2.0F - 1.0F, z, 1.0F};
        float[] m = u.invProj();
        float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3];
        }
        return r;
    }

    static float[] shadeGuarded(Uniforms u, float[] col, float depth, float[] pos, boolean solid, float[] normal) {
        float[] light = u.light();
        boolean valid = solid && depth > 0.0F && depth < 1.0F && light[3] > 0.0F && light[3] < MAX_GAIN;
        if (!valid) {
            return col.clone();
        }
        float[] boosted = lightUp(u, col, pos, normal, true);
        boolean sane = finite3(boosted) && boosted[0] >= col[0] && boosted[1] >= col[1] && boosted[2] >= col[2];
        return sane ? new float[]{clamp(boosted[0], 0.0F, 1.0F), clamp(boosted[1], 0.0F, 1.0F), clamp(boosted[2], 0.0F, 1.0F)} : col.clone();
    }

    static float[] shadeLegacy(Uniforms u, float[] col, float depth, float[] pos, float[] normal) {
        if (depth <= 0.0F || u.light()[3] <= 0.0F) {
            return col.clone();
        }
        float[] boosted = lightUp(u, col, pos, normal, false);
        return new float[]{clampLegacy(boosted[0]), clampLegacy(boosted[1]), clampLegacy(boosted[2])};
    }

    private static float[] lightUp(Uniforms u, float[] col, float[] pos, float[] normal, boolean guarded) {
        float[] light = u.light();
        float[] cone = u.cone();
        float[] range = u.range();
        float dist = length(pos);
        float inv = Math.max(dist, 1.0e-4F);
        float[] dir = {pos[0] / inv, pos[1] / inv, pos[2] / inv};
        float edge = smoothstep(cone[1], cone[0], -dir[2]);
        float coneValue = cone[2] + (1.0F - cone[2]) * edge * edge;
        float core = smoothstep(cone[0], HOTSPOT_COS, -dir[2]);
        coneValue *= 1.0F + HOTSPOT_BOOST * core;
        float warm = mix(WARM_EDGE, WARM_CORE, core);
        float[] tint = {mix(1.0F, WARM[0], warm), mix(1.0F, WARM[1], warm), mix(1.0F, WARM[2], warm)};
        float ratio = dist / Math.max(range[1], 1.0e-3F);
        float falloff = 1.0F / (1.0F + ratio * ratio);
        float cut = 1.0F - smoothstep(range[0] * 0.7F, range[0], dist);
        float normalLength = length(normal);
        float[] unit = {normal[0] / normalLength, normal[1] / normalLength, normal[2] / normalLength};
        float facing;
        if (guarded) {
            boolean oriented = normalLength > 1.0e-6F && !Float.isInfinite(normalLength) && !Float.isNaN(normalLength);
            facing = oriented ? clamp(Math.abs(dot(unit, dir)), 0.0F, 1.0F) : 1.0F;
        } else {
            facing = normalLength > 1.0e-6F ? Math.abs(dot(unit, dir)) : 1.0F;
        }
        float lambert = mix(1.0F, facing, range[2]);
        float y = col[0] * 0.299F + col[1] * 0.587F + col[2] * 0.114F;
        float headroom = 1.0F - smoothstep(0.05F, 0.85F, y);
        float reach = light[3] * coneValue * falloff * cut * lambert;
        float lit = reach * headroom;
        float trust = smoothstep(HUE_TRUST_FROM, HUE_TRUST_TO, y);
        float[] boosted = new float[3];
        for (int c = 0; c < 3; c++) {
            float base = mix(y, col[c], trust);
            boosted[c] = col[c] + (base * lit + range[3] * reach) * light[c] * tint[c];
        }
        return boosted;
    }

    static boolean finite3(float[] v) {
        for (float f : v) {
            if (!(Math.abs(f) < MAX_COORD) || Float.isNaN(f) || Float.isInfinite(f)) {
                return false;
            }
        }
        return true;
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    static float clamp(float x, float lo, float hi) {
        return Math.min(Math.max(x, lo), hi);
    }

    private static float clampLegacy(float x) {
        return Float.isNaN(x) ? Float.NaN : clamp(x, 0.0F, 1.0F);
    }

    private static float mix(float a, float b, float t) {
        return a * (1.0F - t) + b * t;
    }

    private static float[] divide(float[] p) {
        return new float[]{p[0] / p[3], p[1] / p[3], p[2] / p[3]};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(dot(v, v));
    }
}
