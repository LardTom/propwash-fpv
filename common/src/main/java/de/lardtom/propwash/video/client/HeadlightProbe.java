package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import de.lardtom.propwash.video.HeadlightCheck;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

final class HeadlightProbe {
    private static final int USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;

    private final GpuTexture source;
    private final GpuBuffer before;
    private final GpuBuffer after;
    private final int width;
    private final int y;
    private final int rows;
    private final int bytes;
    private final Consumer<HeadlightCheck.@Nullable Result> sink;
    private byte @Nullable [] beforeData;
    private byte @Nullable [] afterData;
    private int pending = 2;
    private boolean broken;

    private HeadlightProbe(GpuTexture source, GpuBuffer before, GpuBuffer after, int width, int y, int rows,
                           Consumer<HeadlightCheck.@Nullable Result> sink) {
        this.source = source;
        this.before = before;
        this.after = after;
        this.width = width;
        this.y = y;
        this.rows = rows;
        this.bytes = width * rows * HeadlightCheck.BYTES_PER_PIXEL;
        this.sink = sink;
    }

    static @Nullable HeadlightProbe begin(GpuTexture source, int width, int height, int index, Consumer<HeadlightCheck.@Nullable Result> sink) {
        if (source.getFormat() != GpuFormat.RGBA8_UNORM || source.getFormat().blockSize() != HeadlightCheck.BYTES_PER_PIXEL || width <= 0
                || height <= 0) {
            return null;
        }
        int rows = HeadlightCheck.bandRows(height);
        int y = HeadlightCheck.bandStart(height, index);
        long size = (long) width * rows * HeadlightCheck.BYTES_PER_PIXEL;
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer before = device.createBuffer(() -> "propwash headlight check before", USAGE, size);
        GpuBuffer after = device.createBuffer(() -> "propwash headlight check after", USAGE, size);
        HeadlightProbe probe = new HeadlightProbe(source, before, after, width, y, rows, sink);
        try {
            probe.copy(before, true);
        } catch (RuntimeException exception) {
            before.close();
            after.close();
            throw exception;
        }
        return probe;
    }

    void finish() {
        this.copy(this.after, false);
    }

    void cancel() {
        this.broken = true;
        if (!this.after.isClosed()) {
            this.after.close();
        }
        this.arrived(false, null);
    }

    private void copy(GpuBuffer target, boolean first) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(this.source, target, 0L, () -> this.read(target, first), 0, 0,
                this.y, this.width, this.rows);
    }

    private void read(GpuBuffer target, boolean first) {
        byte[] data = null;
        try {
            if (!this.broken && !target.isClosed()) {
                try (GpuBufferSlice.MappedView view = target.map(0L, this.bytes, true, false)) {
                    data = new byte[this.bytes];
                    view.data().get(0, data);
                }
            }
        } catch (RuntimeException exception) {
            this.broken = true;
            data = null;
        } finally {
            if (!target.isClosed()) {
                target.close();
            }
        }
        this.arrived(first, data);
    }

    private void arrived(boolean first, byte @Nullable [] data) {
        if (first) {
            this.beforeData = data;
        } else {
            this.afterData = data;
        }
        this.pending--;
        if (this.pending > 0) {
            return;
        }
        byte[] in = this.beforeData;
        byte[] out = this.afterData;
        if (this.broken || in == null || out == null) {
            this.sink.accept(null);
            return;
        }
        this.sink.accept(HeadlightCheck.compare(in, out, this.width * this.rows));
    }
}
