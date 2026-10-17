package de.lardtom.propwash.interact;

import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SnagField;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Arrays;

public final class SnagSampler {
    public static final double CENTER_HALF_FACTOR = 0.35;
    public static final double CENTER_HALF_MIN = 0.03;
    public static final double DISC_HALF_THICKNESS = 0.01;
    public static final double MAX_JUMP = 4.0;
    public static final int MAX_CELLS = 64;
    public static final int MAX_BOXES = 96;

    public interface World {
        void boxes(int x, int y, int z, SnagSampler sampler);
    }

    private static final int POINTS = SnagField.POINTS;

    private final double[] lastX = new double[POINTS];
    private final double[] lastY = new double[POINTS];
    private final double[] lastZ = new double[POINTS];
    private final double[] nowX = new double[POINTS];
    private final double[] nowY = new double[POINTS];
    private final double[] nowZ = new double[POINTS];
    private final double[] extX = new double[POINTS];
    private final double[] extY = new double[POINTS];
    private final double[] extZ = new double[POINTS];
    private final double[][] exposure = new double[POINTS][SnagMaterial.COUNT];
    private final SnagMaterial[] boxMaterial = new SnagMaterial[MAX_BOXES];
    private final double[] boxes = new double[MAX_BOXES * 6];
    private final double[] spanLo = new double[MAX_BOXES];
    private final double[] spanHi = new double[MAX_BOXES];
    private final double[] range = new double[2];
    private final Vector3d position = new Vector3d();
    private final Vector3d scratch = new Vector3d();
    private final Quaterniond attitude = new Quaterniond();
    private int boxCount;
    private boolean primed;
    private boolean touching;

    public void reset() {
        this.primed = false;
        this.touching = false;
        for (double[] row : this.exposure) {
            Arrays.fill(row, 0.0);
        }
    }

    public boolean touching() {
        return this.touching;
    }

    public double exposure(int point, SnagMaterial material) {
        return this.exposure[point][material.ordinal()];
    }

    public double exposure(SnagMaterial material) {
        double sum = 0.0;
        for (int i = 0; i < POINTS; i++) {
            sum += this.exposure[i][material.ordinal()];
        }
        return sum / POINTS;
    }

    public double[][] exposures() {
        return this.exposure;
    }

    public double pointX(int point) {
        return this.nowX[point];
    }

    public double pointY(int point) {
        return this.nowY[point];
    }

    public double pointZ(int point) {
        return this.nowZ[point];
    }

    public boolean sample(DroneSim sim, World world, SnagField out) {
        AirframeSpec p = sim.params();
        sim.getPosition(this.position);
        sim.getAttitude(this.attitude);
        this.scratch.set(0.0, 1.0, 0.0);
        this.attitude.transform(this.scratch);
        double ux = this.scratch.x;
        double uy = this.scratch.y;
        double uz = this.scratch.z;
        double r = p.propRadius;
        double discX = r * Math.sqrt(Math.max(0.0, 1.0 - ux * ux)) + DISC_HALF_THICKNESS;
        double discY = r * Math.sqrt(Math.max(0.0, 1.0 - uy * uy)) + DISC_HALF_THICKNESS;
        double discZ = r * Math.sqrt(Math.max(0.0, 1.0 - uz * uz)) + DISC_HALF_THICKNESS;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.scratch.set(p.motorX(i), p.motorY(i), p.motorZ(i));
            this.attitude.transform(this.scratch);
            this.nowX[i] = this.position.x + this.scratch.x;
            this.nowY[i] = this.position.y + this.scratch.y;
            this.nowZ[i] = this.position.z + this.scratch.z;
            this.extX[i] = discX;
            this.extY[i] = discY;
            this.extZ[i] = discZ;
        }
        double half = Math.max(CENTER_HALF_MIN, CENTER_HALF_FACTOR * p.motorOffset);
        int c = SnagField.CENTER;
        this.nowX[c] = this.position.x;
        this.nowY[c] = this.position.y;
        this.nowZ[c] = this.position.z;
        this.extX[c] = half;
        this.extY[c] = half;
        this.extZ[c] = half;
        if (!this.primed || jumped()) {
            System.arraycopy(this.nowX, 0, this.lastX, 0, POINTS);
            System.arraycopy(this.nowY, 0, this.lastY, 0, POINTS);
            System.arraycopy(this.nowZ, 0, this.lastZ, 0, POINTS);
            this.primed = true;
        }
        for (double[] row : this.exposure) {
            Arrays.fill(row, 0.0);
        }
        this.boxCount = 0;
        this.collect(world);
        boolean any = false;
        for (int i = 0; i < POINTS && this.boxCount > 0; i++) {
            any |= this.expose(i);
        }
        System.arraycopy(this.nowX, 0, this.lastX, 0, POINTS);
        System.arraycopy(this.nowY, 0, this.lastY, 0, POINTS);
        System.arraycopy(this.nowZ, 0, this.lastZ, 0, POINTS);
        SnagMath.fill(this.exposure, out);
        this.touching = any;
        return any;
    }

    public void box(SnagMaterial material, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (this.boxCount >= MAX_BOXES || material == null) {
            return;
        }
        int k = this.boxCount++;
        this.boxMaterial[k] = material;
        int o = k * 6;
        this.boxes[o] = minX;
        this.boxes[o + 1] = minY;
        this.boxes[o + 2] = minZ;
        this.boxes[o + 3] = maxX;
        this.boxes[o + 4] = maxY;
        this.boxes[o + 5] = maxZ;
    }

    private boolean jumped() {
        double dx = this.nowX[SnagField.CENTER] - this.lastX[SnagField.CENTER];
        double dy = this.nowY[SnagField.CENTER] - this.lastY[SnagField.CENTER];
        double dz = this.nowZ[SnagField.CENTER] - this.lastZ[SnagField.CENTER];
        return !(dx * dx + dy * dy + dz * dz <= MAX_JUMP * MAX_JUMP);
    }

    private void collect(World world) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < POINTS; i++) {
            minX = Math.min(minX, Math.min(this.lastX[i], this.nowX[i]) - this.extX[i]);
            minY = Math.min(minY, Math.min(this.lastY[i], this.nowY[i]) - this.extY[i]);
            minZ = Math.min(minZ, Math.min(this.lastZ[i], this.nowZ[i]) - this.extZ[i]);
            maxX = Math.max(maxX, Math.max(this.lastX[i], this.nowX[i]) + this.extX[i]);
            maxY = Math.max(maxY, Math.max(this.lastY[i], this.nowY[i]) + this.extY[i]);
            maxZ = Math.max(maxZ, Math.max(this.lastZ[i], this.nowZ[i]) + this.extZ[i]);
        }
        if (!Double.isFinite(minX + minY + minZ + maxX + maxY + maxZ)) {
            return;
        }
        int x0 = (int) Math.floor(minX);
        int y0 = (int) Math.floor(minY);
        int z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.floor(maxX);
        int y1 = (int) Math.floor(maxY);
        int z1 = (int) Math.floor(maxZ);
        long cells = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (cells <= 0 || cells > MAX_CELLS) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    world.boxes(x, y, z, this);
                }
            }
        }
    }

    private boolean expose(int i) {
        double ex = this.extX[i];
        double ey = this.extY[i];
        double ez = this.extZ[i];
        double px = this.nowX[i];
        double py = this.nowY[i];
        double pz = this.nowZ[i];
        boolean any = false;
        double[] row = this.exposure[i];
        for (SnagMaterial material : SnagMaterial.VALUES) {
            int count = 0;
            boolean inside = false;
            for (int k = 0; k < this.boxCount && !inside; k++) {
                if (this.boxMaterial[k] != material) {
                    continue;
                }
                int o = k * 6;
                double minX = this.boxes[o] - ex;
                double minY = this.boxes[o + 1] - ey;
                double minZ = this.boxes[o + 2] - ez;
                double maxX = this.boxes[o + 3] + ex;
                double maxY = this.boxes[o + 4] + ey;
                double maxZ = this.boxes[o + 5] + ez;
                if (px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ) {
                    inside = true;
                } else if (SnagMath.clip(this.lastX[i], this.lastY[i], this.lastZ[i], px, py, pz, minX, minY, minZ, maxX, maxY, maxZ, this.range)) {
                    this.spanLo[count] = this.range[0];
                    this.spanHi[count] = this.range[1];
                    count++;
                }
            }
            double e = inside ? 1.0 : SnagMath.unionLength(this.spanLo, this.spanHi, count);
            if (e > 0.0) {
                row[material.ordinal()] = e;
                any = true;
            }
        }
        return any;
    }
}
