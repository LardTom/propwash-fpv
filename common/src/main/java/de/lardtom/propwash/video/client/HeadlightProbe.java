package de.lardtom.propwash.video.client;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import de.lardtom.propwash.gpucompat.GpuPaths;
import de.lardtom.propwash.gpucompat.client.GuardedReadback;
import de.lardtom.propwash.video.HeadlightCheck;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

final class HeadlightProbe {
    private static final String WHAT = "Scheinwerfer-Prüfung";
    private static GpuPaths.ReadPath lastPath = GpuPaths.ReadPath.GPU;

    private final GpuTexture source;
    private final int width;
    private final int y;
    private final int rows;
    private final int bytes;
    private final Consumer<HeadlightCheck.@Nullable Result> sink;
    private byte @Nullable [] beforeData;
    private byte @Nullable [] afterData;
    private int pending = 2;
    private boolean broken;

    private HeadlightProbe(GpuTexture source, int width, int y, int rows, Consumer<HeadlightCheck.@Nullable Result> sink) {
        this.source = source;
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
        HeadlightProbe probe = new HeadlightProbe(source, width, y, rows, sink);
        probe.copy(true);
        return probe;
    }

    static GpuPaths.ReadPath lastPath() {
        return lastPath;
    }

    void finish() {
        this.copy(false);
    }

    void cancel() {
        this.broken = true;
        this.arrived(false, null);
    }

    private void copy(boolean first) {
        GuardedReadback.read(this.source, 0, this.y, this.width, this.rows, WHAT, read -> {
            lastPath = read.path();
            byte[] data = read.data();
            this.arrived(first, !this.broken && data != null && data.length >= this.bytes ? data : null);
        });
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
