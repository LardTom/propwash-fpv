package de.lardtom.propwash.sim;

public record BreakReport(int count, double approach, double lostFracture, double lostShards, double plugKg, double areaM2, double share,
                          double rateX, double rateY, double rateZ, int contacts, int struck, boolean crash) {
    public double lost() {
        return this.lostFracture + this.lostShards;
    }

    public double speedAfter() {
        return Math.max(0.0, this.approach - this.lost());
    }

    public double rateChange() {
        return Math.sqrt(this.rateX * this.rateX + this.rateY * this.rateY + this.rateZ * this.rateZ);
    }

    public int struckCount() {
        return Integer.bitCount(this.struck);
    }
}
