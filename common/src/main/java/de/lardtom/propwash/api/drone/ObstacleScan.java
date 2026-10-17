package de.lardtom.propwash.api.drone;

import de.lardtom.propwash.api.flight.SensorDirection;

import java.util.Arrays;

public record ObstacleScan(double range, double[] distances) {
    public ObstacleScan {
        if (distances.length != SensorDirection.values().length) {
            throw new IllegalArgumentException("Erwartet " + SensorDirection.values().length + " Richtungen");
        }
        distances = distances.clone();
    }

    public double distance(SensorDirection direction) {
        return this.distances[direction.ordinal()];
    }

    public boolean clear(SensorDirection direction) {
        return this.distances[direction.ordinal()] >= this.range;
    }

    public double nearest() {
        return Arrays.stream(this.distances).min().orElse(this.range);
    }

    @Override
    public double[] distances() {
        return this.distances.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ObstacleScan scan && scan.range == this.range && Arrays.equals(scan.distances, this.distances);
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(this.range) + Arrays.hashCode(this.distances);
    }

    @Override
    public String toString() {
        return "ObstacleScan[range=" + this.range + ", distances=" + Arrays.toString(this.distances) + "]";
    }
}
