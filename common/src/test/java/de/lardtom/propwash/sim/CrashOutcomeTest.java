package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashOutcomeTest {
    static final int RUNS = 240;
    static final int OTHER_RUNS = 120;
    static final double ALPHA = 0.001;
    static final double Z_ALPHA = 3.090232;
    static final long SEED = System.getenv("PROPWASH_CRASH_SEED") == null ? 11L : Long.parseLong(System.getenv("PROPWASH_CRASH_SEED"));

    record Cell(DroneBuild build, CrashCharacterization.Scenario scenario) {
    }

    static List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        String only = System.getenv("PROPWASH_CRASH_ONLY");
        for (DroneBuild build : DroneBuild.PRESETS) {
            if (only != null && !only.isBlank() && !only.contains(build.frame().key())) {
                continue;
            }
            CrashCharacterization.Scenario[] list = build == DroneBuild.FREESTYLE ? CrashCharacterization.ALL : CrashCharacterization.SHORT;
            for (CrashCharacterization.Scenario s : list) {
                cells.add(new Cell(build, s));
            }
        }
        return cells;
    }

    static int runs(Cell c, int scale) {
        return c.build == DroneBuild.FREESTYLE ? scale : Math.max(1, scale * OTHER_RUNS / RUNS);
    }

    static Map<Cell, CrashCharacterization.Tally> measure(int scale) {
        Map<Cell, CrashCharacterization.Tally> out = new ConcurrentHashMap<>();
        cells().parallelStream().forEach(c -> out.put(c, CrashCharacterization.run(c.build, c.scenario, runs(c, scale), SEED)));
        return out;
    }

    @Test
    void crashesEndOnWhicheverFaceTheyComeToRestOn() {
        String env = System.getenv("PROPWASH_CRASH_RUNS");
        int scale = env == null ? RUNS : Integer.parseInt(env);
        Map<Cell, CrashCharacterization.Tally> tallies = measure(scale);
        StringBuilder report = new StringBuilder();
        for (Cell c : cells()) {
            report.append(CrashCharacterization.row(c.build.frame().key(), c.scenario, tallies.get(c)));
        }
        CalibrationHarness.write("crash.txt", report.toString());
        List<String> failures = new ArrayList<>();
        for (Cell c : cells()) {
            check(c, tallies.get(c), failures);
        }
        checkFreestyle(tallies, failures);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    static void check(Cell c, CrashCharacterization.Tally t, List<String> failures) {
        CrashCharacterization.Scenario s = c.scenario;
        boolean whoop = c.build == DroneBuild.WHOOP;
        String key = c.build.frame().key() + " " + s.name() + ": ";
        int upright = t.runs - t.upsideDown - t.side;
        if (s == CrashCharacterization.GENTLE) {
            atLeast(key + "aufrecht", upright, t.runs, 0.98, failures);
        } else if (s == CrashCharacterization.LOW_DROP) {
            atLeast(key + "aufrecht", upright, t.runs, whoop ? 0.7 : 0.95, failures);
        } else if (s == CrashCharacterization.STEP || s == CrashCharacterization.SLAB) {
            atLeast(key + "aufrecht", upright, t.runs, 0.85, failures);
        } else if (s == CrashCharacterization.INVERTED) {
            atLeast(key + "auf dem Kopf", t.upsideDown, t.runs, 0.75, failures);
        } else if (s == CrashCharacterization.TUMBLE) {
            atLeast(key + "auf dem Kopf", t.upsideDown, t.runs, 0.25, failures);
            atMost(key + "auf dem Kopf", t.upsideDown, t.runs, 0.75, failures);
        } else if (s == CrashCharacterization.WALL_10 || s == CrashCharacterization.DIVE_STEEP_10) {
            atLeast(key + "auf dem Kopf", t.upsideDown, t.runs, 0.15, failures);
        }
        atMost(key + "frei auf der Seite", t.side - t.leaning, t.runs, whoop ? 0.1 : 0.03, failures);
    }

    static void checkFreestyle(Map<Cell, CrashCharacterization.Tally> tallies, List<String> failures) {
        DroneBuild b = DroneBuild.FREESTYLE;
        if (!tallies.containsKey(new Cell(b, CrashCharacterization.GENTLE))) {
            return;
        }
        CrashCharacterization.Tally flat5 = tallies.get(new Cell(b, CrashCharacterization.DIVE_FLAT_5));
        CrashCharacterization.Tally flat10 = tallies.get(new Cell(b, CrashCharacterization.DIVE_FLAT_10));
        CrashCharacterization.Tally flat20 = tallies.get(new Cell(b, CrashCharacterization.DIVE_FLAT_20));
        atMost("5-Zöller flach 5 m/s: auf dem Kopf", flat5.upsideDown, flat5.runs, 0.1, failures);
        atLeast("5-Zöller flach 10 m/s: auf dem Kopf", flat10.upsideDown, flat10.runs, 0.05, failures);
        atLeast("5-Zöller flach 20 m/s: auf dem Kopf", flat20.upsideDown, flat20.runs, 0.05, failures);
        expect(!significantlyMore(flat5, flat20), "5-Zöller flach: bei 20 m/s signifikant seltener auf dem Kopf als bei 5 m/s ("
                + percent(flat20.upsideDownRate()) + " gegen " + percent(flat5.upsideDownRate()) + ")", failures);
        CrashCharacterization.Tally steep5 = tallies.get(new Cell(b, CrashCharacterization.DIVE_STEEP_5));
        atLeast("5-Zöller steil 5 m/s: auf dem Kopf", steep5.upsideDown, steep5.runs, 0.6, failures);
        for (CrashCharacterization.Scenario s : new CrashCharacterization.Scenario[]{CrashCharacterization.DIVE_STEEP_10,
                CrashCharacterization.DIVE_STEEP_20, CrashCharacterization.WALL_5, CrashCharacterization.WALL_10,
                CrashCharacterization.WALL_20}) {
            CrashCharacterization.Tally t = tallies.get(new Cell(b, s));
            atLeast("5-Zöller " + s.name() + ": auf dem Kopf", t.upsideDown, t.runs, 0.25, failures);
            atMost("5-Zöller " + s.name() + ": auf dem Kopf", t.upsideDown, t.runs, 0.75, failures);
        }
        CrashCharacterization.Tally bank = tallies.get(new Cell(b, CrashCharacterization.BANK));
        atLeast("5-Zöller Kurve: auf dem Kopf", bank.upsideDown, bank.runs, 0.2, failures);
        double sum = 0.0;
        double variance = 0.0;
        int n = 0;
        for (CrashCharacterization.Scenario s : CrashCharacterization.CRASHES) {
            CrashCharacterization.Tally t = tallies.get(new Cell(b, s));
            double d = t.upsideDownRate();
            sum += d;
            variance += d * (1.0 - d) / t.runs;
            n++;
        }
        double mean = sum / n;
        double error = Math.sqrt(variance) / n;
        expect(mean >= 0.3 || 0.3 - mean <= Z_ALPHA * error, "5-Zöller: im Mittel der Abstürze nur " + percent(mean)
                + " auf dem Kopf, verlangt 30 % (Toleranz " + percent(Z_ALPHA * error) + ")", failures);
    }

    static void atLeast(String what, int count, int runs, double need, List<String> failures) {
        double p = atMostProbability(count, runs, need);
        expect(p >= ALPHA, String.format(Locale.ROOT, "%s %s (%d von %d), verlangt mindestens %s (p = %.1e < %.0e)", what,
                percent(count / (double) runs), count, runs, percent(need), p, ALPHA), failures);
    }

    static void atMost(String what, int count, int runs, double limit, List<String> failures) {
        double p = 1.0 - atMostProbability(count - 1, runs, limit);
        expect(p >= ALPHA, String.format(Locale.ROOT, "%s %s (%d von %d), erlaubt höchstens %s (p = %.1e < %.0e)", what,
                percent(count / (double) runs), count, runs, percent(limit), p, ALPHA), failures);
    }

    static boolean significantlyMore(CrashCharacterization.Tally more, CrashCharacterization.Tally less) {
        double pooled = (more.upsideDown + less.upsideDown) / (double) (more.runs + less.runs);
        double error = Math.sqrt(pooled * (1.0 - pooled) * (1.0 / more.runs + 1.0 / less.runs));
        double diff = more.upsideDownRate() - less.upsideDownRate();
        return error == 0.0 ? diff > 0.0 : diff / error > Z_ALPHA;
    }

    static double atMostProbability(int k, int n, double p) {
        if (k < 0) {
            return 0.0;
        }
        if (k >= n || p <= 0.0) {
            return 1.0;
        }
        if (p >= 1.0) {
            return 0.0;
        }
        double[] logFactorial = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            logFactorial[i] = logFactorial[i - 1] + Math.log(i);
        }
        double logP = Math.log(p);
        double logQ = Math.log1p(-p);
        double sum = 0.0;
        for (int i = 0; i <= k; i++) {
            sum += Math.exp(logFactorial[n] - logFactorial[i] - logFactorial[n - i] + i * logP + (n - i) * logQ);
        }
        return Math.min(1.0, sum);
    }

    static void expect(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    static String percent(double rate) {
        return Math.round(100.0 * rate) + " %";
    }

    @Test
    void toleranceKeepsFalseAlarmsRareAndStillCatchesRegressions() {
        for (int runs : new int[]{RUNS, OTHER_RUNS}) {
            double shift = runs == RUNS ? 0.15 : 0.2;
            double[] needs = runs == RUNS ? new double[]{0.98, 0.97, 0.95, 0.9, 0.85, 0.75, 0.6, 0.25, 0.2, 0.15, 0.05}
                    : new double[]{0.98, 0.97, 0.95, 0.9, 0.85, 0.75, 0.7, 0.25, 0.15};
            for (double need : needs) {
                int critical = -1;
                while (atMostProbability(critical + 1, runs, need) < ALPHA) {
                    critical++;
                }
                double falseAlarm = atMostProbability(critical, runs, need);
                double power = atMostProbability(critical, runs, Math.max(0.0, need - shift));
                String what = runs + " Läufe, Schwelle " + percent(need) + ": ";
                assertTrue(falseAlarm < ALPHA, what + "Fehlalarm " + falseAlarm);
                assertTrue(power >= 0.9, what + "Rückschritt um " + percent(shift) + " nur zu " + percent(power) + " erkannt");
            }
        }
    }

    @Test
    void checksPassAtTheThresholdAndFailOnRegressions() {
        Cell wall = new Cell(DroneBuild.WHOOP, CrashCharacterization.WALL_10);
        Cell gentle = new Cell(DroneBuild.FREESTYLE, CrashCharacterization.GENTLE);
        Cell inverted = new Cell(DroneBuild.FREESTYLE, CrashCharacterization.INVERTED);
        Cell tumble = new Cell(DroneBuild.FREESTYLE, CrashCharacterization.TUMBLE);
        assertTrue(failures(wall, tally(OTHER_RUNS, OTHER_RUNS * 15 / 100, 0)).isEmpty());
        assertTrue(failures(gentle, tally(RUNS, 0, RUNS * 2 / 100)).isEmpty());
        assertTrue(failures(inverted, tally(RUNS, RUNS * 75 / 100, 0)).isEmpty());
        assertTrue(failures(tumble, tally(RUNS, RUNS * 74 / 100, 0)).isEmpty());
        assertTrue(!failures(wall, tally(OTHER_RUNS, OTHER_RUNS * 2 / 100, 0)).isEmpty());
        assertTrue(!failures(gentle, tally(RUNS, 0, RUNS * 8 / 100)).isEmpty());
        assertTrue(!failures(inverted, tally(RUNS, RUNS * 60 / 100, 0)).isEmpty());
        assertTrue(!failures(tumble, tally(RUNS, RUNS * 12 / 100, 0)).isEmpty());
        assertTrue(!failures(tumble, tally(RUNS, RUNS * 88 / 100, 0)).isEmpty());
        assertTrue(!failures(gentle, tally(RUNS, 0, RUNS * 6 / 100)).isEmpty());
    }

    static List<String> failures(Cell c, CrashCharacterization.Tally t) {
        List<String> failures = new ArrayList<>();
        check(c, t, failures);
        return failures;
    }

    static CrashCharacterization.Tally tally(int runs, int upsideDown, int side) {
        CrashCharacterization.Tally t = new CrashCharacterization.Tally();
        t.runs = runs;
        t.upsideDown = upsideDown;
        t.side = side;
        t.leaning = side;
        return t;
    }

    @Test
    void traceOneCrash() {
        String spec = System.getenv("PROPWASH_CRASH_TRACE");
        if (spec == null || spec.isBlank()) {
            return;
        }
        String[] a = spec.split(",");
        DroneBuild build = DroneBuild.PRESETS.stream().filter(b -> b.frame().key().equals(a[0])).findFirst().orElseThrow();
        CrashCharacterization.Scenario scenario = CrashCharacterization.ALL[Integer.parseInt(a[1])];
        int index = Integer.parseInt(a[2]);
        SplittableRandom r = CrashCharacterization.random(build, scenario, SEED);
        SplittableRandom pick = null;
        for (int i = 0; i <= index; i++) {
            pick = r.split();
        }
        StringBuilder out = new StringBuilder(scenario.name() + " #" + index + "\n");
        CrashCharacterization.Outcome o = CrashCharacterization.run(build, scenario, pick, out);
        out.append("Ende ").append(o.finalTilt).append("\n");
        CalibrationHarness.write("crash-trace.txt", out.toString());
    }
}
