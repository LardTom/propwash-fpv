package de.lardtom.propwash.freecam.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovGatePassesTest {
    private static final double STEP = 0.3;
    private static final double X = 0.5;
    private static final double Y = 2.0;
    private static final List<CovGatePasses.Opening> COURSE = List.of(new CovGatePasses.Opening(X, Y, 10.5),
            new CovGatePasses.Opening(X, Y, 20.5), new CovGatePasses.Opening(X, Y, 30.5));

    private static CovGatePasses passes() {
        return new CovGatePasses(COURSE, 0.5);
    }

    private static int fly(double startOffset, double driftZ, double driftX) {
        CovGatePasses passes = passes();
        double start = COURSE.getFirst().z() - 6.0;
        double end = COURSE.getLast().z() + 6.0;
        double x = X;
        double y = Y;
        double z = start + startOffset;
        boolean back = false;
        for (int tick = 0; tick < 1000; tick++) {
            double to = back ? Math.max(start, z - STEP) : Math.min(end, z + STEP);
            passes.tick(x, y, z, X, Y, to, back);
            if (!back && to >= end) {
                back = true;
            } else if (back && to <= start) {
                return passes.count();
            }
            x = X + driftX;
            y = Y + 0.001;
            z = to + driftZ;
        }
        throw new AssertionError("Flug endet nicht");
    }

    @Test
    void driftAcrossThePlaneBetweenTwoTicksCounts() {
        CovGatePasses passes = passes();
        passes.tick(X, Y, 10.199, X, Y, 10.499, false);
        passes.tick(X, Y, 10.502, X, Y, 10.802, false);
        assertEquals(1, passes.count(), "Wind schiebt die Freecam zwischen zwei Ticks durch die Ebene des ersten Gates");
        assertEquals(0.003, passes.drift(), 1.0E-9);
    }

    @Test
    void everyGateCountsOnceInEachDirectionWithWindDrift() {
        List<String> wrong = new ArrayList<>();
        int fewer = 0;
        for (int d = -20; d <= 20; d++) {
            double drift = d * 0.0005;
            for (int o = -40; o <= 40; o++) {
                double offset = o * 0.0025;
                int count = fly(offset, drift, 0.0002 * d);
                if (count < 2 * COURSE.size()) {
                    fewer++;
                }
                if (count != 2 * COURSE.size()) {
                    wrong.add(String.format(Locale.ROOT, "Abdrift %.4f, Start %+.4f: %d", drift, offset, count));
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.size() + " Flüge zählen nicht genau 6 Durchflüge, davon " + fewer + " zu wenige, etwa "
                + wrong.subList(0, Math.min(6, wrong.size())));
    }

    @Test
    void landingExactlyOnThePlaneCountsOnce() {
        CovGatePasses passes = passes();
        passes.tick(X, Y, 10.2, X, Y, 10.5, false);
        passes.tick(X, Y, 10.5, X, Y, 10.8, false);
        assertEquals(1, passes.count());
    }

    @Test
    void driftBackAcrossThePlaneIsNoSecondPassAndNoReturn() {
        CovGatePasses passes = passes();
        passes.tick(X, Y, 10.3, X, Y, 10.6, false);
        passes.tick(X, Y, 10.45, X, Y, 10.75, false);
        assertEquals(1, passes.count(), "hin");
        passes.tick(X, Y, 10.8, X, Y, 10.2, true);
        assertEquals(2, passes.count(), "zurück");
    }

    @Test
    void passBesideTheOpeningDoesNotCount() {
        CovGatePasses passes = passes();
        passes.tick(X + 0.8, Y, 10.3, X + 0.8, Y, 10.6, false);
        passes.tick(X, Y + 0.8, 20.3, X, Y + 0.8, 20.6, false);
        assertEquals(0, passes.count());
        assertEquals(6, passes.expected());
    }
}
