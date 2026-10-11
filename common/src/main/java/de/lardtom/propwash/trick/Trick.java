package de.lardtom.propwash.trick;

public record Trick(TrickType type, double time, double magnitude, double minClearance, double start) {
    public Trick(TrickType type, double time, double magnitude, double minClearance) {
        this(type, time, magnitude, minClearance, time);
    }
}
