package de.lardtom.propwash.gpucompat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.gpucompat.GpuPaths;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class GuardedReadback {
    private static final int USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
    private static final Set<String> WARNED = new HashSet<>();
    private static long classicReads;
    private static long missedReads;

    private GuardedReadback() {
    }

    public static GpuPaths.ReadPath path() {
        return GpuPathCheck.ensure().readPath();
    }

    public static long classicReads() {
        return classicReads;
    }

    public static long missedReads() {
        return missedReads;
    }

    public static void read(GpuTexture source, int x, int y, int width, int height, String what, Consumer<Read> sink) {
        GpuPaths.ReadPath path = path();
        int bytes = width * height * source.getFormat().blockSize();
        switch (path) {
            case GPU -> gpu(source, x, y, width, height, bytes, what, sink);
            case CLASSIC -> sink.accept(new Read(classic(source, x, y, width, height, bytes, what), GpuPaths.ReadPath.CLASSIC));
            case NONE -> {
                missedReads++;
                warn(what, "kein Rücklese-Weg liefert ein Bild (" + GpuPathCheck.health().describe() + ")");
                GpuPathCheck.announceBroken();
                sink.accept(new Read(null, GpuPaths.ReadPath.NONE));
            }
        }
    }

    private static void gpu(GpuTexture source, int x, int y, int width, int height, int bytes, String what, Consumer<Read> sink) {
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(() -> "propwash read " + what, USAGE, bytes);
        try {
            device.createCommandEncoder().copyTextureToBuffer(source, buffer, 0L, () -> {
                byte[] data = null;
                try {
                    if (!buffer.isClosed()) {
                        try (GpuBufferSlice.MappedView view = buffer.map(0L, bytes, true, false)) {
                            data = new byte[bytes];
                            view.data().get(0, data);
                        }
                    }
                } catch (RuntimeException exception) {
                    data = null;
                } finally {
                    if (!buffer.isClosed()) {
                        buffer.close();
                    }
                }
                sink.accept(new Read(data, GpuPaths.ReadPath.GPU));
            }, 0, x, y, width, height);
        } catch (RuntimeException exception) {
            buffer.close();
            throw exception;
        }
    }

    private static byte @Nullable [] classic(GpuTexture source, int x, int y, int width, int height, int bytes, String what) {
        ClassicReadback reader = new ClassicReadback();
        ByteBuffer out = MemoryUtil.memAlloc(Math.max(bytes, width * height * 4));
        try {
            classicReads++;
            if (!reader.read(source, x, y, width, height, out)) {
                warn(what, "Ersatzweg glReadPixels: " + reader.problem());
                return null;
            }
            byte[] data = new byte[bytes];
            out.get(0, data);
            return data;
        } finally {
            reader.close();
            MemoryUtil.memFree(out);
        }
    }

    private static void warn(String what, String problem) {
        if (WARNED.add(what + "|" + problem)) {
            Constants.LOG.warn("Rücklesen {}: {}", what, problem);
        }
    }

    public record Read(byte @Nullable [] data, GpuPaths.ReadPath path) {
        public boolean ok() {
            return this.data != null;
        }
    }
}
