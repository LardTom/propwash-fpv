package de.lardtom.propwash.osd.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class OsdPreviewScene {
    private static final int SKY_TOP = 0xFF3B6FBF;
    private static final int SKY_HORIZON = 0xFFBFD6EE;
    private static final int FAR_HILLS = 0xFF86A5BA;
    private static final int MID_HILLS = 0xFF6F9A5A;
    private static final int MID_HILLS_SHADE = 0xFF628C4F;
    private static final int GROUND_FAR = 0xFF6A9848;
    private static final int GROUND_NEAR = 0xFF365F24;
    private static final int GROUND_BAND = 0x12FFFFFF;
    private static final int CLOUD = 0xB8F4F8FC;
    private static final int CLOUD_SHADE = 0x90C9D6E4;
    private static final int LEAVES = 0xFF2E5B25;
    private static final int LEAVES_LIGHT = 0xFF3E7432;
    private static final int LEAVES_DARK = 0xFF21431A;
    private static final int TRUNK = 0xFF5B3E24;
    private static final int GATE_POST = 0xFF22262C;
    private static final int GATE_EDGE = 0xFF363B43;
    private static final int GATE_GREEN = 0xFF52E07F;
    private static final int GATE_WHITE = 0xFFE4E8EC;
    private static final int GATE_CAP = 0xFFE2A928;
    private static final int SHADOW = 0x38000000;

    private static final float[][] CLOUDS = {{0.14F, 0.30F, 1.0F}, {0.47F, 0.36F, 0.7F}, {0.74F, 0.27F, 1.2F}, {0.93F, 0.40F, 0.6F}};
    private static final float[][] TREES = {{0.04F, 0.0F, 1.0F}, {0.12F, 0.01F, 1.35F}, {0.21F, 0.0F, 0.85F}, {0.64F, 0.0F, 0.9F},
            {0.72F, 0.012F, 1.25F}, {0.83F, 0.0F, 1.0F}, {0.95F, 0.02F, 1.5F}};

    private OsdPreviewScene() {
    }

    static void draw(GuiGraphicsExtractor g, int x0, int y0, int w, int h) {
        int x1 = x0 + w;
        int y1 = y0 + h;
        int horizon = y0 + h * 11 / 20;
        int unit = Math.max(1, Math.round(h / 90.0F));
        g.fillGradient(x0, y0, x1, horizon, SKY_TOP, SKY_HORIZON);
        for (float[] cloud : CLOUDS) {
            cloud(g, x0 + Math.round(w * cloud[0]), y0 + Math.round(h * cloud[1]), Math.max(1, Math.round(unit * 2 * cloud[2])), x0, x1);
        }
        hills(g, x0, x1, horizon, w, h, unit);
        ground(g, x0, x1, horizon, y1);
        for (float[] tree : TREES) {
            tree(g, x0 + Math.round(w * tree[0]), horizon + Math.round(h * tree[1]), Math.max(1, Math.round(unit * tree[2])), x0, x1);
        }
        int far = Math.max(8, Math.round(w * 0.07F));
        gate(g, x0 + Math.round(w * 0.42F), horizon + Math.round(h * 0.05F), far, far * 3 / 4, GATE_WHITE, unit);
        int near = Math.max(16, Math.round(w * 0.3F));
        gate(g, x0 + w / 2 - Math.round(w * 0.02F), horizon + Math.round(h * 0.2F), near, near * 3 / 4, GATE_GREEN, unit * 2);
    }

    private static float wave(float t, float frequency, float phase) {
        return (float) Math.sin((t * frequency + phase) * Math.PI * 2.0);
    }

    private static void hills(GuiGraphicsExtractor g, int x0, int x1, int horizon, int w, int h, int unit) {
        for (int x = x0; x < x1; x += unit) {
            float t = (x - x0) / (float) Math.max(1, w);
            int far = Math.round(h * (0.06F + 0.025F * wave(t, 1.3F, 0.1F) + 0.015F * wave(t, 3.7F, 0.6F)));
            g.fill(x, horizon - far, Math.min(x1, x + unit), horizon, FAR_HILLS);
        }
        for (int x = x0; x < x1; x += unit) {
            float t = (x - x0) / (float) Math.max(1, w);
            int mid = Math.round(h * (0.025F + 0.02F * wave(t, 2.1F, 0.35F) + 0.01F * wave(t, 5.3F, 0.2F)));
            mid = Math.max(unit, mid);
            g.fill(x, horizon - mid, Math.min(x1, x + unit), horizon, MID_HILLS);
            g.fill(x, horizon - mid, Math.min(x1, x + unit), horizon - mid + unit, MID_HILLS_SHADE);
        }
    }

    private static void ground(GuiGraphicsExtractor g, int x0, int x1, int horizon, int y1) {
        g.fillGradient(x0, horizon, x1, y1, GROUND_FAR, GROUND_NEAR);
        float band = 1.5F;
        float y = horizon + 1.0F;
        boolean light = true;
        while (y < y1) {
            int top = Math.round(y);
            int bottom = Math.min(y1, Math.round(y + band));
            if (light && bottom > top) {
                g.fill(x0, top, x1, bottom, GROUND_BAND);
            }
            y += band;
            band *= 1.45F;
            light = !light;
        }
    }

    private static void cloud(GuiGraphicsExtractor g, int cx, int cy, int u, int clipX0, int clipX1) {
        int[][] rows = {{-3, 3, -1}, {-7, 7, 0}, {-9, 10, 1}};
        for (int[] row : rows) {
            int left = Math.max(clipX0, cx + row[0] * u);
            int right = Math.min(clipX1, cx + row[1] * u);
            if (right > left) {
                g.fill(left, cy + row[2] * u, right, cy + (row[2] + 1) * u, CLOUD);
            }
        }
        int left = Math.max(clipX0, cx - 8 * u);
        int right = Math.min(clipX1, cx + 9 * u);
        if (right > left) {
            g.fill(left, cy + 2 * u, right, cy + 3 * u, CLOUD_SHADE);
        }
    }

    private static void tree(GuiGraphicsExtractor g, int cx, int base, int u, int clipX0, int clipX1) {
        int[][] canopy = {{-2, 2, -9, LEAVES_LIGHT}, {-4, 4, -8, LEAVES_LIGHT}, {-5, 5, -7, LEAVES}, {-5, 5, -5, LEAVES}, {-4, 4, -3, LEAVES_DARK}};
        int trunkLeft = Math.max(clipX0, cx - u);
        int trunkRight = Math.min(clipX1, cx + u);
        if (trunkRight > trunkLeft) {
            g.fill(trunkLeft, base - 3 * u, trunkRight, base, TRUNK);
        }
        for (int i = 0; i < canopy.length; i++) {
            int[] row = canopy[i];
            int nextTop = i + 1 < canopy.length ? canopy[i + 1][2] : -2;
            int left = Math.max(clipX0, cx + row[0] * u);
            int right = Math.min(clipX1, cx + row[1] * u);
            if (right > left) {
                g.fill(left, base + row[2] * u, right, base + nextTop * u, row[3]);
            }
        }
    }

    private static void gate(GuiGraphicsExtractor g, int cx, int base, int width, int height, int light, int post) {
        int left = cx - width / 2;
        int right = left + width;
        int top = base - height;
        g.fill(left + post, base, right + post * 2, base + Math.max(1, post / 2 + 1), SHADOW);
        g.fill(left, top, left + post, base, GATE_POST);
        g.fill(right - post, top, right, base, GATE_POST);
        g.fill(left, top, right, top + post, GATE_POST);
        g.fill(left, base - post, right, base, GATE_POST);
        int stripe = Math.max(1, post / 2);
        g.fill(left + post, top + post, left + post + stripe, base - post, light);
        g.fill(right - post - stripe, top + post, right - post, base - post, light);
        g.fill(left + post, top + post, right - post, top + post + stripe, light);
        g.fill(left + post, base - post - stripe, right - post, base - post, light);
        g.fill(left, top, right, top + Math.max(1, stripe / 2), GATE_EDGE);
        int cap = post + Math.max(1, post / 2);
        g.fill(left - (cap - post) / 2, top - (cap - post) / 2, left - (cap - post) / 2 + cap, top - (cap - post) / 2 + cap, GATE_CAP);
        g.fill(right - post - (cap - post) / 2, top - (cap - post) / 2, right - post - (cap - post) / 2 + cap, top - (cap - post) / 2 + cap, GATE_CAP);
        g.fill(left - (cap - post) / 2, base - cap + (cap - post) / 2, left - (cap - post) / 2 + cap, base + (cap - post) / 2, GATE_CAP);
        g.fill(right - post - (cap - post) / 2, base - cap + (cap - post) / 2, right - post - (cap - post) / 2 + cap, base + (cap - post) / 2, GATE_CAP);
    }
}
