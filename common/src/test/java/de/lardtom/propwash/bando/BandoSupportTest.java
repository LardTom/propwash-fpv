package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.bando.plan.Meta;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandoSupportTest {
    private static final int SEEDS = 60;
    private static final double EPS = 0.01;

    private record Shape(double lo, double hi) {
        static final Shape FULL = new Shape(0.0, 1.0);

        boolean touchesAbove() {
            return this.hi >= 1.0 - EPS;
        }

        boolean touchesBelow() {
            return this.lo <= EPS;
        }

        boolean overlaps(Shape other) {
            return Math.min(this.hi, other.hi) - Math.max(this.lo, other.lo) > EPS;
        }
    }

    private static long seed(BandoVariant variant, int i) {
        return 0x9E3779B97F4A7C15L * (i + 1) + variant.ordinal() * 1_000_003L;
    }

    static Shape shape(String state, boolean solid) {
        if (state.contains("_slab")) {
            if (solid || state.contains("type=double")) {
                return Shape.FULL;
            }
            return state.contains("type=top") ? new Shape(0.5, 1.0) : new Shape(0.0, 0.5);
        }
        if (state.contains("snow[layers=")) {
            return solid ? Shape.FULL : new Shape(0.0, Integer.parseInt(state.replaceAll(".*layers=(\\d+).*", "$1")) / 8.0);
        }
        if (state.contains("_carpet") || state.contains("lily_pad")) {
            return new Shape(0.0, 0.0625);
        }
        if (state.contains("minecraft:chest")) {
            return new Shape(0.0, 0.875);
        }
        if (state.contains("lantern")) {
            return new Shape(0.0, 0.5625);
        }
        return Shape.FULL;
    }

    private static Shape shape(BandoPalettes.Palette palette, Mat mat, int meta) {
        boolean solid = (meta & Meta.SOLID) != 0;
        double lo = 0.0;
        double hi = 1.0;
        List<BandoPalettes.Entry> entries = palette.entries(mat);
        if (entries.isEmpty()) {
            return null;
        }
        for (BandoPalettes.Entry entry : entries) {
            Shape s = shape(entry.state(), solid);
            lo = Math.max(lo, s.lo());
            hi = Math.min(hi, s.hi());
        }
        return new Shape(lo, hi);
    }

    static List<String> floating(BandoPlan plan, BandoPalettes.Palette palette, int limit) {
        boolean groundHolds = plan.variant().family() != BandoFamily.COAST;
        int sx = plan.sizeX();
        int sy = plan.sizeY();
        int sz = plan.sizeZ();
        int g = plan.ground();
        Shape[] shapes = new Shape[sx * sy * sz];
        boolean[] seen = new boolean[sx * sy * sz];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    Mat mat = plan.mat(x, y, z);
                    if (!mat.holdsUp() && mat.kind() != Mat.Kind.DECOR) {
                        continue;
                    }
                    Shape s = shape(palette, mat, plan.meta(x, y, z));
                    int i = (y * sz + z) * sx + x;
                    shapes[i] = s;
                    if (s == null) {
                        continue;
                    }
                    Mat below = plan.mat(x, y - 1, z);
                    boolean seed = groundHolds && (y < g || y <= g + 1 && below == Mat.EMPTY && s.touchesBelow())
                            || y == 0 && plan.fill(x, z) != Mat.EMPTY
                            || mat == Mat.FLOAT && below == Mat.WATER;
                    if (seed) {
                        seen[i] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            Mat from = plan.mat(c[0], c[1], c[2]);
            if (from.kind() == Mat.Kind.DECOR) {
                continue;
            }
            Shape fromShape = shapes[(c[1] * sz + c[2]) * sx + c[0]];
            boolean step = from == Mat.ROOF_STEP;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (axes == 0 || axes == 3 || axes == 2 && (!step || dy == 0)) {
                            continue;
                        }
                        int x = c[0] + dx;
                        int y = c[1] + dy;
                        int z = c[2] + dz;
                        if (!plan.inside(x, y, z)) {
                            continue;
                        }
                        int i = (y * sz + z) * sx + x;
                        Mat mat = plan.mat(x, y, z);
                        Shape to = shapes[i];
                        if (seen[i] || to == null || from.kind() == Mat.Kind.ATTACHED && mat.kind() == Mat.Kind.STRUCTURE
                                || axes == 2 && mat != Mat.ROOF_STEP && mat != Mat.ROOF) {
                            continue;
                        }
                        boolean touch;
                        if (axes == 2) {
                            touch = true;
                        } else if (dy > 0) {
                            touch = fromShape.touchesAbove() && to.touchesBelow();
                        } else if (dy < 0) {
                            touch = to.touchesAbove() && fromShape.touchesBelow();
                        } else {
                            touch = fromShape.overlaps(to);
                        }
                        if (touch) {
                            seen[i] = true;
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }
        List<String> cells = new ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = (y * sz + z) * sx + x;
                    if (shapes[i] != null && !seen[i] && cells.size() < limit) {
                        cells.add(plan.mat(x, y, z) + "@" + x + "," + y + "," + z + " auf " + plan.mat(x, y - 1, z));
                    }
                }
            }
        }
        return cells;
    }

    @Test
    void nothingFloatsWithTheRealBlockShapes() {
        for (BandoVariant variant : BandoVariant.values()) {
            for (int i = 0; i < SEEDS; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                for (BandoPalettes.Palette palette : BandoPalettes.of(variant)) {
                    List<String> cells = floating(plan, palette, 6);
                    assertTrue(cells.isEmpty(), variant + "/" + i + "/" + palette.name() + ": schwebt " + cells);
                }
            }
        }
    }

    @Test
    void decorationsRestOnFullBlocks() {
        for (BandoVariant variant : BandoVariant.values()) {
            for (int i = 0; i < SEEDS; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                int g = plan.ground();
                for (int y = 1; y < plan.sizeY(); y++) {
                    for (int z = 0; z < plan.sizeZ(); z++) {
                        for (int x = 0; x < plan.sizeX(); x++) {
                            Mat mat = plan.mat(x, y, z);
                            boolean resting = mat.kind() == Mat.Kind.LOOSE && mat != Mat.YARD || mat == Mat.MOSS || mat == Mat.FOLIAGE;
                            if (!resting || plan.mat(x, y + 1, z) == Mat.CHAIN) {
                                continue;
                            }
                            Mat below = plan.mat(x, y - 1, z);
                            boolean solid = below.slab() && (plan.meta(x, y - 1, z) & Meta.SOLID) != 0;
                            boolean ok = below.carries() || solid || below == Mat.EMPTY && y - 1 <= g || mat == Mat.FOLIAGE && below.flatTop();
                            assertTrue(ok, variant + "/" + i + ": " + mat + "@" + x + "," + y + "," + z + " liegt auf " + below);
                        }
                    }
                }
            }
        }
    }

    @Test
    void standingPartsReachTheGround() {
        for (BandoVariant variant : BandoVariant.values()) {
            if (variant.family() == BandoFamily.COAST) {
                continue;
            }
            for (int i = 0; i < SEEDS; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                int g = plan.ground();
                for (int z = 0; z < plan.sizeZ(); z++) {
                    for (int x = 0; x < plan.sizeX(); x++) {
                        for (int y = 0; y <= g + 1; y++) {
                            Mat mat = plan.mat(x, y, z);
                            if (!mat.holdsUp() || mat == Mat.YARD) {
                                continue;
                            }
                            Mat under = plan.mat(x, g, z);
                            if (mat.kind() == Mat.Kind.STRUCTURE && (y <= g || under == Mat.EMPTY || under == Mat.YARD)) {
                                assertFalse(plan.fill(x, z) == Mat.EMPTY, variant + "/" + i + ": " + mat + "@" + x + "," + y + "," + z + " ohne Fundament");
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    @Test
    void mountainBandoBuildsWithoutSlabs() {
        for (BandoPalettes.Palette palette : BandoPalettes.of(BandoVariant.MINE_HEAD)) {
            for (Mat mat : Mat.values()) {
                if (mat == Mat.DEBRIS_LOW) {
                    continue;
                }
                for (BandoPalettes.Entry entry : palette.entries(mat)) {
                    assertFalse(entry.state().contains("_slab"), palette.name() + ": " + mat + " ist eine Stufe (" + entry.state() + ")");
                }
            }
        }
    }

    @Test
    void shapeModelKnowsSlabsStairsAndLayers() {
        assertEquals(new Shape(0.0, 0.5), shape("minecraft:oak_slab", false));
        assertEquals(new Shape(0.5, 1.0), shape("minecraft:oak_slab[type=top]", false));
        assertEquals(Shape.FULL, shape("minecraft:oak_slab", true));
        assertEquals(Shape.FULL, shape("minecraft:snow[layers=3]", true));
        assertEquals(Shape.FULL, shape("minecraft:oak_stairs", false));
        assertTrue(shape("minecraft:moss_carpet", false).hi() < 0.1);
    }
}
