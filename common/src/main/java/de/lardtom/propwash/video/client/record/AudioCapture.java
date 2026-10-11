package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.audio.client.MotorAudioTap;

final class AudioCapture implements MotorAudioTap.Listener {
    static final int RATE = MotorAudioTap.SAMPLE_RATE;
    private static final int CAPACITY = RATE * 2;

    private final short[] ring = new short[CAPACITY];
    private volatile int source = -1;
    private volatile long written;
    private long read;
    private long overruns;

    void listen(int sourceId) {
        this.source = -1;
        this.read = this.written;
        this.source = sourceId;
        MotorAudioTap.listen(this);
    }

    void stop() {
        this.source = -1;
        MotorAudioTap.release(this);
    }

    boolean active() {
        return this.source >= 0;
    }

    long overruns() {
        return this.overruns;
    }

    @Override
    public void onPcm(int sourceId, short[] pcm, int frames) {
        if (sourceId != this.source) {
            return;
        }
        long at = this.written;
        for (int i = 0; i < frames; i++) {
            this.ring[(int) ((at + i) % CAPACITY)] = pcm[i];
        }
        this.written = at + frames;
    }

    int drain(short[] out) {
        long end = this.written;
        if (end - this.read > CAPACITY) {
            this.read = end - CAPACITY;
            this.overruns++;
        }
        int count = (int) Math.min(out.length, end - this.read);
        for (int i = 0; i < count; i++) {
            out[i] = this.ring[(int) ((this.read + i) % CAPACITY)];
        }
        this.read += count;
        return count;
    }
}
