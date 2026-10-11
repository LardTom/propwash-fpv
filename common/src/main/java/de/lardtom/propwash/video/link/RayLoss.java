package de.lardtom.propwash.video.link;

import org.jspecify.annotations.Nullable;

public final class RayLoss {
    private double linearDb;
    private double foliageMeters;
    private int cells;
    private @Nullable RfMaterial run;

    public void reset() {
        this.linearDb = 0.0;
        this.foliageMeters = 0.0;
        this.cells = 0;
        this.run = null;
    }

    void add(RfMaterial material, double meters) {
        if (material != this.run) {
            this.linearDb += material.dbPerCrossing();
            this.run = material;
        }
        if (material.foliage()) {
            this.foliageMeters += meters * material.foliageDensity();
        } else {
            this.linearDb += meters * material.dbPerMeter();
        }
    }

    void breakRun() {
        this.run = null;
    }

    void countCell() {
        this.cells++;
    }

    public double linearDb() {
        return this.linearDb;
    }

    public double foliageMeters() {
        return this.foliageMeters;
    }

    public int cells() {
        return this.cells;
    }

    public double totalDb(double capDb) {
        double total = this.linearDb + FoliageLoss.db(this.foliageMeters);
        if (!(total > 0.0)) {
            return 0.0;
        }
        return Math.min(total, capDb);
    }

    public boolean reached(double capDb) {
        return this.linearDb >= capDb;
    }
}
