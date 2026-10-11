package de.lardtom.propwash.bando.plan;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

final class RuinBandos {
    private RuinBandos() {
    }

    static BandoPlan collapsedHall(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int w = 17 + 2 * random.nextInt(4);
        int bay = 5;
        int l = bay * (5 + random.nextInt(2)) + 1;
        int h = 8 + random.nextInt(3);
        Hall.Roof roof = random.nextBoolean() ? Hall.Roof.FLAT : Hall.Roof.PITCHED;
        int g = 1;
        int m = 3;
        BandoPlan p = new BandoPlan(BandoVariant.COLLAPSED_HALL, seed, w + 2 * m, g + h + 6, l + 2 * m, g);
        Kit k = new Kit(p, random);
        Hall hall = new Hall(m, m, w, l, h, bay, roof, g);
        hall.floor(k, Mat.FLOOR);
        hall.clearInside(k);
        hall.frame(k, w >= 21);
        int band = k.range(3, 4);
        hall.cladding(k, band, 0.5);
        hall.endDoors(k, w - k.range(6, 8), h - 2, true);
        hall.sideWindows(k, band, 0.55);
        hall.roof(k);
        hall.roofHoles(k, k.range(3, 5), 3, 5);
        boolean tiers = k.chance(0.6);
        if (tiers) {
            for (int i = 0; i < 4; i++) {
                int x = hall.x1() - 1 - i;
                int top = g + 4 - i;
                k.box(x, g + 1, hall.z0 + 2, x, top - 1, hall.z1() - 2, Mat.TRIM);
                k.box(x, top, hall.z0 + 2, x, top, hall.z1() - 2, Mat.ROOF_STEP, Meta.EAST);
            }
            k.patches(hall.x1() - 4, g + 1, hall.z0 + 2, hall.x1() - 1, g + 4, hall.z1() - 2, EnumSet.of(Mat.TRIM, Mat.ROOF_STEP), k.range(1, 2), 2, 3);
            spots.add(new int[]{hall.x1() - 1, g + 5, hall.z0 + 2, Meta.WEST});
        }
        boolean west = !tiers || k.chance(0.5);
        int level = g + k.range(5, 6);
        hall.catwalk(k, west, level, 2);
        spots.add(new int[]{west ? hall.x0 + 1 : hall.x1() - 1, level + 1, hall.z1() - 1, west ? Meta.EAST : Meta.WEST});
        int bays = l / bay;
        int start = k.chance(0.5) ? bays - 2 : k.range(1, Math.max(1, bays - 3));
        hall.collapse(k, hall.z0 + start * bay, hall.z0 + (start + k.range(2, 3)) * bay, 0.65);
        k.decay(hall.x0, g + 1, hall.z0, hall.x1(), hall.eave(), hall.z1(), EnumSet.of(Mat.WALL, Mat.WALL_ALT), 0.1);
        hall.debris(k, k.range(6, 10));
        k.scatter(hall.x0 + 1, hall.z0 + 1, hall.x1() - 1, hall.z1() - 1, g + 1, k.range(2, 4), Mat.DEBRIS);
        spots.add(new int[]{hall.x0 + 1, g + 1, hall.z0 + 2, Meta.EAST});
        spots.add(new int[]{hall.x1() - 1, g + 1, hall.z1() - 1, Meta.WEST});
        return p;
    }

    static BandoPlan concreteFrame(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int nx = 3 + random.nextInt(2);
        int nz = 2 + random.nextInt(2);
        int bay = 5 + random.nextInt(2);
        int storeys = 3 + random.nextInt(3);
        int storey = 4;
        int g = 1;
        int m = 3;
        int w = nx * bay + 1;
        int l = nz * bay + 1;
        BandoPlan p = new BandoPlan(BandoVariant.CONCRETE_FRAME, seed, w + 2 * m, g + storeys * storey + 5, l + 2 * m, g);
        Kit k = new Kit(p, random);
        int x0 = m;
        int z0 = m;
        int x1 = x0 + w - 1;
        int z1 = z0 + l - 1;
        k.box(x0, g, z0, x1, g, z1, Mat.FLOOR);
        k.underFill(x0, z0, x1, z1, Mat.FOUNDATION);
        k.clear(x0, g + 1, z0, x1, g + storeys * storey + 3, z1);
        int shaftX = random.nextInt(nx);
        int shaftZ = random.nextInt(nz);
        int top = g + storeys * storey;
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= nz; j++) {
                int x = x0 + i * bay;
                int z = z0 + j * bay;
                int height = top;
                if (k.chance(0.35)) {
                    height = top - k.range(1, storey + 1);
                }
                k.column(x, z, g + 1, height, Mat.COLUMN);
                if (height == top || k.chance(0.5)) {
                    k.column(x, z, height + 1, height + k.range(1, 2), Mat.BRACE);
                }
            }
        }
        for (int s = 1; s <= storeys; s++) {
            int y = g + s * storey;
            for (int j = 0; j <= nz; j++) {
                int z = z0 + j * bay;
                for (int i = 0; i < nx; i++) {
                    if (s < storeys || k.chance(0.6)) {
                        k.beamX(x0 + i * bay + 1, x0 + (i + 1) * bay - 1, y, z, Mat.BEAM);
                    }
                }
            }
            for (int i = 0; i <= nx; i++) {
                int x = x0 + i * bay;
                for (int j = 0; j < nz; j++) {
                    if (s < storeys || k.chance(0.6)) {
                        k.beamZ(x, y, z0 + j * bay + 1, z0 + (j + 1) * bay - 1, Mat.BEAM);
                    }
                }
            }
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < nz; j++) {
                    if ((i == shaftX && j == shaftZ) || !k.chance(s == storeys ? 0.45 : 0.7)) {
                        continue;
                    }
                    int bx = x0 + i * bay;
                    int bz = z0 + j * bay;
                    k.box(bx + 1, y, bz + 1, bx + bay - 1, y, bz + bay - 1, Mat.FLOOR);
                    if (k.chance(0.35)) {
                        int hx = bx + 1 + k.range(0, Math.max(0, bay - 4));
                        int hz = bz + 1 + k.range(0, Math.max(0, bay - 4));
                        k.clear(hx, y, hz, hx + 2, y, hz + 2);
                    }
                    if (s == storeys && k.chance(0.5)) {
                        k.box(bx, y + 1, bz, bx + bay, y + 1, bz, Mat.WALL);
                        k.decay(bx, y + 1, bz, bx + bay, y + 1, bz, EnumSet.of(Mat.WALL), 0.4);
                    }
                }
            }
            int sx0 = x0 + shaftX * bay + 1;
            int sz0 = z0 + shaftZ * bay + 1;
            for (int i = 0; i < storey - 1 && sz0 + i <= z0 + shaftZ * bay + bay - 1; i++) {
                k.plan().set(sx0, y - storey + 1 + i, sz0 + i, Mat.ROOF_STEP, Meta.SOUTH);
            }
        }
        for (int i = 0; i < nx; i++) {
            if (k.chance(0.5)) {
                int bx = x0 + i * bay;
                k.box(bx + 1, g + 1, z0, bx + bay - 1, g + storey - 1, z0, Mat.WALL);
                k.clear(bx + 2, g + 1, z0, bx + bay - 2, g + 2, z0);
            }
            if (k.chance(0.5)) {
                int bx = x0 + i * bay;
                k.box(bx + 1, g + 1, z1, bx + bay - 1, g + 2, z1, Mat.WALL);
                k.clear(bx + 2, g + 2, z1, bx + bay - 3, g + 2, z1);
            }
        }
        k.decay(x0, g + 1, z0, x1, top + 2, z1, EnumSet.of(Mat.WALL, Mat.FLOOR), 0.05);
        k.vines(x0 - 1, g + 2, z0 - 1, x1 + 1, top + 2, z1 + 1, 0.22, 6);
        k.foliage(x0, g + storey, z0, x1, top + 2, z1, EnumSet.of(Mat.FLOOR, Mat.BEAM), 0.025, 2);
        k.moss(x0, g, z0, x1, top + 1, z1, EnumSet.of(Mat.FLOOR, Mat.BEAM), 0.25);
        k.scatter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, g + 1, k.range(4, 8), Mat.DEBRIS_LOW);
        k.scatter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, g + 1, k.range(1, 3), Mat.DEBRIS);
        int cornerX = shaftX == 0 ? x1 - 1 : x0 + 1;
        spots.add(new int[]{cornerX, g + storey + 1, z0 + 1, cornerX == x0 + 1 ? Meta.EAST : Meta.WEST});
        spots.add(new int[]{x0 + 1, g + 1, z1 - 1, Meta.EAST});
        spots.add(new int[]{x1 - 1, g + 1, z0 + 1, Meta.WEST});
        return p;
    }

    static BandoPlan sunkenWarehouse(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int w = 13 + 2 * random.nextInt(3);
        int bay = 5;
        int l = bay * (3 + random.nextInt(3)) + 1;
        int h = 7;
        int g = 1;
        int m = 3;
        BandoPlan p = new BandoPlan(BandoVariant.SUNKEN_WAREHOUSE, seed, w + 2 * m, g + h + 4, l + 2 * m, g);
        Kit k = new Kit(p, random);
        Hall hall = new Hall(m, m, w, l, h, bay, Hall.Roof.FLAT, g);
        hall.floor(k, Mat.FLOOR);
        hall.clearInside(k);
        hall.frame(k, false);
        hall.cladding(k, 4, 0.2);
        hall.endDoors(k, w - 6, 4, true);
        hall.sideWindows(k, 4, 0.5);
        hall.roof(k);
        hall.roofHoles(k, k.range(5, 8), 3, 6);
        hall.stripBays(k, k.range(1, 2));
        int bays = l / bay;
        int start = k.range(0, Math.max(0, bays - 2));
        hall.collapse(k, hall.z0 + start * bay, hall.z0 + (start + 1) * bay + k.range(0, bay), 0.4);
        k.decay(hall.x0, g + 3, hall.z0, hall.x1(), hall.eave(), hall.z1(), EnumSet.of(Mat.WALL, Mat.WALL_ALT), 0.2);
        boolean west = k.chance(0.5);
        hall.catwalk(k, west, g + 4, 2);
        spots.add(new int[]{west ? hall.x0 + 1 : hall.x1() - 1, g + 5, hall.z1() - 1, west ? Meta.EAST : Meta.WEST});
        spots.add(new int[]{west ? hall.x0 + 2 : hall.x1() - 2, g + 5, hall.z0 + 3, west ? Meta.EAST : Meta.WEST});
        for (int x = hall.x0 + 1; x < hall.x1(); x++) {
            for (int z = hall.z0 + 1; z < hall.z1(); z++) {
                for (int y = g + 1; y <= g + 2; y++) {
                    Mat at = p.mat(x, y, z);
                    if (at == Mat.AIR || at == Mat.EMPTY) {
                        p.set(x, y, z, Mat.WATER);
                    }
                }
                if (p.mat(x, g + 2, z) == Mat.WATER && p.mat(x, g + 3, z) == Mat.AIR && k.chance(0.07)) {
                    p.set(x, g + 3, z, Mat.FLOAT);
                }
            }
        }
        k.vines(hall.x0 - 1, g + 4, hall.z0 - 1, hall.x1() + 1, hall.eave(), hall.z1() + 1, 0.15, 3);
        k.foliage(hall.x0, hall.eave(), hall.z0, hall.x1(), hall.top(), hall.z1(), EnumSet.of(Mat.BEAM, Mat.ROOF), 0.02, 1);
        k.moss(hall.x0, g + 3, hall.z0, hall.x1(), hall.top(), hall.z1(), EnumSet.of(Mat.WALL, Mat.CATWALK, Mat.ROOF), 0.2);
        return p;
    }

    static BandoPlan mineHead(long seed, List<int[]> spots) {
        Random random = new Random(seed);
        int tower = 14 + random.nextInt(5);
        int gallery = 12 + random.nextInt(5);
        int depth = 6;
        int g = depth + 1;
        int m = 3;
        int houseW = 9;
        int houseL = 11 + 2 * random.nextInt(2);
        int sx = m + 6 + gallery + 7 + m;
        int sz = m + Math.max(7, houseL) + 2 + 7 + m;
        int sy = g + tower + 9;
        BandoPlan p = new BandoPlan(BandoVariant.MINE_HEAD, seed, sx, sy, sz, g);
        Kit k = new Kit(p, random);
        int hx = sx - m - 4;
        int hz = m + 3;
        int deck = g + tower;
        k.box(hx - 3, g, hz - 3, hx + 3, g, hz + 3, Mat.FLOOR);
        k.underFill(hx - 3, hz - 3, hx + 3, hz + 3, Mat.FOUNDATION);
        k.clear(hx - 2, g + 1, hz - 2, hx + 2, deck + 8, hz + 2);
        k.box(hx - 2, 0, hz - 2, hx + 2, g - 1, hz + 2, Mat.FOUNDATION);
        k.clear(hx - 1, 1, hz - 1, hx + 1, g, hz + 1);
        k.ladder(hx - 1, hz + 1, 1, g, Meta.NORTH);
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                k.column(hx + dx, hz + dz, g + 1, deck, Mat.COLUMN);
            }
        }
        for (int y = g + 4; y < deck; y += 4) {
            k.beamX(hx - 1, hx + 1, y, hz - 2, Mat.BEAM);
            k.beamX(hx - 1, hx + 1, y, hz + 2, Mat.BEAM);
            k.beamZ(hx - 2, y, hz - 1, hz + 1, Mat.BEAM);
            k.beamZ(hx + 2, y, hz - 1, hz + 1, Mat.BEAM);
            if (k.chance(0.5)) {
                k.line(hx - 1, y + 1, hz - 2, hx + 1, y + 3, hz - 2, Mat.BRACE, 0);
            }
            if (k.chance(0.5)) {
                k.line(hx + 2, y + 1, hz - 1, hx + 2, y + 3, hz + 1, Mat.BRACE, 0);
            }
        }
        k.box(hx - 2, deck, hz - 2, hx + 2, deck, hz + 2, Mat.CATWALK);
        k.clear(hx - 1, deck, hz - 1, hx, deck, hz);
        k.railX(hx - 2, hx + 2, deck + 1, hz - 2, 0.3);
        k.railX(hx - 2, hx + 2, deck + 1, hz + 2, 0.3);
        int cy = deck + 4;
        k.box(hx - 1, deck + 1, hz, hx - 1, cy, hz, Mat.COLUMN, Meta.AXIS_Y);
        k.box(hx + 1, deck + 1, hz, hx + 1, cy - 1, hz, Mat.COLUMN, Meta.AXIS_Y);
        for (int a = 0; a < 24; a++) {
            double angle = Math.PI * 2 * a / 24;
            int wy = cy + (int) Math.round(3 * Math.sin(angle));
            int wz = hz + (int) Math.round(3 * Math.cos(angle));
            k.plan().set(hx, wy, wz, Mat.TRIM);
        }
        k.line(hx, cy - 2, hz, hx, cy + 2, hz, Mat.BEAM, Meta.AXIS_Y);
        k.line(hx, cy, hz - 2, hx, cy, hz + 2, Mat.BEAM, Meta.AXIS_Z);
        k.plan().set(hx - 1, cy, hz, Mat.BEAM, Meta.AXIS_X);
        k.plan().set(hx + 1, cy, hz, Mat.BEAM, Meta.AXIS_X);
        k.column(hx + 1, hz, deck + 1, cy - 1, Mat.COLUMN);
        k.ladder(hx - 3, hz - 2, g + 1, deck - 1, Meta.WEST);
        spots.add(new int[]{hx + 1, deck + 1, hz + 1, Meta.NORTH});
        int start = deck - 6;
        int x = hx - 3;
        int y = start;
        int gz = hz;
        int end = x - gallery;
        while (x > end && y > g + 2) {
            k.box(x, y, gz - 1, x, y, gz + 1, Mat.CATWALK);
            k.plan().set(x, y + 1, gz - 1, Mat.RAIL);
            k.plan().set(x, y + 1, gz + 1, Mat.RAIL);
            if ((hx - 3 - x) % 3 == 0) {
                k.column(x, gz - 1, y + 1, y + 3, Mat.COLUMN);
                k.column(x, gz + 1, y + 1, y + 3, Mat.COLUMN);
                k.beamZ(x, y + 3, gz - 1, gz + 1, Mat.BEAM);
            }
            if ((hx - 3 - x) % 4 == 2) {
                k.column(x, gz - 1, g + 1, y - 1, Mat.COLUMN);
                k.column(x, gz + 1, g + 1, y - 1, Mat.COLUMN);
            }
            x--;
            if ((hx - 3 - x) % 2 == 0) {
                y--;
            }
        }
        int bx = x;
        k.box(bx - 4, g, gz - 2, bx, g, gz + 2, Mat.FOUNDATION);
        k.underFill(bx - 4, gz - 2, bx, gz + 2, Mat.FOUNDATION);
        for (int dx = 0; dx <= 4; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                k.column(bx - dx, gz + dz, g + 1, y + 3, Mat.COLUMN);
            }
        }
        k.box(bx - 4, y - 1, gz - 2, bx, y + 3, gz + 2, Mat.WALL);
        k.clear(bx - 3, y - 1, gz - 1, bx - 1, y + 3, gz + 1);
        k.clear(bx, y, gz - 1, bx, y + 2, gz + 1);
        k.decay(bx - 4, y - 1, gz - 2, bx, y + 3, gz + 2, EnumSet.of(Mat.WALL), 0.15);
        int houseX = m;
        int houseZ = sz - m - houseL;
        Hall house = new Hall(houseX + 1, houseZ, houseW, houseL, 5, 4, Hall.Roof.GABLE, g);
        house.floor(k, Mat.FLOOR);
        house.clearInside(k);
        house.frame(k, false);
        house.cladding(k, 4, 0.0);
        house.gableWalls(k, 0.25);
        house.roof(k);
        house.endDoors(k, 3, 4, true);
        house.sideWindows(k, 3, 0.8);
        house.roofHoles(k, k.range(2, 3), 2, 3);
        int chimneyX = house.x1() - 2;
        int chimneyZ = house.z0 + 2;
        k.box(chimneyX, g + 1, chimneyZ, chimneyX + 1, house.top() + 4, chimneyZ + 1, Mat.STACK);
        spots.add(new int[]{house.x0 + 1, g + 1, house.z0 + 1, Meta.EAST});
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(3, 6), Mat.DEBRIS);
        k.scatter(0, 0, sx - 1, sz - 1, g + 1, k.range(2, 4), Mat.CARGO);
        return p;
    }
}
