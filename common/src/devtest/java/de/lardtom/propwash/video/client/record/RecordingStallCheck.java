package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.video.record.PipelineStats;
import org.jspecify.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.Locale;

public final class RecordingStallCheck {
    public static final double LIMIT_MS = 25.0;
    public static final double QUANTILE = 0.99;
    private static final long MB = 1024L * 1024L;

    private RecordingStallCheck() {
    }

    public static Result check(PipelineStats.Snapshot snapshot) {
        double captureAt = snapshot.captureMsAt(QUANTILE);
        double readbackAt = snapshot.readbackMsAt(QUANTILE);
        long slowCaptures = snapshot.capturesOver(LIMIT_MS);
        long slowReadbacks = snapshot.readbacksOver(LIMIT_MS);
        String detail = String.format(Locale.ROOT, "Auftrag %d von %d, Auslesen %d von %d über %.0f ms; Auftrag p99 %.2f, max %.2f; Auslesen p99 %.2f, max %.2f",
                slowCaptures, snapshot.captures(), slowReadbacks, snapshot.readbacks(), LIMIT_MS, captureAt, snapshot.captureNanosMax() / 1.0E6,
                readbackAt, snapshot.readbackNanosMax() / 1.0E6);
        double percentile = Math.max(captureAt, readbackAt);
        if (percentile > LIMIT_MS) {
            return new Result(String.format(Locale.ROOT, "Renderthread hängt: 99. Perzentil %.2f ms über der Grenze %.0f ms (%s)", percentile, LIMIT_MS,
                    detail), null, detail);
        }
        double worst = Math.max(snapshot.captureNanosMax(), snapshot.readbackNanosMax()) / 1.0E6;
        if (slowCaptures + slowReadbacks > 0L) {
            return new Result(null, String.format(Locale.ROOT, "Ausreißer im Renderthread, kein Fehler (99. Perzentil %.2f ms, schlimmstes Bild %.2f ms): %s",
                    percentile, worst, detail), detail);
        }
        return new Result(null, null, detail);
    }

    public static String memory(PipelineStats.Snapshot snapshot) {
        Runtime runtime = Runtime.getRuntime();
        return String.format(Locale.ROOT, "System frei %s von %s, Swap belegt %s; Heap-Spitze %d MB von höchstens %d MB; GC während der Aufnahme %d× %d ms",
                megabytes(system("getFreeMemorySize")), megabytes(system("getTotalMemorySize")), swapUsed(), snapshot.heapPeak() / MB,
                runtime.maxMemory() / MB, snapshot.gcCount(), snapshot.gcMillis());
    }

    private static String swapUsed() {
        long total = system("getTotalSwapSpaceSize");
        long free = system("getFreeSwapSpaceSize");
        return total < 0L || free < 0L ? "unbekannt" : megabytes(total - free);
    }

    private static String megabytes(long bytes) {
        return bytes < 0L ? "unbekannt" : bytes / MB + " MB";
    }

    private static long system(String name) {
        try {
            Class<?> type = Class.forName("com.sun.management.OperatingSystemMXBean");
            Object bean = ManagementFactory.getOperatingSystemMXBean();
            if (type.isInstance(bean) && type.getMethod(name).invoke(bean) instanceof Long value) {
                return value;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return -1L;
    }

    public record Result(@Nullable String failure, @Nullable String outliers, String detail) {
    }
}
