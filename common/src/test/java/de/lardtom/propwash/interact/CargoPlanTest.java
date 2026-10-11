package de.lardtom.propwash.interact;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CargoPlanTest {
    private static CargoPlan.Load load(String key, double grams, int count, int max) {
        return new CargoPlan.Load(key, grams, count, max);
    }

    @Test
    void weightLimitsHowManyItemsFit() {
        assertEquals(8, CargoPlan.takeable(List.of(), load("cobble", 50.0, 64, 64), 400.0, 4));
        assertEquals(6, CargoPlan.takeable(List.of(load("seeds", 10.0, 10, 64)), load("cobble", 50.0, 64, 64), 400.0, 4));
        assertEquals(0, CargoPlan.takeable(List.of(load("cobble", 50.0, 8, 64)), load("cobble", 50.0, 64, 64), 400.0, 4));
    }

    @Test
    void stackLimitCountsDifferentItems() {
        List<CargoPlan.Load> full = List.of(load("a", 1.0, 1, 64), load("b", 1.0, 1, 64), load("c", 1.0, 1, 64), load("d", 1.0, 1, 64));
        assertEquals(0, CargoPlan.takeable(full, load("e", 1.0, 5, 64), 400.0, 4));
        List<CargoPlan.Load> withSame = List.of(load("a", 1.0, 60, 64), load("b", 1.0, 1, 64), load("c", 1.0, 1, 64), load("d", 1.0, 1, 64));
        assertEquals(4, CargoPlan.takeable(withSame, load("a", 1.0, 10, 64), 400.0, 4), "nur der Platz im passenden Stapel");
    }

    @Test
    void unstackableItemsTakeOneSlotEach() {
        assertEquals(2, CargoPlan.takeable(List.of(load("x", 1.0, 1, 64), load("y", 1.0, 1, 64)), load("sword", 150.0, 3, 1), 400.0, 4));
        assertEquals(1, CargoPlan.takeable(List.of(), load("sword", 150.0, 1, 1), 400.0, 4));
    }

    @Test
    void noCapacityNoPickup() {
        assertEquals(0, CargoPlan.takeable(List.of(), load("apple", 10.0, 5, 64), 0.0, 4));
        assertEquals(0, CargoPlan.takeable(List.of(), load("apple", 10.0, 0, 64), 400.0, 4));
        assertEquals(0, CargoPlan.takeable(List.of(), load("drone", 650.0, 1, 1), 400.0, 4), "zu schwer");
        assertEquals(400.0, CargoPlan.totalGrams(List.of(load("a", 50.0, 4, 64), load("b", 100.0, 2, 16))), 1.0E-9);
    }
}
