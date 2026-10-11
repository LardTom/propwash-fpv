package de.lardtom.propwash.drone.bind;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class BindSearch {
    private BindSearch() {
    }

    public static <T> Optional<T> pick(Collection<T> drones, Function<T, BindAccess> access, ToDoubleFunction<T> distanceSq) {
        T best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (T drone : drones) {
            BindAccess candidate = access.apply(drone);
            if (candidate == null || !candidate.matches()) {
                continue;
            }
            double distance = distanceSq.applyAsDouble(drone);
            if (!Double.isFinite(distance)) {
                continue;
            }
            if (best == null || distance < bestDistance) {
                best = drone;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
