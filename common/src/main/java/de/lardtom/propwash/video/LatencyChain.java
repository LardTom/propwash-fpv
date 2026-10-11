package de.lardtom.propwash.video;

import java.util.Locale;

public final class LatencyChain {
    public enum Part {
        INPUT_AGE,
        SIM_PENDING,
        VIDEO_SYSTEM,
        VIDEO_APPLIED,
        PIPELINE,
        SCANOUT,
        TOTAL,
        TOTAL_UNCOMPENSATED
    }

    private static final Part[] PARTS = Part.values();

    private final long[] count = new long[PARTS.length];
    private final double[] sum = new double[PARTS.length];
    private final long[] max = new long[PARTS.length];
    private final long[] last = new long[PARTS.length];

    public void reset() {
        for (int i = 0; i < PARTS.length; i++) {
            this.count[i] = 0L;
            this.sum[i] = 0.0;
            this.max[i] = 0L;
            this.last[i] = 0L;
        }
    }

    public void frame(long inputAgeNanos, long simPendingNanos, long videoSystemNanos, long videoAppliedNanos, long pipelineNanos,
                      long scanoutNanos) {
        long input = Math.max(0L, inputAgeNanos);
        long pending = Math.max(0L, simPendingNanos);
        long applied = Math.max(0L, videoAppliedNanos);
        long pipeline = Math.max(0L, pipelineNanos);
        long scanout = Math.max(0L, scanoutNanos);
        this.add(Part.INPUT_AGE, input);
        this.add(Part.SIM_PENDING, pending);
        this.add(Part.VIDEO_SYSTEM, Math.max(0L, videoSystemNanos));
        this.add(Part.VIDEO_APPLIED, applied);
        this.add(Part.PIPELINE, pipeline);
        this.add(Part.SCANOUT, scanout);
        long system = Math.max(0L, videoSystemNanos);
        this.add(Part.TOTAL, total(input, pending, applied, pipeline, scanout));
        this.add(Part.TOTAL_UNCOMPENSATED, input + pending + system + pipeline + scanout);
    }

    public static long total(long inputAgeNanos, long simPendingNanos, long videoAppliedNanos, long pipelineNanos, long scanoutNanos) {
        return Math.max(0L, inputAgeNanos) + Math.max(Math.max(0L, simPendingNanos), Math.max(0L, videoAppliedNanos))
                + Math.max(0L, pipelineNanos) + Math.max(0L, scanoutNanos);
    }

    private void add(Part part, long nanos) {
        int i = part.ordinal();
        this.count[i]++;
        this.sum[i] += nanos;
        this.max[i] = Math.max(this.max[i], nanos);
        this.last[i] = nanos;
    }

    public long frames() {
        return this.count[Part.TOTAL.ordinal()];
    }

    public double meanMs(Part part) {
        int i = part.ordinal();
        return this.count[i] == 0L ? 0.0 : this.sum[i] / this.count[i] * 1.0E-6;
    }

    public double maxMs(Part part) {
        return this.max[part.ordinal()] * 1.0E-6;
    }

    public double lastMs(Part part) {
        return this.last[part.ordinal()] * 1.0E-6;
    }

    public String summary() {
        StringBuilder out = new StringBuilder();
        out.append("frames=").append(this.frames());
        for (Part part : PARTS) {
            out.append(String.format(Locale.ROOT, " %s=%.2f/%.2f", part.name().toLowerCase(Locale.ROOT), this.meanMs(part), this.maxMs(part)));
        }
        return out.toString();
    }
}
