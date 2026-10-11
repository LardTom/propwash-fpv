package de.lardtom.propwash.bando.plan;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

final class CoastBandos {
    static final int WATER_ROW = 3;
    static final int PIER_LAND = 6;

    private CoastBandos() {
    }

    static BandoPlan pier(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int w = 5 + 2 * random.nextInt(2);
        int length = 30 + random.nextInt(13);
        boolean crane = random.nextBoolean();
        int g = WATER_ROW;
        int deck = g + 2 + random.nextInt(2);
        int m = crane ? 9 : 4;
        int sx = w + 2 * m;
        int sz = length + 2;
        int sy = deck + 13;
        BandoPlan p = new BandoPlan(BandoVariant.PIER, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        int x0 = m;
        int x1 = m + w - 1;
        int last = length - 1;
        k.box(x0, deck, 0, x1, deck, last, Mat.FLOOR);
        k.underFill(x0, 0, x1, PIER_LAND - 1, Mat.FOUNDATION);
        k.box(x0, 0, 0, x1, deck - 1, PIER_LAND - 1, Mat.FOUNDATION);
        k.clear(x0 - 1, deck + 1, 0, x1 + 1, deck + 4, last);
        k.beamZ(x0, deck - 1, PIER_LAND, last, Mat.BEAM);
        k.beamZ(x1, deck - 1, PIER_LAND, last, Mat.BEAM);
        int pile = 0;
        for (int z = PIER_LAND; z <= last; z += 4) {
            k.column(x0, z, 0, deck - 1, Mat.COLUMN);
            k.column(x1, z, 0, deck - 1, Mat.COLUMN);
            k.underFill(x0, z, x0, z, Mat.COLUMN);
            k.underFill(x1, z, x1, z, Mat.COLUMN);
            if (pile % 2 == 1) {
                k.beamX(x0 + 1, x1 - 1, g + 1, z, Mat.BEAM);
            }
            pile++;
        }
        k.column(x0, last, 0, deck - 1, Mat.COLUMN);
        k.column(x1, last, 0, deck - 1, Mat.COLUMN);
        k.underFill(x0, last, x0, last, Mat.COLUMN);
        k.underFill(x1, last, x1, last, Mat.COLUMN);
        int gap = PIER_LAND + 4 * k.range(2, Math.max(2, (length - PIER_LAND) / 4 - 3)) + 1;
        int gapLength = k.range(3, 3);
        k.clear(x0, deck - 1, gap, x1, deck, gap + gapLength - 1);
        k.decay(x0, deck, PIER_LAND, x1, deck, last, EnumSet.of(Mat.FLOOR), 0.1);
        k.railZ(x0, deck + 1, 1, last, 0.35);
        k.railZ(x1, deck + 1, 1, last, 0.35);
        for (int z = PIER_LAND + 2; z < last - 4; z += 8) {
            if (p.mat(x0, deck, z) == Mat.FLOOR) {
                k.column(x0, z, deck + 1, deck + 3, Mat.RAIL);
                p.set(x0, deck + 4, z, Mat.TRIM);
            }
        }
        int ez = last - 6;
        if (crane) {
            int tx = x1 - 1;
            int tz = last - 3;
            int top = deck + 9;
            for (int dx = 0; dx <= 2; dx += 2) {
                for (int dz = 0; dz <= 2; dz += 2) {
                    k.column(tx - 1 + dx, tz - 1 + dz, deck + 1, top, Mat.COLUMN);
                }
            }
            for (int y = deck + 3; y <= top; y += 3) {
                k.box(tx - 1, y, tz - 1, tx + 1, y, tz + 1, Mat.BEAM, Meta.AXIS_X);
                k.clear(tx, y, tz, tx, y, tz);
            }
            int boom = top;
            k.beamX(tx + 2, Math.min(sx - 1, tx + 2 + k.range(5, 7)), boom, tz, Mat.BEAM);
            int hook = Math.min(sx - 1, tx + 2 + k.range(3, 5));
            k.chain(hook, tz, boom - k.range(2, 4), boom - 1);
            k.beamX(x0 - 1 - k.range(0, 3), tx - 2, boom, tz, Mat.BEAM);
            spots.add(new int[]{x0 + 1, deck + 1, last - 1, Meta.NORTH});
        } else {
            int hx0 = x0 - 2;
            int hx1 = x1 + 2;
            k.box(hx0, deck, ez, hx1, deck, last, Mat.FLOOR);
            for (int dx : new int[]{hx0, hx1}) {
                for (int dz : new int[]{ez, last}) {
                    k.column(dx, dz, 0, deck - 1, Mat.COLUMN);
                    k.underFill(dx, dz, dx, dz, Mat.COLUMN);
                }
            }
            Hall shed = new Hall(hx0, ez, hx1 - hx0 + 1, last - ez + 1, 4, 3, Hall.Roof.FLAT, deck);
            shed.frame(k, false);
            shed.cladding(k, 2, 0.5);
            shed.endDoors(k, 3, 3, true);
            shed.sideWindows(k, 2, 0.8);
            shed.roof(k);
            shed.roofHoles(k, k.range(1, 2), 2, 3);
            spots.add(new int[]{hx0 + 1, deck + 1, last - 1, Meta.EAST});
        }
        k.scatter(x0, PIER_LAND, x1, last, deck + 1, k.range(2, 4), Mat.CARGO);
        spots.add(new int[]{x1 - 1, deck + 1, 2, Meta.WEST});
        return p;
    }

    static BandoPlan platform(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int d = 15 + 2 * random.nextInt(3);
        int derrick = 12 + random.nextInt(5);
        int g = WATER_ROW;
        int lower = g + 5;
        int upper = g + 10;
        int m = 5;
        int sx = d + 2 * m;
        int sz = d + 2 * m;
        int sy = upper + derrick + 4;
        BandoPlan p = new BandoPlan(BandoVariant.PLATFORM, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        int x0 = m;
        int z0 = m;
        int x1 = m + d - 1;
        int z1 = m + d - 1;
        int[] legX = {x0 + 1, x1 - 2};
        int[] legZ = {z0 + 1, z1 - 2};
        for (int lx : legX) {
            for (int lz : legZ) {
                k.box(lx, 0, lz, lx + 1, upper - 1, lz + 1, Mat.COLUMN, Meta.AXIS_Y);
                k.underFill(lx, lz, lx + 1, lz + 1, Mat.COLUMN);
            }
        }
        k.beamX(legX[0] + 2, legX[1] - 1, g + 2, legZ[0], Mat.BEAM);
        k.beamX(legX[0] + 2, legX[1] - 1, g + 2, legZ[1] + 1, Mat.BEAM);
        k.beamZ(legX[0], g + 2, legZ[0] + 2, legZ[1] - 1, Mat.BEAM);
        k.beamZ(legX[1] + 1, g + 2, legZ[0] + 2, legZ[1] - 1, Mat.BEAM);
        k.line(legX[0] + 2, g + 3, legZ[0], legX[1] - 1, lower - 1, legZ[0], Mat.BRACE, 0);
        k.line(legX[0], g + 3, legZ[1] - 1, legX[0], lower - 1, legZ[0] + 2, Mat.BRACE, 0);
        k.box(x0, lower, z0, x1, lower, z1, Mat.CATWALK);
        k.patches(x0, lower, z0, x1, lower, z1, EnumSet.of(Mat.CATWALK), k.range(3, 5), 3, 5);
        k.railX(x0, x1, lower + 1, z0, 0.4);
        k.railZ(x1, lower + 1, z0, z1, 0.4);
        k.box(x0 - 1, upper, z0 - 1, x1 + 1, upper, z1 + 1, Mat.FLOOR);
        k.box(x0 - 1, upper - 1, z0 - 1, x1 + 1, upper - 1, z0 - 1, Mat.BEAM, Meta.AXIS_X);
        k.box(x0 - 1, upper - 1, z1 + 1, x1 + 1, upper - 1, z1 + 1, Mat.BEAM, Meta.AXIS_X);
        k.box(x0 - 1, upper - 1, z0, x0 - 1, upper - 1, z1, Mat.BEAM, Meta.AXIS_Z);
        k.box(x1 + 1, upper - 1, z0, x1 + 1, upper - 1, z1, Mat.BEAM, Meta.AXIS_Z);
        for (int z = z0 + 3; z < z1; z += 4) {
            k.beamX(x0, x1, upper - 1, z, Mat.BEAM);
        }
        k.patches(x0, upper, z0, x1, upper, z1, EnumSet.of(Mat.FLOOR), k.range(2, 4), 3, 4);
        k.railX(x0 - 1, x1 + 1, upper + 1, z0 - 1, 0.3);
        k.railX(x0 - 1, x1 + 1, upper + 1, z1 + 1, 0.3);
        k.railZ(x0 - 1, upper + 1, z0, z1, 0.3);
        k.railZ(x1 + 1, upper + 1, z0, z1, 0.3);
        for (int i = 0; i < upper - lower - 1; i++) {
            p.set(x0 + 2 + i, lower + 1 + i, z1 - 1, Mat.ROOF_STEP, Meta.EAST);
        }
        k.clear(x0 + 2 + upper - lower - 3, upper, z1 - 1, x0 + 2 + upper - lower - 1, upper, z1 - 1);
        int dx0 = x0 + d / 2 - 2;
        int dz0 = z0 + d / 2 - 2;
        int top = upper + derrick;
        for (int cx = dx0; cx <= dx0 + 4; cx += 4) {
            for (int cz = dz0; cz <= dz0 + 4; cz += 4) {
                k.column(cx, cz, upper + 1, top, Mat.COLUMN);
            }
        }
        for (int y = upper + 4; y <= top; y += 4) {
            k.beamX(dx0 + 1, dx0 + 3, y, dz0, Mat.BEAM);
            k.beamX(dx0 + 1, dx0 + 3, y, dz0 + 4, Mat.BEAM);
            k.beamZ(dx0, y, dz0 + 1, dz0 + 3, Mat.BEAM);
            k.beamZ(dx0 + 4, y, dz0 + 1, dz0 + 3, Mat.BEAM);
            if (k.chance(0.6)) {
                k.line(dx0 + 1, y - 3, dz0, dx0 + 3, y - 1, dz0, Mat.BRACE, 0);
            }
            if (k.chance(0.6)) {
                k.line(dx0 + 4, y - 3, dz0 + 1, dx0 + 4, y - 1, dz0 + 3, Mat.BRACE, 0);
            }
        }
        k.box(dx0, top + 1, dz0, dx0 + 4, top + 1, dz0 + 4, Mat.TRIM);
        k.clear(dx0 + 1, top + 1, dz0 + 1, dx0 + 3, top + 1, dz0 + 3);
        k.clear(dx0 + 1, upper, dz0 + 1, dx0 + 3, upper, dz0 + 3);
        k.chain(dx0 + 2, dz0 + 2, upper + 3, top);
        int px = x1 - 1;
        int pz = z0 + 1;
        k.column(px, pz, upper + 1, upper + 4, Mat.COLUMN);
        k.line(px, upper + 5, pz, Math.min(sx - 1, px + 5), upper + 8, Math.max(0, pz - 5), Mat.BEAM, Meta.AXIS_X);
        k.plan().set(px, upper + 5, pz, Mat.BEAM, Meta.AXIS_X);
        int hook = Math.min(sx - 1, px + 4);
        k.chain(hook, Math.max(0, pz - 4), upper + 3, upper + 6);
        if (k.chance(0.6)) {
            int cx0 = x0 + 1;
            int cz0 = z0 + 1;
            k.box(cx0, upper + 1, cz0, cx0 + 4, upper + 2, cz0 + 1, Mat.WALL_ALT);
            k.clear(cx0 + 1, upper + 1, cz0, cx0 + 3, upper + 2, cz0 + 1);
            k.box(cx0, upper + 3, cz0, cx0 + 4, upper + 3, cz0 + 1, Mat.WALL_ALT);
            spots.add(new int[]{cx0 + 2, upper + 1, cz0 + 1, Meta.NORTH});
        }
        spots.add(new int[]{x1 - 1, lower + 1, z0 + 2, Meta.WEST});
        spots.add(new int[]{x0 + 1, upper + 1, z1 - 3, Meta.EAST});
        k.scatter(x0, z0, x1, z1, upper + 1, k.range(2, 4), Mat.CARGO);
        return p;
    }
}
