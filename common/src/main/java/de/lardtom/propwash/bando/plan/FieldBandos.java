package de.lardtom.propwash.bando.plan;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

final class FieldBandos {
    private FieldBandos() {
    }

    static BandoPlan barn(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int w = 9 + 2 * random.nextInt(3);
        int l = 4 * (3 + random.nextInt(3)) + 1;
        int h = 4 + random.nextInt(2);
        boolean silo = random.nextDouble() < 0.5;
        int siloHeight = h + 7 + random.nextInt(5);
        int g = 1;
        int m = 3;
        int sx = w + 2 * m + (silo ? 9 : 0);
        int sz = l + 2 * m;
        int sy = Math.max(g + h + (w + 1) / 2 + 2, g + siloHeight + 3);
        BandoPlan p = new BandoPlan(BandoVariant.BARN, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        Hall hall = new Hall(m, m, w, l, h, 4, Hall.Roof.GABLE, g);
        hall.floor(k, Mat.FLOOR);
        hall.clearInside(k);
        hall.frame(k, false);
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? hall.x0 : hall.x1();
            k.beamZ(x, g + 2, hall.z0, hall.z1(), Mat.BEAM);
        }
        hall.cladding(k, hall.h - 1, 0.0);
        hall.gableWalls(k, 0.2);
        hall.roof(k);
        int door = k.odd(3, 5);
        hall.endDoors(k, door, hall.h - 1, true);
        int cx = hall.x0 + hall.w / 2;
        k.clear(cx - 1, hall.eave() + 1, hall.z0, cx, hall.eave() + 2, hall.z0);
        hall.sideWindows(k, 2, 0.7);
        k.patches(hall.x0, g + 1, hall.z0 + 1, hall.x0, hall.eave() - 1, hall.z1() - 1, Hall.WALLS, k.range(3, 5), 2, 3);
        k.patches(hall.x1(), g + 1, hall.z0 + 1, hall.x1(), hall.eave() - 1, hall.z1() - 1, Hall.WALLS, k.range(3, 5), 2, 3);
        k.decay(hall.x0, g + 1, hall.z0, hall.x1(), hall.top(), hall.z1(), EnumSet.of(Mat.WALL), 0.12);
        boolean loft = k.chance(0.7);
        int loftEnd = hall.z0 + Math.max(4, hall.l / 3);
        if (loft) {
            k.box(hall.x0 + 1, g + 3, hall.z0 + 1, hall.x1() - 1, g + 3, loftEnd, Mat.FLOOR);
            k.decay(hall.x0 + 1, g + 3, hall.z0 + 1, hall.x1() - 1, g + 3, loftEnd, EnumSet.of(Mat.FLOOR), 0.1);
            k.scatter(hall.x0 + 1, hall.z0 + 1, hall.x1() - 1, loftEnd - 1, g + 4, k.range(2, 4), Mat.CARGO);
            k.ladder(hall.x0 + 1, loftEnd + 1, g + 1, g + 3, Meta.EAST);
            spots.add(new int[]{hall.x1() - 1, g + 4, hall.z0 + 1, Meta.WEST});
        }
        hall.roofHoles(k, k.range(3, 5), 2, 4);
        if (k.chance(0.5)) {
            int z = hall.z0 + 4 * k.range(1, Math.max(1, hall.l / 4 - 2));
            hall.collapse(k, z, z + 3, 0.2);
        }
        if (silo) {
            int scx = hall.x1() + 5;
            int scz = hall.z0 + hall.l / 2;
            int top = g + siloHeight;
            k.disc(scx, scz, 2.5, g, Mat.FLOOR, 0);
            k.underFill(scx - 3, scz - 3, scx + 3, scz + 3, Mat.FOUNDATION);
            k.cylinder(scx, scz, 2.5, g + 1, top, Mat.STACK, true);
            k.clear(scx - 3, g + 1, scz - 1, scx - 2, g + 3, scz);
            int hole = k.range(g + 5, top - 3);
            k.clear(scx + 2, hole, scz - 1, scx + 3, hole + 2, scz);
            k.ringFree(scx, scz, 2.5, top + 1, Mat.TRIM, 0);
            k.decay(scx - 3, top - 1, scz - 3, scx + 3, top + 1, scz + 3, EnumSet.of(Mat.STACK, Mat.TRIM), 0.3);
            k.ladder(scx, scz - 3, g + 1, top - 2, Meta.NORTH);
        } else if (k.chance(0.6)) {
            int fx0 = hall.x1() + 2;
            int fx1 = p.sizeX() - 1;
            k.railZ(fx1, g + 1, hall.z0, hall.z1(), 0.35);
            k.railX(fx0, fx1, g + 1, hall.z0, 0.35);
            k.railX(fx0, fx1, g + 1, hall.z1(), 0.35);
        }
        k.scatter(0, 0, p.sizeX() - 1, p.sizeZ() - 1, g + 1, k.range(3, 6), Mat.CARGO);
        hall.debris(k, k.range(3, 6));
        k.vines(hall.x0 - 1, g + 1, hall.z0 - 1, hall.x1() + 1, hall.eave(), hall.z1() + 1, 0.03, 3);
        spots.add(new int[]{hall.x0 + 1, g + 1, hall.z1() - 1, Meta.EAST});
        spots.add(new int[]{hall.x1() - 1, g + 1, hall.z0 + 1, Meta.WEST});
        return p;
    }

    static BandoPlan warehouse(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int w = 15 + 2 * random.nextInt(4);
        int bay = 5 + random.nextInt(2);
        int l = bay * (4 + random.nextInt(3)) + 1;
        int h = 7 + random.nextInt(2);
        Hall.Roof roof = random.nextBoolean() ? Hall.Roof.FLAT : Hall.Roof.PITCHED;
        int g = 1;
        int m = 3;
        BandoPlan p = new BandoPlan(BandoVariant.WAREHOUSE, seed, w + 2 * m, g + h + 6, l + 2 * m + 3, g);
        Kit k = new Kit(p, random);
        Hall hall = new Hall(m, m + 3, w, l, h, bay, roof, g);
        hall.floor(k, Mat.FLOOR);
        k.box(hall.x0, g, m, hall.x1(), g, hall.z0 - 1, Mat.FLOOR);
        k.underFill(hall.x0, m, hall.x1(), hall.z0 - 1, Mat.FOUNDATION);
        hall.clearInside(k);
        hall.frame(k, hall.w >= 19);
        int band = k.range(2, 3);
        hall.cladding(k, band, 0.35);
        hall.endDoors(k, hall.w - k.range(4, 6), hall.h - 2, true);
        hall.sideWindows(k, band, 0.5);
        hall.roof(k);
        hall.roofHoles(k, k.range(4, 7), 3, 5);
        hall.stripBays(k, k.range(1, 2));
        k.decay(hall.x0, g + 1, hall.z0, hall.x1(), hall.eave(), hall.z1(), EnumSet.of(Mat.WALL, Mat.WALL_ALT), 0.1);
        if (k.chance(0.7)) {
            boolean west = k.chance(0.5);
            int level = g + k.range(4, 5);
            hall.catwalk(k, west, level, 2);
            spots.add(new int[]{west ? hall.x0 + 1 : hall.x1() - 1, level + 1, hall.z1() - 1, west ? Meta.EAST : Meta.WEST});
        }
        if (k.chance(0.5)) {
            hall.crane(k);
        }
        if (k.chance(0.45)) {
            int ox0 = hall.x0 + 1;
            int oz1 = hall.z1() - 1;
            int ox1 = ox0 + 4;
            int oz0 = oz1 - 4;
            int floor = g + 3;
            k.column(ox1, oz0, g + 1, floor - 1, Mat.COLUMN);
            k.column(ox1, oz1, g + 1, floor - 1, Mat.COLUMN);
            k.box(ox0, floor, oz0, ox1, floor, oz1, Mat.CATWALK);
            k.box(ox0, floor + 1, oz0, ox1, floor + 2, oz0, Mat.WALL_ALT);
            k.box(ox1, floor + 1, oz0, ox1, floor + 2, oz1, Mat.WALL_ALT);
            k.clear(ox0 + 1, floor + 1, oz0, ox0 + 2, floor + 2, oz0);
            k.clear(ox1, floor + 2, oz0 + 1, ox1, floor + 2, oz1 - 1);
            k.box(ox0, floor + 3, oz0, ox1, floor + 3, oz1, Mat.ROOF);
            k.patches(ox0, floor + 3, oz0, ox1, floor + 3, oz1, Hall.ROOFING, 1, 2, 2);
            k.ladder(ox1 + 1, oz0 + 2, g + 1, floor, Meta.EAST);
            spots.add(new int[]{ox0 + 1, floor + 1, oz1, Meta.NORTH});
        }
        if (k.chance(0.35)) {
            int z = hall.z0 + bay * k.range(1, Math.max(1, hall.l / bay - 1));
            hall.collapse(k, z, z + bay, 0.3);
        }
        k.scatter(hall.x0 + 1, hall.z0 + 1, hall.x1() - 1, hall.z1() - 1, g + 1, k.range(3, 7), Mat.CARGO);
        k.scatter(hall.x0, m, hall.x1(), hall.z0 - 1, g + 1, k.range(1, 3), Mat.CARGO);
        hall.debris(k, k.range(4, 8));
        k.vines(hall.x0 - 1, g + 1, hall.z0 - 1, hall.x1() + 1, hall.eave(), hall.z1() + 1, 0.04, 4);
        spots.add(new int[]{hall.x0 + 1, g + 1, hall.z0 + 2, Meta.EAST});
        spots.add(new int[]{hall.x1() - 1, g + 1, hall.z1() - 2, Meta.WEST});
        return p;
    }
}
