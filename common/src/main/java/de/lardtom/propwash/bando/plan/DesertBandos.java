package de.lardtom.propwash.bando.plan;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

final class DesertBandos {
    private DesertBandos() {
    }

    static BandoPlan factory(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int w = 15 + 2 * random.nextInt(3);
        int bay = 5;
        int l = bay * (4 + random.nextInt(3)) + 1;
        int h = 7 + random.nextInt(3);
        Hall.Roof roof = random.nextDouble() < 0.65 ? Hall.Roof.SAWTOOTH : Hall.Roof.FLAT;
        int stackHeight = 18 + random.nextInt(9);
        boolean annex = random.nextDouble() < 0.5;
        int g = 1;
        int m = 3;
        int west = annex ? 9 : 0;
        int sx = m + west + w + 8 + m;
        int sz = l + 2 * m;
        int sy = g + Math.max(h + 6, stackHeight + 2);
        BandoPlan p = new BandoPlan(BandoVariant.FACTORY, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        Hall hall = new Hall(m + west, m, w, l, h, bay, roof, g);
        hall.floor(k, Mat.FLOOR);
        hall.clearInside(k);
        hall.frame(k, w >= 17);
        int band = k.range(3, 4);
        hall.cladding(k, band, 0.55);
        hall.endDoors(k, k.odd(5, 7), h - 2, true);
        hall.sideWindows(k, band, 0.6);
        k.patches(hall.x0, g + band + 1, hall.z0 + 1, hall.x0, hall.eave() - 1, hall.z1() - 1, Hall.WALLS, k.range(2, 3), 2, 3);
        k.patches(hall.x1(), g + band + 1, hall.z0 + 1, hall.x1(), hall.eave() - 1, hall.z1() - 1, Hall.WALLS, k.range(2, 3), 2, 3);
        hall.roof(k);
        hall.roofHoles(k, k.range(3, 5), 3, 4);
        if (k.chance(0.6)) {
            hall.stripBays(k, 1);
        }
        if (k.chance(0.6)) {
            boolean side = k.chance(0.5);
            int level = g + k.range(4, 5);
            hall.catwalk(k, side, level, 2);
            spots.add(new int[]{side ? hall.x0 + 1 : hall.x1() - 1, level + 1, hall.z1() - 1, side ? Meta.EAST : Meta.WEST});
        }
        if (k.chance(0.5)) {
            hall.crane(k);
        }
        int scx = hall.x1() + 5;
        int scz = hall.z0 + k.range(3, hall.l - 4);
        int top = g + stackHeight;
        k.disc(scx, scz, 2.5, g, Mat.FOUNDATION, 0);
        k.underFill(scx - 3, scz - 3, scx + 3, scz + 3, Mat.FOUNDATION);
        k.cylinder(scx, scz, 2.5, g + 1, top, Mat.STACK, true);
        k.ringFree(scx, scz, 2.5, top + 1, Mat.TRIM, 0);
        k.clear(scx - 3, g + 1, scz - 1, scx - 2, g + 3, scz);
        int crack = k.range(g + 7, top - 4);
        int dir = k.range(0, 1);
        k.clear(scx + (dir == 0 ? 2 : -1), crack, scz + (dir == 0 ? -1 : 2), scx + (dir == 0 ? 3 : 0), crack + 2, scz + (dir == 0 ? 0 : 3));
        k.ladder(scx, scz + 3, g + 1, top - 3, Meta.SOUTH);
        int pipeY = g + k.range(5, Math.min(h - 1, 7));
        k.beamX(hall.x1() + 1, scx - 3, pipeY, scz, Mat.PIPE);
        k.beamX(hall.x1() + 1, scx - 3, pipeY + 2, scz, Mat.PIPE);
        if (annex) {
            Hall shed = new Hall(m, m + k.range(1, 4), 7, k.range(9, 13), 5, 4, Hall.Roof.FLAT, g);
            shed.floor(k, Mat.FLOOR);
            shed.clearInside(k);
            shed.frame(k, false);
            shed.cladding(k, 3, 0.4);
            shed.endDoors(k, 3, 3, true);
            shed.roof(k);
            shed.roofHoles(k, k.range(1, 3), 2, 3);
            if (k.chance(0.5)) {
                shed.collapse(k, shed.z0 + 4, shed.z1(), 0.5);
            }
            spots.add(new int[]{shed.x0 + 1, g + 1, shed.z0 + 1, Meta.EAST});
            k.scatter(shed.x0 + 1, shed.z0 + 1, shed.x1() - 1, shed.z1() - 1, g + 1, k.range(1, 3), Mat.CARGO);
        }
        k.scatter(hall.x0 + 1, hall.z0 + 1, hall.x1() - 1, hall.z1() - 1, g + 1, k.range(3, 6), Mat.CARGO);
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(2, 4), Mat.CARGO);
        hall.debris(k, k.range(4, 8));
        k.decay(hall.x0, g + 1, hall.z0, hall.x1(), hall.top(), hall.z1(), EnumSet.of(Mat.WALL, Mat.WALL_ALT, Mat.ROOF_STEP), 0.08);
        spots.add(new int[]{hall.x0 + 1, g + 1, hall.z1() - 1, Meta.EAST});
        spots.add(new int[]{hall.x1() - 1, g + 1, hall.z0 + 1, Meta.WEST});
        return p;
    }

    static BandoPlan waterTower(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int span = random.nextBoolean() ? 5 : 7;
        int legs = 10 + random.nextInt(5);
        double radius = span == 5 ? 3.0 : 4.0;
        int tank = 4 + random.nextInt(3);
        int g = 1;
        int m = 4;
        int towerSize = (int) Math.ceil(2 * radius) + 5;
        int sx = m + towerSize + 8 + m;
        int sz = m + towerSize + m;
        int sy = g + legs + tank + (int) radius + 5;
        BandoPlan p = new BandoPlan(BandoVariant.WATER_TOWER, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        int cx = m + towerSize / 2;
        int cz = sz / 2;
        int half = span / 2;
        int deck = g + legs + 1;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                k.column(cx + dx * half, cz + dz * half, g + 1, deck - 1, Mat.COLUMN);
                k.box(cx + dx * half, g, cz + dz * half, cx + dx * half, g, cz + dz * half, Mat.FOUNDATION);
                k.underFill(cx + dx * half, cz + dz * half, cx + dx * half, cz + dz * half, Mat.FOUNDATION);
            }
        }
        for (int y = g + 4; y < deck - 1; y += 4) {
            k.beamX(cx - half + 1, cx + half - 1, y, cz - half, Mat.BEAM);
            k.beamX(cx - half + 1, cx + half - 1, y, cz + half, Mat.BEAM);
            k.beamZ(cx - half, y, cz - half + 1, cz + half - 1, Mat.BEAM);
            k.beamZ(cx + half, y, cz - half + 1, cz + half - 1, Mat.BEAM);
            if (y + 4 < deck - 1 && k.chance(0.6)) {
                int face = k.range(0, 3);
                int fx = face == 1 ? cx + half : face == 3 ? cx - half : 0;
                int fz = face == 0 ? cz - half : face == 2 ? cz + half : 0;
                if (face == 0 || face == 2) {
                    k.line(cx - half + 1, y + 1, fz, cx + half - 1, y + 3, fz, Mat.BRACE, 0);
                } else {
                    k.line(fx, y + 1, cz - half + 1, fx, y + 3, cz + half - 1, Mat.BRACE, 0);
                }
            }
        }
        k.disc(cx, cz, radius + 1.5, deck, Mat.CATWALK, 0);
        k.ringFree(cx, cz, radius + 1.5, deck + 1, Mat.RAIL, 0);
        k.decay(cx - (int) radius - 3, deck + 1, cz - (int) radius - 3, cx + (int) radius + 3, deck + 1, cz + (int) radius + 3, EnumSet.of(Mat.RAIL), 0.35);
        k.disc(cx, cz, radius, deck, Mat.TANK, 0);
        k.cylinder(cx, cz, radius, deck + 1, deck + tank, Mat.TANK, true);
        int roofY = deck + tank + 1;
        for (int i = 0; radius - i * 1.2 > 0.5; i++) {
            k.ringFree(cx, cz, radius - i * 1.2, roofY + i, Mat.ROOF, 0);
            k.disc(cx, cz, radius - i * 1.2, roofY + i, Mat.ROOF, 0);
        }
        int holes = k.range(1, 2);
        for (int i = 0; i < holes; i++) {
            int facing = k.range(0, 3);
            int ox = cx + Meta.dx(facing) * (int) radius;
            int oz = cz + Meta.dz(facing) * (int) radius;
            int y0 = deck + 1 + k.range(0, Math.max(0, tank - 3));
            if (Meta.dx(facing) != 0) {
                k.clear(ox, y0, oz - 1, ox, y0 + 2, oz);
            } else {
                k.clear(ox - 1, y0, oz, ox, y0 + 2, oz);
            }
        }
        if (k.chance(0.7)) {
            int hx = cx + k.range(-1, 0);
            int hz = cz + k.range(-1, 0);
            k.clear(hx, roofY, hz, hx + 1, roofY + (int) radius + 1, hz + 1);
        }
        if (k.chance(0.4)) {
            k.clear(cx - 1, deck, cz - 1, cx, deck, cz);
        }
        k.ladder(cx - half - 1, cz - half, g + 1, deck - 1, Meta.WEST);
        int hx0 = m + towerSize + 1;
        int hz0 = cz - 3;
        k.box(hx0, g, hz0, hx0 + 5, g, hz0 + 5, Mat.FLOOR);
        k.underFill(hx0, hz0, hx0 + 5, hz0 + 5, Mat.FOUNDATION);
        k.box(hx0, g + 1, hz0, hx0 + 5, g + 3, hz0 + 5, Mat.WALL);
        k.clear(hx0 + 1, g + 1, hz0 + 1, hx0 + 4, g + 3, hz0 + 4);
        k.clear(hx0, g + 1, hz0 + 2, hx0, g + 2, hz0 + 3);
        k.clear(hx0 + 5, g + 2, hz0 + 2, hx0 + 5, g + 3, hz0 + 3);
        k.box(hx0, g + 4, hz0, hx0 + 5, g + 4, hz0 + 5, Mat.ROOF);
        k.patches(hx0, g + 4, hz0, hx0 + 5, g + 4, hz0 + 5, Hall.ROOFING, 1, 2, 3);
        k.decay(hx0, g + 1, hz0, hx0 + 5, g + 3, hz0 + 5, EnumSet.of(Mat.WALL), 0.1);
        k.beamX(cx + half + 1, hx0 - 1, g + 1, cz, Mat.PIPE);
        spots.add(new int[]{hx0 + 4, g + 1, hz0 + 4, Meta.NORTH});
        spots.add(new int[]{cx + (int) radius + 1, deck + 1, cz, Meta.WEST});
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(2, 5), Mat.CARGO);
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(2, 4), Mat.DEBRIS_LOW);
        return p;
    }

    static BandoPlan refinery(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int g = 1;
        int sx = 34 + random.nextInt(5);
        int sz = 30 + random.nextInt(5);
        int column = 18 + random.nextInt(7);
        int flare = 20 + random.nextInt(9);
        int sy = g + Math.max(column, flare) + 4;
        BandoPlan p = new BandoPlan(BandoVariant.REFINERY, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        int tanks = 2 + random.nextInt(2);
        int tx = 3;
        for (int i = 0; i < tanks && tx < sx - 8; i++) {
            double r = 3.5 + random.nextInt(3) * 0.5;
            int height = 5 + random.nextInt(4);
            int cx = tx + (int) Math.ceil(r);
            int cz = sz - 3 - (int) Math.ceil(r);
            k.disc(cx, cz, r, g, Mat.FLOOR, 0);
            k.underFill(cx - (int) r - 1, cz - (int) r - 1, cx + (int) r + 1, cz + (int) r + 1, Mat.FOUNDATION);
            k.cylinder(cx, cz, r, g + 1, g + height, Mat.TANK, true);
            if (k.chance(0.6)) {
                k.disc(cx, cz, r, g + height + 1, Mat.ROOF, 0);
                k.patches(cx - (int) r, g + height + 1, cz - (int) r, cx + (int) r, g + height + 1, cz + (int) r, Hall.ROOFING, k.range(1, 2), 3, 4);
            }
            int facing = k.range(0, 3);
            int ox = cx + Meta.dx(facing) * (int) r;
            int oz = cz + Meta.dz(facing) * (int) r;
            if (Meta.dx(facing) != 0) {
                k.clear(ox, g + 1, oz - 1, ox, g + 3, oz + 1);
            } else {
                k.clear(ox - 1, g + 1, oz, ox + 1, g + 3, oz);
            }
            k.ladder(cx, cz - (int) r - 1, g + 1, g + height, Meta.NORTH);
            tx = cx + (int) Math.ceil(r) + 3;
            if (i == 0) {
                spots.add(new int[]{cx, g + 1, cz, Meta.NORTH});
            }
        }
        int rackZ = 6;
        int rackX0 = 3;
        int rackX1 = sx - 10;
        for (int x = rackX0; x <= rackX1; x += 4) {
            k.column(x, rackZ, g + 1, g + 8, Mat.COLUMN);
            k.column(x, rackZ + 4, g + 1, g + 8, Mat.COLUMN);
            k.beamZ(x, g + 5, rackZ + 1, rackZ + 3, Mat.BEAM);
            k.beamZ(x, g + 8, rackZ + 1, rackZ + 3, Mat.BEAM);
            k.box(x, g, rackZ, x, g, rackZ, Mat.FOUNDATION);
            k.box(x, g, rackZ + 4, x, g, rackZ + 4, Mat.FOUNDATION);
        }
        k.beamX(rackX0, rackX1, g + 6, rackZ + 1, Mat.PIPE);
        k.beamX(rackX0, rackX1, g + 6, rackZ + 3, Mat.PIPE);
        k.beamX(rackX0, rackX1, g + 9, rackZ + 2, Mat.PIPE);
        k.decay(rackX0, g + 6, rackZ + 1, rackX1, g + 9, rackZ + 3, EnumSet.of(Mat.PIPE), 0.05);
        int ccx = sx - 6;
        int ccz = rackZ + 2;
        k.disc(ccx, ccz, 3.5, g, Mat.FLOOR, 0);
        k.underFill(ccx - 4, ccz - 4, ccx + 4, ccz + 4, Mat.FOUNDATION);
        k.cylinder(ccx, ccz, 1.5, g + 1, g + column, Mat.STACK, false);
        for (int y = g + 5; y <= g + column - 2; y += 5) {
            k.ringFree(ccx, ccz, 3.5, y, Mat.CATWALK, 0);
            k.disc(ccx, ccz, 3.0, y, Mat.CATWALK, 0);
            k.cylinder(ccx, ccz, 1.5, y, y, Mat.STACK, false);
            k.ringFree(ccx, ccz, 3.5, y + 1, Mat.RAIL, 0);
            k.decay(ccx - 4, y + 1, ccz - 4, ccx + 4, y + 1, ccz + 4, EnumSet.of(Mat.RAIL), 0.4);
            k.decay(ccx - 4, y, ccz - 4, ccx + 4, y, ccz + 4, EnumSet.of(Mat.CATWALK), 0.15);
        }
        k.ladder(ccx - 2, ccz, g + 1, g + column - 2, Meta.WEST);
        k.beamX(rackX1, ccx - 2, g + 6, ccz, Mat.PIPE);
        spots.add(new int[]{ccx + 2, g + 6, ccz + 2, Meta.SOUTH});
        int fx = 2 + random.nextInt(3);
        int fz = 2;
        k.box(fx - 1, g, fz - 1, fx + 1, g, fz + 1, Mat.FOUNDATION);
        k.column(fx, fz, g + 1, g + flare, Mat.STACK);
        k.box(fx - 1, g + flare + 1, fz - 1, fx + 1, g + flare + 1, fz + 1, Mat.TRIM);
        k.plan().set(fx, g + flare + 1, fz, Mat.AIR);
        for (int y = g + 6; y < g + flare - 2; y += 6) {
            k.box(fx - 1, y, fz, fx + 1, y, fz, Mat.BRACE);
            k.box(fx, y, fz - 1, fx, y, fz + 1, Mat.BRACE);
        }
        int bx0 = sx / 2 - 3;
        int bz0 = rackZ + 7;
        k.box(bx0, g, bz0, bx0 + 6, g, bz0 + 4, Mat.FLOOR);
        k.underFill(bx0, bz0, bx0 + 6, bz0 + 4, Mat.FOUNDATION);
        k.box(bx0, g + 1, bz0, bx0 + 6, g + 3, bz0 + 4, Mat.WALL);
        k.clear(bx0 + 1, g + 1, bz0 + 1, bx0 + 5, g + 3, bz0 + 3);
        k.clear(bx0 + 2, g + 1, bz0, bx0 + 3, g + 2, bz0);
        k.clear(bx0 + 6, g + 2, bz0 + 1, bx0 + 6, g + 3, bz0 + 3);
        k.box(bx0, g + 4, bz0, bx0 + 6, g + 4, bz0 + 4, Mat.ROOF);
        k.patches(bx0, g + 4, bz0, bx0 + 6, g + 4, bz0 + 4, Hall.ROOFING, 1, 2, 3);
        spots.add(0, new int[]{bx0 + 5, g + 1, bz0 + 3, Meta.WEST});
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(4, 8), Mat.CARGO);
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(3, 6), Mat.DEBRIS_LOW);
        return p;
    }
}
