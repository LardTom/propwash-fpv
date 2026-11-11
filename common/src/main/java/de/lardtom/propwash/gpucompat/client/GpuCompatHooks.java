package de.lardtom.propwash.gpucompat.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.gpucompat.GpuCompatDecision;
import de.lardtom.propwash.gpucompat.GpuCompatMode;
import de.lardtom.propwash.platform.Services;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;

public final class GpuCompatHooks {
    private static volatile @Nullable GpuCompatDecision decision;
    private static volatile int dsaOffered = -1;
    private static volatile int storageOffered = -1;
    private static volatile String dsaChosen = "";
    private static volatile String storageChosen = "";
    private static volatile int devices;
    private static boolean logged;

    private GpuCompatHooks() {
    }

    public static boolean directStateAccess(boolean offered) {
        dsaOffered = offered ? 1 : 0;
        return offered && !decision().active();
    }

    public static void storageOffered(boolean offered) {
        storageOffered = offered ? 1 : 0;
    }

    public static boolean forceMutable(boolean forced) {
        return forced || decision().active();
    }

    public static void storageCreated(@Nullable Object storage) {
        storageChosen = name(storage);
    }

    public static void dsaCreated(@Nullable Object dsa) {
        dsaChosen = name(dsa);
        devices++;
        report();
    }

    public static GpuCompatDecision decision() {
        GpuCompatDecision current = decision;
        if (current == null) {
            current = compute();
            decision = current;
        }
        return current;
    }

    public static Status status() {
        GpuCompatDecision current = decision;
        return new Status(current == null ? GpuCompatDecision.undecided() : current, flag(dsaOffered), flag(storageOffered), dsaChosen,
                storageChosen, devices);
    }

    private static GpuCompatDecision compute() {
        GpuCompatMode override = GpuCompatDecision.override(System.getProperty(GpuCompatDecision.MODE_PROPERTY), System.getenv(GpuCompatDecision.MODE_ENV));
        GpuCompatMode mode = override != null ? override : GpuCompatSettings.stored();
        GpuCompatDecision.Source source = override != null ? GpuCompatDecision.Source.OVERRIDE : GpuCompatDecision.Source.SETTING;
        boolean simulated = development() && GpuCompatDecision.simulateIntelWindows(System.getenv(GpuCompatDecision.SIMULATE_ENV));
        String vendor = simulated ? GpuCompatDecision.SIMULATED_VENDOR : glString(GL11C.GL_VENDOR);
        String renderer = simulated ? GpuCompatDecision.SIMULATED_RENDERER : glString(GL11C.GL_RENDERER);
        boolean windows = simulated || Util.getPlatform() == Util.OS.WINDOWS;
        return GpuCompatDecision.decide(mode, source, windows, vendor, renderer, simulated);
    }

    private static void report() {
        if (logged) {
            return;
        }
        logged = true;
        Status status = status();
        Constants.LOG.info("Grafik-Kompatibilität: {}", status.describe());
    }

    private static boolean development() {
        try {
            return Services.PLATFORM.isDevelopmentEnvironment();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static String glString(int name) {
        try {
            String value = GL11C.glGetString(name);
            return value == null ? "" : value;
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    private static String name(@Nullable Object value) {
        return value == null ? "?" : value.getClass().getSimpleName();
    }

    private static @Nullable Boolean flag(int value) {
        return value < 0 ? null : value == 1;
    }

    public record Status(GpuCompatDecision decision, @Nullable Boolean dsaOffered, @Nullable Boolean storageOffered, String dsaChosen,
                         String storageChosen, int devices) {
        public boolean hooked() {
            return this.dsaOffered != null && this.storageOffered != null && !this.dsaChosen.isEmpty() && !this.storageChosen.isEmpty();
        }

        public boolean emulated() {
            return "Emulated".equals(this.dsaChosen);
        }

        public boolean mutableBuffers() {
            return "Mutable".equals(this.storageChosen);
        }

        public String describe() {
            String way = this.decision.active() ? "OpenGL-Weg wie auf macOS" : "OpenGL-Weg unverändert";
            return String.format("%s. Treiber bietet Direct State Access: %s, Buffer Storage: %s; %s: DSA %s, Puffer %s",
                    this.decision.describe(), yesNo(this.dsaOffered), yesNo(this.storageOffered), way, dsa(this.dsaChosen), buffers(this.storageChosen));
        }

        private static String yesNo(@Nullable Boolean value) {
            return value == null ? "?" : value ? "ja" : "nein";
        }

        private static String dsa(String chosen) {
            return switch (chosen) {
                case "Core" -> "direkt (Core)";
                case "Emulated" -> "nachgebildet (Emulated)";
                default -> chosen.isEmpty() ? "?" : chosen;
            };
        }

        private static String buffers(String chosen) {
            return switch (chosen) {
                case "Immutable" -> "dauerhaft gemappt (Immutable)";
                case "Mutable" -> "veränderlich (Mutable)";
                default -> chosen.isEmpty() ? "?" : chosen;
            };
        }
    }
}
