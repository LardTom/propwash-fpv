package de.lardtom.propwash.sim;

@FunctionalInterface
public interface FcListener {
    void onFcSample(DroneSim sim, FcSample sample);
}
