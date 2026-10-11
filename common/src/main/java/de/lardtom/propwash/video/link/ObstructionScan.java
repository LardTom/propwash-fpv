package de.lardtom.propwash.video.link;

public final class ObstructionScan {
    public static final double MAX_LOSS_DB = 60.0;
    public static final int SIDES = 4;
    public static final int MAX_RAYS = 1 + SIDES * FresnelCut.SAMPLES;
    public static final int REPRESENTATIVE = 1 + SIDES;
    private static final int REPRESENTATIVE_SAMPLE = 2;
    private static final double[] T = {0.0, 0.02, 0.06, 0.13, 0.22, 0.34, 0.5, 0.66, 0.78, 0.87, 0.94, 0.98, 1.0};
    private static final double[] SPINDLE = new double[T.length];

    static {
        for (int k = 0; k < T.length; k++) {
            SPINDLE[k] = Math.sqrt(T[k] * (1.0 - T[k]));
        }
    }

    private final double[] points = new double[T.length * 3];
    private final RayLoss ray = new RayLoss();
    private final double[][] sideLoss = new double[SIDES][FresnelCut.SAMPLES];
    private final double[][] sideAmplitude = new double[SIDES][FresnelCut.SAMPLES];
    private final int[] sideCount = new int[SIDES];
    private double centerLoss;
    private double ax;
    private double ay;
    private double az;
    private double bx;
    private double by;
    private double bz;
    private double rx;
    private double ry;
    private double rz;
    private double ux;
    private double uy;
    private double uz;
    private double scale;
    private boolean valid;
    private int side = SIDES;
    private int index;
    private boolean complete = true;
    private double lossDb;
    private long raysTraced;
    private int roundRays;
    private int roundCells;

    public void start(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        this.ax = fromX;
        this.ay = fromY;
        this.az = fromZ;
        this.bx = toX;
        this.by = toY;
        this.bz = toZ;
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.valid = length > 1.0E-3 && Double.isFinite(length);
        for (int s = 0; s < SIDES; s++) {
            this.sideCount[s] = 0;
        }
        this.centerLoss = 0.0;
        this.side = -1;
        this.index = 0;
        this.complete = false;
        this.roundRays = 0;
        this.roundCells = 0;
        if (!this.valid) {
            return;
        }
        double fx = dx / length;
        double fy = dy / length;
        double fz = dz / length;
        double px;
        double py;
        double pz;
        if (Math.abs(fy) < 0.95) {
            px = -fz;
            py = 0.0;
            pz = fx;
        } else {
            px = 0.0;
            py = fz;
            pz = -fy;
        }
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        this.rx = px / pl;
        this.ry = py / pl;
        this.rz = pz / pl;
        this.ux = this.ry * fz - this.rz * fy;
        this.uy = this.rz * fx - this.rx * fz;
        this.uz = this.rx * fy - this.ry * fx;
        this.scale = Math.sqrt(RadioBand.WAVELENGTH_M * length) / Math.sqrt(2.0);
    }

    public void clear() {
        this.complete = true;
        this.side = SIDES;
        this.lossDb = 0.0;
        this.centerLoss = 0.0;
        for (int s = 0; s < SIDES; s++) {
            this.sideCount[s] = 0;
        }
    }

    public boolean advance(ObstructionTracer.World world, int cellBudget) {
        if (this.complete) {
            return true;
        }
        if (!this.valid) {
            this.finish();
            return true;
        }
        int spent = 0;
        while (!this.complete && spent < cellBudget) {
            spent += this.traceNext(world);
        }
        return this.complete;
    }

    public double evaluate(ObstructionTracer.World world) {
        this.advance(world, Integer.MAX_VALUE);
        return this.lossDb;
    }

    public boolean complete() {
        return this.complete;
    }

    public double lossDb() {
        return this.lossDb;
    }

    public long raysTraced() {
        return this.raysTraced;
    }

    public int roundRays() {
        return this.roundRays;
    }

    public int roundCells() {
        return this.roundCells;
    }

    public double centerLossDb() {
        return this.centerLoss;
    }

    public int sideCount(int side) {
        return this.sideCount[side];
    }

    public double sideLossDb(int side, int sample) {
        return this.sideLoss[side][sample];
    }

    public int representativeLosses(double[] out) {
        out[0] = this.centerLoss;
        int n = 1;
        for (int s = 0; s < SIDES; s++) {
            int count = this.sideCount[s];
            if (count > 0) {
                out[n++] = this.sideLoss[s][Math.min(REPRESENTATIVE_SAMPLE, count - 1)];
            }
        }
        return n;
    }

    public void samplePath(int side, double nu, double[] out) {
        double ex = side < 2 ? this.rx : this.ux;
        double ey = side < 2 ? this.ry : this.uy;
        double ez = side < 2 ? this.rz : this.uz;
        double signed = side % 2 == 0 ? nu : -nu;
        for (int k = 0; k < T.length; k++) {
            double t = T[k];
            double offset = signed * this.scale * SPINDLE[k];
            out[3 * k] = this.ax + t * (this.bx - this.ax) + ex * offset;
            out[3 * k + 1] = this.ay + t * (this.by - this.ay) + ey * offset;
            out[3 * k + 2] = this.az + t * (this.bz - this.az) + ez * offset;
        }
    }

    public static int pathPoints() {
        return T.length;
    }

    private int traceNext(ObstructionTracer.World world) {
        if (this.side < 0) {
            this.centerLoss = this.traceSample(world, 0, 0.0);
            this.side = 0;
            this.index = 0;
            return Math.max(1, this.ray.cells());
        }
        double nu = FresnelCut.radius(this.index);
        double loss = this.traceSample(world, this.side, nu);
        int cells = Math.max(1, this.ray.cells());
        int s = this.side;
        this.sideLoss[s][this.index] = loss;
        this.index++;
        this.sideCount[s] = this.index;
        if (this.index >= FresnelCut.SAMPLES || this.stops(s, this.index)) {
            this.side++;
            this.index = 0;
            if (this.side >= SIDES) {
                this.finish();
            }
        }
        return cells;
    }

    private boolean stops(int s, int count) {
        if (count < 2 || FresnelCut.radius(count - 1) < FresnelCut.STOP_FROM) {
            return false;
        }
        return this.sideLoss[s][count - 1] <= 0.0 && this.sideLoss[s][count - 2] <= 0.0;
    }

    private double traceSample(ObstructionTracer.World world, int s, double nu) {
        this.samplePath(s, nu, this.points);
        this.ray.reset();
        ObstructionTracer.trace(this.points, T.length, world, this.ray, MAX_LOSS_DB);
        this.raysTraced++;
        this.roundRays++;
        this.roundCells += this.ray.cells();
        return this.ray.totalDb(MAX_LOSS_DB);
    }

    private void finish() {
        this.complete = true;
        this.side = SIDES;
        if (!this.valid) {
            this.lossDb = 0.0;
            return;
        }
        boolean clear = this.centerLoss <= 0.0;
        for (int s = 0; s < SIDES && clear; s++) {
            for (int i = 0; i < this.sideCount[s]; i++) {
                if (this.sideLoss[s][i] > 0.0) {
                    clear = false;
                    break;
                }
            }
        }
        if (clear) {
            this.lossDb = 0.0;
            return;
        }
        for (int s = 0; s < SIDES; s++) {
            for (int i = 0; i < this.sideCount[s]; i++) {
                this.sideAmplitude[s][i] = FresnelCut.amplitude(this.sideLoss[s][i]);
            }
        }
        double center = FresnelCut.amplitude(this.centerLoss);
        double cutX = FresnelCut.power(center, this.sideAmplitude[0], this.sideCount[0], this.sideAmplitude[1], this.sideCount[1]);
        double cutY = FresnelCut.power(center, this.sideAmplitude[2], this.sideCount[2], this.sideAmplitude[3], this.sideCount[3]);
        double power = FresnelCut.combine(cutX, cutY, center * center);
        this.lossDb = Math.clamp(FresnelCut.lossDb(power), 0.0, MAX_LOSS_DB);
    }
}
