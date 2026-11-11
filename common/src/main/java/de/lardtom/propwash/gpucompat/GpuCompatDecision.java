package de.lardtom.propwash.gpucompat;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public record GpuCompatDecision(boolean active, Reason reason, GpuCompatMode mode, Source source, String vendor, String renderer, boolean windows,
                                boolean simulated) {
    public static final String MODE_PROPERTY = "propwash.gpuCompat";
    public static final String MODE_ENV = "PROPWASH_GPU_COMPAT";
    public static final String SIMULATE_ENV = "PROPWASH_GPU_COMPAT_SIMULATE";
    public static final String SIMULATE_INTEL_WINDOWS = "intel-windows";
    public static final String SIMULATED_VENDOR = "Intel";
    public static final String SIMULATED_RENDERER = "Intel(R) UHD Graphics 620";

    public static GpuCompatDecision decide(GpuCompatMode mode, Source source, boolean windows, @Nullable String vendor, @Nullable String renderer,
                                           boolean simulated) {
        String v = vendor == null ? "" : vendor.strip();
        String r = renderer == null ? "" : renderer.strip();
        Reason reason = switch (mode) {
            case ON -> Reason.SETTING_ON;
            case OFF -> Reason.SETTING_OFF;
            case AUTO -> !intel(v, r) ? Reason.AUTO_NOT_INTEL : windows ? Reason.AUTO_INTEL_WINDOWS : Reason.AUTO_NOT_WINDOWS;
        };
        return new GpuCompatDecision(reason.active, reason, mode, source, v, r, windows, simulated);
    }

    public static GpuCompatDecision undecided() {
        return new GpuCompatDecision(false, Reason.NOT_OPENGL, GpuCompatMode.AUTO, Source.SETTING, "", "", false, false);
    }

    public static boolean intel(@Nullable String vendor, @Nullable String renderer) {
        return contains(vendor, "intel") || contains(renderer, "intel");
    }

    public static @Nullable GpuCompatMode override(@Nullable String property, @Nullable String env) {
        GpuCompatMode fromProperty = GpuCompatMode.parse(property);
        return fromProperty != null ? fromProperty : GpuCompatMode.parse(env);
    }

    public static boolean simulateIntelWindows(@Nullable String env) {
        return env != null && env.strip().equalsIgnoreCase(SIMULATE_INTEL_WINDOWS);
    }

    private static boolean contains(@Nullable String text, String part) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(part);
    }

    public String describe() {
        String device = (this.vendor.isEmpty() ? "?" : this.vendor) + " / " + (this.renderer.isEmpty() ? "?" : this.renderer)
                + (this.windows ? ", Windows" : "") + (this.simulated ? ", simuliert" : "");
        String from = this.source == Source.OVERRIDE ? " (vorgegeben über " + MODE_PROPERTY + " bzw. " + MODE_ENV + ")" : "";
        return (this.active ? "an" : "aus") + " – " + this.reason.text + from + " (" + device + ")";
    }

    public enum Reason {
        SETTING_ON(true, "Einstellung An"),
        SETTING_OFF(false, "Einstellung Aus"),
        AUTO_INTEL_WINDOWS(true, "Automatisch, Intel-Grafik unter Windows erkannt"),
        AUTO_NOT_INTEL(false, "Automatisch, keine Intel-Grafik"),
        AUTO_NOT_WINDOWS(false, "Automatisch, Intel-Grafik, aber nicht unter Windows"),
        NOT_OPENGL(false, "nicht entschieden (kein OpenGL-Gerät angelegt)");

        private final boolean active;
        private final String text;

        Reason(boolean active, String text) {
            this.active = active;
            this.text = text;
        }

        public String key() {
            return "propwash.gpucompat.reason." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Source {
        SETTING,
        OVERRIDE
    }
}
