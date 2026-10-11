package de.lardtom.propwash.bando.plan;

import java.util.EnumSet;
import java.util.Set;

final class Hall {
    enum Roof {
        FLAT,
        PITCHED,
        SAWTOOTH,
        GABLE
    }

    static final Set<Mat> ROOFING = EnumSet.of(Mat.ROOF, Mat.ROOF_STEP);
    static final Set<Mat> WALLS = EnumSet.of(Mat.WALL, Mat.WALL_ALT, Mat.WINDOW);

    final int x0;
    final int z0;
    final int w;
    final int l;
    final int h;
    final int bay;
    final Roof roof;
    final int y;

    Hall(int x0, int z0, int w, int l, int h, int bay, Roof roof, int y) {
        this.x0 = x0;
        this.z0 = z0;
        this.w = w;
        this.l = l;
        this.h = h;
        this.bay = bay;
        this.roof = roof;
        this.y = y;
    }

    int x1() {
        return this.x0 + this.w - 1;
    }

    int z1() {
        return this.z0 + this.l - 1;
    }

    int eave() {
        return this.y + this.h;
    }

    int top() {
        return switch (this.roof) {
            case FLAT -> this.eave() + 1;
            case PITCHED -> this.eave() + 1 + (this.w - 1) / 2 / 3;
            case SAWTOOTH -> this.eave() + 4;
            case GABLE -> this.eave() + (this.w + 1) / 2;
        };
    }

    boolean bayLine(int z) {
        return (z - this.z0) % this.bay == 0 || z == this.z1();
    }

    void floor(Kit k, Mat floor) {
        k.box(this.x0, this.y, this.z0, this.x1(), this.y, this.z1(), floor);
        k.underFill(this.x0, this.z0, this.x1(), this.z1(), Mat.FOUNDATION);
    }

    void clearInside(Kit k) {
        k.clear(this.x0, this.y + 1, this.z0, this.x1(), this.top(), this.z1());
    }

    void frame(Kit k, boolean middleRow) {
        int eave = this.eave();
        for (int z = this.z0; z <= this.z1(); z++) {
            if (!this.bayLine(z)) {
                continue;
            }
            k.column(this.x0, z, this.y + 1, eave, Mat.COLUMN);
            k.column(this.x1(), z, this.y + 1, eave, Mat.COLUMN);
            if (middleRow && z != this.z0 && z != this.z1()) {
                k.column(this.x0 + this.w / 2, z, this.y + 1, eave, Mat.COLUMN);
            }
            k.beamX(this.x0 + 1, this.x1() - 1, eave, z, Mat.BEAM);
        }
        k.beamZ(this.x0, eave, this.z0, this.z1(), Mat.BEAM);
        k.beamZ(this.x1(), eave, this.z0, this.z1(), Mat.BEAM);
        for (int x = this.x0 + 1; x < this.x1(); x++) {
            if ((x - this.x0) % this.bay == 0) {
                k.column(x, this.z0, this.y + 1, eave, Mat.COLUMN);
                k.column(x, this.z1(), this.y + 1, eave, Mat.COLUMN);
            }
        }
    }

    void cladding(Kit k, int band, double panels) {
        int eave = this.eave();
        for (int z = this.z0; z <= this.z1(); z++) {
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? this.x0 : this.x1();
                k.boxIfFree(x, this.y + 1, z, x, this.y + band, z, Mat.WALL, 0);
            }
        }
        for (int x = this.x0; x <= this.x1(); x++) {
            for (int side = 0; side < 2; side++) {
                int z = side == 0 ? this.z0 : this.z1();
                k.boxIfFree(x, this.y + 1, z, x, this.y + band, z, Mat.WALL, 0);
            }
        }
        for (int z = this.z0; z < this.z1(); z += this.bay) {
            for (int side = 0; side < 2; side++) {
                if (!k.chance(panels)) {
                    continue;
                }
                int x = side == 0 ? this.x0 : this.x1();
                k.boxIfFree(x, this.y + band + 1, z + 1, x, eave - 1, Math.min(z + this.bay - 1, this.z1() - 1), Mat.WALL_ALT, 0);
            }
        }
    }

    void endDoors(Kit k, int width, int height, boolean both) {
        int cx = this.x0 + this.w / 2;
        int half = width / 2;
        k.clear(cx - half, this.y + 1, this.z0, cx - half + width - 1, this.y + height, this.z0);
        if (both) {
            int shift = k.range(-1, 1);
            k.clear(cx - half + shift, this.y + 1, this.z1(), cx - half + shift + width - 1, this.y + height, this.z1());
        }
    }

    void sideWindows(Kit k, int band, double chance) {
        for (int z = this.z0; z < this.z1(); z += this.bay) {
            for (int side = 0; side < 2; side++) {
                if (!k.chance(chance)) {
                    continue;
                }
                int x = side == 0 ? this.x0 : this.x1();
                int length = Math.max(2, Math.min(this.bay - 2, k.range(2, 4)));
                int start = z + 1 + k.range(0, Math.max(0, this.bay - 1 - length));
                int bottom = this.y + Math.max(1, band - 1);
                k.clear(x, bottom, start, x, bottom + 1, start + length - 1);
            }
        }
    }

    void roof(Kit k) {
        int eave = this.eave();
        switch (this.roof) {
            case FLAT -> {
                k.box(this.x0, eave + 1, this.z0, this.x1(), eave + 1, this.z1(), Mat.ROOF);
                for (int x = this.x0 + 3; x < this.x1(); x += 4) {
                    k.beamZ(x, eave, this.z0, this.z1(), Mat.BEAM);
                }
            }
            case PITCHED -> {
                for (int x = this.x0; x <= this.x1(); x++) {
                    int d = Math.min(x - this.x0, this.x1() - x);
                    int ry = eave + 1 + d / 3;
                    k.box(x, ry, this.z0, x, ry, this.z1(), Mat.ROOF);
                    if (d / 3 > 0 && d % 3 == 0) {
                        k.box(x, ry - 1, this.z0, x, ry - 1, this.z1(), Mat.TRIM);
                    }
                }
                int ridge = this.x0 + this.w / 2;
                for (int z = this.z0; z <= this.z1(); z++) {
                    if (this.bayLine(z)) {
                        int rise = (this.w - 1) / 2 / 3;
                        k.column(ridge, z, eave + 1, eave + rise, Mat.COLUMN);
                    }
                }
            }
            case SAWTOOTH -> {
                for (int z = this.z0; z <= this.z1(); z++) {
                    int phase = (z - this.z0) % this.bay;
                    int ry = eave + 1 + Math.min(3, phase * 3 / Math.max(1, this.bay - 1));
                    k.box(this.x0, ry, z, this.x1(), ry, z, Mat.ROOF_STEP, Meta.SOUTH);
                    if ((phase == this.bay - 1 || z == this.z1()) && ry > eave + 1) {
                        for (int x = this.x0; x <= this.x1(); x += 4) {
                            k.column(x, z, eave + 1, ry - 1, Mat.COLUMN);
                        }
                        k.column(this.x1(), z, eave + 1, ry - 1, Mat.COLUMN);
                    }
                }
            }
            case GABLE -> {
                int half = (this.w + 1) / 2;
                for (int i = 0; i < half; i++) {
                    int ry = eave + i;
                    int left = this.x0 - 1 + i;
                    int right = this.x1() + 1 - i;
                    if (left < right) {
                        k.box(left, ry, this.z0 - 1, left, ry, this.z1() + 1, Mat.ROOF_STEP, Meta.EAST);
                        k.box(right, ry, this.z0 - 1, right, ry, this.z1() + 1, Mat.ROOF_STEP, Meta.WEST);
                    } else if (left == right) {
                        k.box(left, ry, this.z0 - 1, left, ry, this.z1() + 1, Mat.ROOF);
                    }
                }
                int ridge = this.x0 + (this.w - 1) / 2;
                k.beamZ(ridge, eave + half - 1 - ((this.w & 1) == 0 ? 0 : 1), this.z0, this.z1(), Mat.BEAM);
            }
        }
    }

    void gableWalls(Kit k, double decay) {
        if (this.roof != Roof.GABLE) {
            return;
        }
        int half = (this.w + 1) / 2;
        for (int i = 0; i < half; i++) {
            int ry = this.eave() + i;
            for (int x = this.x0 + i; x <= this.x1() - i; x++) {
                for (int side = 0; side < 2; side++) {
                    int z = side == 0 ? this.z0 : this.z1();
                    if (!k.chance(decay)) {
                        k.plan().setIfFree(x, ry, z, Mat.WALL, 0);
                    }
                }
            }
        }
    }

    void roofHoles(Kit k, int count, int min, int max) {
        k.patches(this.x0, this.eave() + 1, this.z0, this.x1(), this.top(), this.z1(), ROOFING, 0, min, max);
        for (int i = 0; i < count; i++) {
            int w = k.range(min, max);
            int d = k.range(min, max);
            int x = k.range(this.x0, Math.max(this.x0, this.x1() - w + 1));
            int z = k.range(this.z0, Math.max(this.z0, this.z1() - d + 1));
            for (int xx = x; xx < x + w; xx++) {
                for (int zz = z; zz < z + d; zz++) {
                    for (int yy = this.eave() + 1; yy <= this.top(); yy++) {
                        if (ROOFING.contains(k.plan().mat(xx, yy, zz)) || k.plan().mat(xx, yy, zz) == Mat.TRIM) {
                            k.plan().set(xx, yy, zz, Mat.AIR);
                        }
                    }
                }
            }
        }
    }

    void stripBays(Kit k, int count) {
        int bays = Math.max(1, (this.l - 1) / this.bay);
        for (int i = 0; i < count; i++) {
            int b = k.range(0, bays - 1);
            int za = this.z0 + b * this.bay + 1;
            int zb = Math.min(this.z1() - 1, this.z0 + (b + 1) * this.bay - 1);
            for (int z = za; z <= zb; z++) {
                for (int x = this.x0 - 1; x <= this.x1() + 1; x++) {
                    for (int yy = this.eave() + 1; yy <= this.top(); yy++) {
                        Mat m = k.plan().mat(x, yy, z);
                        if (ROOFING.contains(m) || m == Mat.TRIM) {
                            k.plan().set(x, yy, z, Mat.AIR);
                        }
                    }
                }
            }
        }
    }

    void catwalk(Kit k, boolean west, int level, int width) {
        int x = west ? this.x0 + 1 : this.x1() - width;
        int railX = west ? this.x0 + width + 1 : this.x1() - width - 1;
        k.box(x, level, this.z0 + 1, x + width - 1, level, this.z1() - 1, Mat.CATWALK);
        k.railZ(railX, level + 1, this.z0 + 1, this.z1() - 1, 0.25);
        for (int z = this.z0 + 1; z < this.z1(); z++) {
            if (k.plan().mat(railX, level + 1, z) == Mat.RAIL) {
                k.plan().setIfFree(railX, level, z, Mat.CATWALK, 0);
            }
        }
        int ladderX = west ? this.x0 + 1 : this.x1() - 1;
        int facing = west ? Meta.EAST : Meta.WEST;
        k.ladder(ladderX, this.z0 + 2, this.y + 1, level - 1, facing);
        k.clear(ladderX, level, this.z0 + 2, ladderX, level, this.z0 + 2);
    }

    void crane(Kit k) {
        int eave = this.eave();
        k.beamZ(this.x0 + 1, eave - 1, this.z0 + 1, this.z1() - 1, Mat.BEAM);
        k.beamZ(this.x1() - 1, eave - 1, this.z0 + 1, this.z1() - 1, Mat.BEAM);
        int z = this.z0 + k.range(2, this.l - 3);
        k.beamX(this.x0 + 2, this.x1() - 2, eave - 1, z, Mat.BEAM);
        int hx = this.x0 + k.range(3, this.w - 4);
        int length = k.range(1, Math.max(1, this.h - 5));
        k.chain(hx, z, eave - 1 - length, eave - 2);
        k.plan().set(hx, eave - 2 - length, z, Mat.CARGO);
    }

    void debris(Kit k, int count) {
        k.scatter(this.x0 + 1, this.z0 + 1, this.x1() - 1, this.z1() - 1, this.y + 2, count, Mat.DEBRIS_LOW);
    }

    void collapse(Kit k, int fromZ, int toZ, double columnLoss) {
        int a = Math.max(this.z0, Math.min(fromZ, toZ));
        int b = Math.min(this.z1(), Math.max(fromZ, toZ));
        for (int z = a; z <= b; z++) {
            for (int x = this.x0 - 1; x <= this.x1() + 1; x++) {
                for (int yy = this.eave() - 1; yy <= this.top() + 1; yy++) {
                    Mat m = k.plan().mat(x, yy, z);
                    if (ROOFING.contains(m) || m == Mat.TRIM || (m == Mat.BEAM && yy >= this.eave() && k.chance(0.7))) {
                        k.plan().set(x, yy, z, Mat.AIR);
                    }
                }
            }
            if (this.bayLine(z)) {
                for (int x = this.x0; x <= this.x1(); x++) {
                    if (k.plan().mat(x, this.y + 1, z) == Mat.COLUMN && k.chance(columnLoss)) {
                        int keep = k.range(1, Math.max(1, this.h - 2));
                        k.clear(x, this.y + 1 + keep, z, x, this.eave(), z);
                    }
                }
            }
        }
    }
}
