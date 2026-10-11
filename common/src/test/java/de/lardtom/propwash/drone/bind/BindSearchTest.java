package de.lardtom.propwash.drone.bind;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindSearchTest {
    private record Drone(String name, BindAccess access, double distanceSq) {
    }

    private static Optional<String> pick(Drone... drones) {
        return BindSearch.pick(List.of(drones), Drone::access, Drone::distanceSq).map(Drone::name);
    }

    @Test
    void nearestOwnDroneWins() {
        assertEquals(Optional.of("near"), pick(new Drone("far", BindAccess.PILOT, 900.0), new Drone("near", BindAccess.PILOT, 4.0)));
        assertEquals(Optional.of("own"), pick(new Drone("friend", BindAccess.OTHER, 1.0), new Drone("own", BindAccess.PILOT, 400.0)));
    }

    @Test
    void foreignClearedAndLegacyDronesNeverConnect() {
        assertTrue(pick(new Drone("a", BindAccess.OTHER, 1.0), new Drone("b", BindAccess.CLEARED, 1.0),
                new Drone("c", BindAccess.UNBOUND, 1.0)).isEmpty());
        assertTrue(pick().isEmpty());
    }

    @Test
    void outOfRangeIsIgnored() {
        assertEquals(Optional.of("in"), pick(new Drone("out", BindAccess.PILOT, Double.POSITIVE_INFINITY), new Drone("in", BindAccess.PILOT, 9.0)));
        assertTrue(pick(new Drone("out", BindAccess.PILOT, Double.NaN)).isEmpty());
    }
}
