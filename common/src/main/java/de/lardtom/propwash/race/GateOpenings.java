package de.lardtom.propwash.race;

@FunctionalInterface
public interface GateOpenings {
    GateOpenings NONE = gateIndex -> false;

    boolean legs(int gateIndex);
}
