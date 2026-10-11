package de.lardtom.propwash.bando.plan;

import java.util.Arrays;

public final class BandoPlan {
    private final BandoVariant variant;
    private final long seed;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int ground;
    private final byte[] mats;
    private final byte[] metas;
    private final byte[] fill;
    private int chestX = -1;
    private int chestY = -1;
    private int chestZ = -1;
    private int chestFacing;

    public BandoPlan(BandoVariant variant, long seed, int sizeX, int sizeY, int sizeZ, int ground) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || ground < 0 || ground >= sizeY) {
            throw new IllegalArgumentException("Ungültige Plangröße " + sizeX + "×" + sizeY + "×" + sizeZ + " Boden " + ground);
        }
        this.variant = variant;
        this.seed = seed;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.ground = ground;
        this.mats = new byte[sizeX * sizeY * sizeZ];
        this.metas = new byte[sizeX * sizeY * sizeZ];
        this.fill = new byte[sizeX * sizeZ];
    }

    public BandoVariant variant() {
        return this.variant;
    }

    public long seed() {
        return this.seed;
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public int ground() {
        return this.ground;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.sizeX && y < this.sizeY && z < this.sizeZ;
    }

    private int index(int x, int y, int z) {
        return (y * this.sizeZ + z) * this.sizeX + x;
    }

    public Mat mat(int x, int y, int z) {
        return this.inside(x, y, z) ? Mat.byId(this.mats[this.index(x, y, z)]) : Mat.EMPTY;
    }

    public int meta(int x, int y, int z) {
        return this.inside(x, y, z) ? this.metas[this.index(x, y, z)] : 0;
    }

    public boolean blocksFlight(int x, int y, int z) {
        return this.mat(x, y, z).blocksFlight();
    }

    public void set(int x, int y, int z, Mat mat) {
        this.set(x, y, z, mat, 0);
    }

    public void set(int x, int y, int z, Mat mat, int meta) {
        if (!this.inside(x, y, z)) {
            return;
        }
        int i = this.index(x, y, z);
        this.mats[i] = (byte) mat.ordinal();
        this.metas[i] = (byte) meta;
        if (mat != Mat.CHEST && this.chestX == x && this.chestY == y && this.chestZ == z) {
            this.chestX = -1;
            this.chestY = -1;
            this.chestZ = -1;
        }
    }

    public void setIfFree(int x, int y, int z, Mat mat, int meta) {
        Mat current = this.mat(x, y, z);
        if (current == Mat.EMPTY || current == Mat.AIR) {
            this.set(x, y, z, mat, meta);
        }
    }

    public Mat fill(int x, int z) {
        return x >= 0 && z >= 0 && x < this.sizeX && z < this.sizeZ ? Mat.byId(this.fill[z * this.sizeX + x]) : Mat.EMPTY;
    }

    public void setFill(int x, int z, Mat mat) {
        if (x >= 0 && z >= 0 && x < this.sizeX && z < this.sizeZ) {
            this.fill[z * this.sizeX + x] = (byte) mat.ordinal();
        }
    }

    public boolean hasChest() {
        return this.chestX >= 0 && this.mat(this.chestX, this.chestY, this.chestZ) == Mat.CHEST;
    }

    public int chestX() {
        return this.chestX;
    }

    public int chestY() {
        return this.chestY;
    }

    public int chestZ() {
        return this.chestZ;
    }

    public int chestFacing() {
        return this.chestFacing;
    }

    public void placeChest(int x, int y, int z, int facing) {
        if (this.hasChest()) {
            this.set(this.chestX, this.chestY, this.chestZ, Mat.AIR);
        }
        this.set(x, y, z, Mat.CHEST, facing & 3);
        this.chestX = x;
        this.chestY = y;
        this.chestZ = z;
        this.chestFacing = facing & 3;
    }

    public int count(Mat mat) {
        int n = 0;
        byte id = (byte) mat.ordinal();
        for (byte b : this.mats) {
            if (b == id) {
                n++;
            }
        }
        return n;
    }

    public int placedBlocks() {
        int n = 0;
        for (byte b : this.mats) {
            Mat mat = Mat.byId(b);
            if (mat != Mat.EMPTY && mat != Mat.AIR) {
                n++;
            }
        }
        return n;
    }

    public long fingerprint() {
        long h = 1125899906842597L;
        for (int i = 0; i < this.mats.length; i++) {
            h = 31 * h + this.mats[i];
            h = 31 * h + this.metas[i];
        }
        return 31 * h + Arrays.hashCode(this.fill);
    }
}
