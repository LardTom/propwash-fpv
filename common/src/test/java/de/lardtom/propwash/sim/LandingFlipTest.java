package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingFlipTest {
    static final int RUNS = 20;
    static final int LEDGE_RUNS = 100;
    static final long SEED = 7L;
    static final StabMode[] MODES = {StabMode.ACRO, StabMode.ANGLE};

    record Cell(DroneBuild build, StabMode mode, boolean crashDisarm, LandingCharacterization.Surface surface,
                LandingCharacterization.Family family) {
        String key() {
            return this.build.frame().key() + " " + this.mode + " " + (this.crashDisarm ? "Crash-Disarm" : "ohne Crash-Disarm") + " "
                    + this.surface + " " + this.family.name();
        }
    }

    static List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (LandingCharacterization.Family family : LandingCharacterization.FAMILIES) {
                for (StabMode mode : MODES) {
                    for (boolean crashDisarm : new boolean[]{true, false}) {
                        cells.add(new Cell(build, mode, crashDisarm, LandingCharacterization.Surface.FLAT, family));
                    }
                }
            }
            for (LandingCharacterization.Surface surface : new LandingCharacterization.Surface[]{LandingCharacterization.Surface.SLAB_EDGE,
                    LandingCharacterization.Surface.STEP}) {
                for (StabMode mode : MODES) {
                    cells.add(new Cell(build, mode, true, surface, LandingCharacterization.DRIFT));
                }
            }
        }
        return cells;
    }

    static Map<Cell, LandingCharacterization.Tally> measure(int runs) {
        Map<Cell, LandingCharacterization.Tally> out = new ConcurrentHashMap<>();
        cells().parallelStream().forEach(c -> out.put(c, LandingCharacterization.run(c.build, c.mode, c.crashDisarm, c.surface, c.family,
                ledge(c) ? Math.max(runs, LEDGE_RUNS) : runs, SEED)));
        return out;
    }

    @Test
    void landingsEndTheRightWayUp() {
        String env = System.getenv("PROPWASH_LANDING_RUNS");
        int runs = env == null ? RUNS : Integer.parseInt(env);
        Map<Cell, LandingCharacterization.Tally> tallies = measure(runs);
        StringBuilder report = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (Cell c : cells()) {
            LandingCharacterization.Tally t = tallies.get(c);
            report.append(LandingCharacterization.row(c.build.frame().key(), c.mode, c.crashDisarm, c.surface, c.family, t));
            double limit = limit(c);
            double bad = ledge(c) ? (double) (t.flips + t.sides) / t.runs : t.flipRate();
            if (bad > limit || (!ledge(c) && gentle(c.family) && t.sides > 0)) {
                failures.add(c.key() + ": " + t.flips + " von " + t.runs + " auf dem Kopf, " + t.sides + " auf der Seite (erlaubt "
                        + Math.round(100.0 * limit) + " %)");
            }
        }
        CalibrationHarness.write("landing.txt", report.toString());
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    static boolean ledge(Cell c) {
        return c.surface != LandingCharacterization.Surface.FLAT;
    }

    static boolean gentle(LandingCharacterization.Family family) {
        return family == LandingCharacterization.GENTLE || family == LandingCharacterization.DRIFT || family == LandingCharacterization.IDLE;
    }

    static double limit(Cell c) {
        boolean whoop = c.build == DroneBuild.WHOOP;
        if (c.surface != LandingCharacterization.Surface.FLAT) {
            return 0.1;
        }
        if (gentle(c.family)) {
            return 0.0;
        }
        if (c.family == LandingCharacterization.SKID) {
            return 0.05;
        }
        if (c.family == LandingCharacterization.HARD) {
            return whoop ? 0.25 : 0.05;
        }
        return whoop ? 0.35 : 0.1;
    }

    @Test
    void traceOneLanding() {
        String spec = System.getenv("PROPWASH_LANDING_TRACE");
        if (spec == null || spec.isBlank()) {
            return;
        }
        String[] a = spec.split(",");
        DroneBuild build = DroneBuild.PRESETS.stream().filter(b -> b.frame().key().equals(a[0])).findFirst().orElseThrow();
        StabMode mode = StabMode.valueOf(a[1]);
        LandingCharacterization.Family family = LandingCharacterization.FAMILIES[Integer.parseInt(a[2])];
        LandingCharacterization.Surface surface = LandingCharacterization.Surface.valueOf(a[3]);
        int index = Integer.parseInt(a[4]);
        SplittableRandom r = new SplittableRandom(SEED);
        LandingCharacterization.Touchdown td = null;
        for (int i = 0; i <= index; i++) {
            td = LandingCharacterization.Touchdown.draw(family, r);
        }
        StringBuilder out = new StringBuilder(td + "\n");
        LandingCharacterization.land(build, mode, true, surface, family, td, out);
        CalibrationHarness.write("trace.txt", out.toString());
    }
}
