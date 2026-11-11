package de.lardtom.propwash.dev;

import java.util.Random;

public final class BadNetwork {
    public static final long MS = 1_000_000L;

    public record Settings(long latencyNanos, long jitterNanos, double loss, long retransmitNanos, long tickNanos, long seed) {
        public String text() {
            return "Latenz " + this.latencyNanos / MS + " ms, Jitter " + this.jitterNanos / MS + " ms, Verlust " + Math.round(this.loss * 100.0)
                    + " % (Neusendung nach " + this.retransmitNanos / MS + " ms), Takt " + this.tickNanos / MS + " ms";
        }
    }

    public static final Settings PILOT = new Settings(120L * MS, 80L * MS, 0.05, 300L * MS, 50L * MS, 0x0A4L);
    public static final Settings SPECTATOR = new Settings(90L * MS, 70L * MS, 0.05, 250L * MS, 0L, 0x0A5L);

    private BadNetwork() {
    }

    public static long[] arrivals(long[] sends, Settings settings, long lossFrom, long lossTo) {
        Random random = new Random(settings.seed());
        long[] out = new long[sends.length];
        long last = Long.MIN_VALUE;
        for (int i = 0; i < sends.length; i++) {
            long at = sends[i] + settings.latencyNanos() + Math.round(random.nextDouble() * settings.jitterNanos());
            boolean lossWindow = sends[i] >= lossFrom && sends[i] <= lossTo;
            if (random.nextDouble() < settings.loss() && lossWindow) {
                at += settings.retransmitNanos();
            }
            at = Math.max(at, last);
            last = at;
            if (settings.tickNanos() > 0L) {
                at = Math.ceilDiv(at, settings.tickNanos()) * settings.tickNanos();
            }
            out[i] = at;
        }
        return out;
    }

    public static int stalls(long[] arrivals, long sendSpacing) {
        int stalls = 0;
        for (int i = 1; i < arrivals.length; i++) {
            if (arrivals[i] - arrivals[i - 1] >= 4L * sendSpacing) {
                stalls++;
            }
        }
        return stalls;
    }
}
