package de.lardtom.propwash.gpucompat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public final class GpuFaults {
    private static final AtomicLong COPIES = new AtomicLong();
    private static final AtomicLong READS = new AtomicLong();
    private static final AtomicLong SKIPPED_COPIES = new AtomicLong();
    private static final AtomicLong ZEROED_READS = new AtomicLong();
    private static final String SCREENSHOT = "net.minecraft.client.Screenshot";
    private static final String SCREENSHOTS = "de.lardtom.propwash.dev.client.Screenshots";
    private static final String GUARDED = "de.lardtom.propwash.gpucompat.client.GuardedScreenshot";
    private static volatile boolean copyNoop;
    private static volatile boolean readZero;

    private GpuFaults() {
    }

    public static void set(boolean copy, boolean read, boolean classic) {
        copyNoop = copy;
        readZero = read;
        ClassicReadback.fault = classic ? ClassicReadback.Fault.ZERO : ClassicReadback.Fault.NONE;
    }

    public static void clear() {
        set(false, false, false);
    }

    public static boolean any() {
        return copyNoop || readZero || ClassicReadback.fault != ClassicReadback.Fault.NONE;
    }

    public static String describe() {
        return "Blit ohne Wirkung " + copyNoop + ", GPU-Rücklesen Nullen " + readZero + ", klassisches Rücklesen Nullen "
                + (ClassicReadback.fault == ClassicReadback.Fault.ZERO);
    }

    public static boolean skipCopy() {
        COPIES.incrementAndGet();
        if (copyNoop) {
            SKIPPED_COPIES.incrementAndGet();
            return true;
        }
        return false;
    }

    public static boolean zeroRead(CommandEncoderBackend encoder, GpuBuffer destination, long offset, int bytes, Runnable callback) {
        READS.incrementAndGet();
        if (!readZero || measurement()) {
            return false;
        }
        ZEROED_READS.incrementAndGet();
        ByteBuffer zeros = MemoryUtil.memCalloc(Math.max(1, bytes));
        try {
            encoder.writeToBuffer(destination.slice(offset, bytes), zeros);
        } finally {
            MemoryUtil.memFree(zeros);
        }
        RenderSystem.queueFencedTask(callback);
        return true;
    }

    private static boolean measurement() {
        return StackWalker.getInstance().walk(frames -> {
            boolean screenshot = false;
            boolean test = false;
            for (StackWalker.StackFrame frame : (Iterable<StackWalker.StackFrame>) frames::iterator) {
                String name = frame.getClassName();
                if (name.equals(GUARDED)) {
                    return false;
                }
                if (name.equals(SCREENSHOT)) {
                    screenshot = true;
                } else if (screenshot && (name.endsWith("SelfTests") || name.equals(SCREENSHOTS))) {
                    test = true;
                }
            }
            return screenshot && test;
        });
    }

    public static long copies() {
        return COPIES.get();
    }

    public static long reads() {
        return READS.get();
    }

    public static long skippedCopies() {
        return SKIPPED_COPIES.get();
    }

    public static long zeroedReads() {
        return ZEROED_READS.get();
    }

    public static long classicReads() {
        return ClassicReadback.reads();
    }

    public static void resetHealth() {
        GpuPathCheck.recheck();
    }
}
