package de.lardtom.propwash.video.record;

import de.lardtom.propwash.video.record.aac.AacEncoder;
import de.lardtom.propwash.video.record.mp4.Mp4Writer;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class AudioLane {
    public static final int RATE = AacEncoder.SAMPLE_RATE;

    private final ConcurrentLinkedQueue<short[]> incoming = new ConcurrentLinkedQueue<>();
    private final AtomicLong queued = new AtomicLong();
    private final AacEncoder encoder;
    private final float[] frame = new float[AacEncoder.FRAME];
    private int filled;
    private long drop = -1L;
    private long accepted;
    private long frames;
    private long peak;

    public AudioLane(int bitrate) {
        this.encoder = new AacEncoder(RATE, bitrate);
    }

    public int bitrate() {
        return this.encoder.bitrate();
    }

    public long accepted() {
        return this.accepted;
    }

    public long frames() {
        return this.frames;
    }

    public long queuedSamples() {
        return this.queued.get();
    }

    public long peak() {
        return this.peak;
    }

    public void submit(short[] pcm, int offset, int count) {
        if (count <= 0) {
            return;
        }
        short[] copy = new short[count];
        System.arraycopy(pcm, offset, copy, 0, count);
        this.incoming.add(copy);
        this.queued.addAndGet(count);
    }

    public void submitSilence(int count) {
        if (count <= 0) {
            return;
        }
        this.incoming.add(new short[count]);
        this.queued.addAndGet(count);
    }

    public boolean started() {
        return this.drop >= 0L;
    }

    public void start(long firstSlot, int fps) {
        if (this.drop < 0L) {
            this.drop = Math.max(0L, firstSlot) * RATE / Math.max(1, fps);
        }
    }

    public void pump(Mp4Writer writer) throws IOException {
        if (this.drop < 0L) {
            return;
        }
        short[] chunk;
        while ((chunk = this.incoming.poll()) != null) {
            this.queued.addAndGet(-chunk.length);
            int at = 0;
            if (this.drop > 0L) {
                int skipped = (int) Math.min(this.drop, chunk.length);
                this.drop -= skipped;
                at = skipped;
            }
            for (int i = at; i < chunk.length; i++) {
                this.feed(writer, chunk[i]);
                this.peak = Math.max(this.peak, Math.abs(chunk[i]));
            }
        }
    }

    public void finish(Mp4Writer writer, long targetSamples) throws IOException {
        if (this.drop < 0L) {
            this.drop = 0L;
        }
        this.pump(writer);
        while (this.accepted < targetSamples) {
            this.feed(writer, 0.0F);
        }
        if (this.filled > 0) {
            while (this.filled > 0) {
                this.feed(writer, 0.0F);
            }
        }
        writer.writeAudioSample(this.encoder.flush(), AacEncoder.FRAME);
        this.frames++;
        writer.setAudioPresentation(AacEncoder.PRIMING, targetSamples);
    }

    private void feed(Mp4Writer writer, float sample) throws IOException {
        this.frame[this.filled++] = sample;
        this.accepted++;
        if (this.filled == AacEncoder.FRAME) {
            writer.writeAudioSample(this.encoder.encode(this.frame, 0), AacEncoder.FRAME);
            this.frames++;
            this.filled = 0;
        }
    }
}
