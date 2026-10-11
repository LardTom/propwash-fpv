package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.MotorSynth;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

public final class MotorStream implements AudioStream {
    public static final int CHUNK_FRAMES = 720;

    private static final AudioFormat FORMAT = new AudioFormat(MotorSynth.SAMPLE_RATE, 16, 1, true, false);

    private final MotorSynth synth;
    private final int sourceId;
    private final short[] pcm = new short[CHUNK_FRAMES];
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK_FRAMES * 2).order(ByteOrder.nativeOrder());
    private final ShortBuffer shorts = this.buffer.asShortBuffer();
    private volatile boolean closed;
    private volatile boolean keepPlaying = true;
    private volatile long chunksRead;
    private volatile boolean failed;

    public MotorStream(MotorSynth synth) {
        this(synth, -1);
    }

    public MotorStream(MotorSynth synth, int sourceId) {
        this.synth = synth;
        this.sourceId = sourceId;
    }

    public int sourceId() {
        return this.sourceId;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int expectedSize) {
        try {
            if (this.closed || this.failed) {
                Arrays.fill(this.pcm, (short) 0);
            } else {
                this.synth.render(this.pcm, CHUNK_FRAMES);
                MotorAudioTap.publish(this.sourceId, this.pcm, CHUNK_FRAMES);
            }
        } catch (RuntimeException e) {
            this.failed = true;
            Arrays.fill(this.pcm, (short) 0);
        }
        this.shorts.clear();
        this.shorts.put(this.pcm, 0, CHUNK_FRAMES);
        this.buffer.clear();
        this.buffer.limit(CHUNK_FRAMES * 2);
        this.chunksRead = this.chunksRead + 1;
        return this.buffer;
    }

    @Override
    public void close() {
        this.closed = true;
        this.keepPlaying = false;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public boolean failed() {
        return this.failed;
    }

    public boolean keepPlaying() {
        return this.keepPlaying && !this.closed;
    }

    public void release() {
        this.keepPlaying = false;
    }

    public long chunksRead() {
        return this.chunksRead;
    }
}
