package de.lardtom.propwash.video.record;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface FrameEncoder extends AutoCloseable {
    String label();

    boolean hardware();

    byte[] sps();

    byte[] pps();

    void encode(ByteBuffer frame, int preset, SampleSink sink) throws IOException;

    void repeat(SampleSink sink) throws IOException;

    void finish(SampleSink sink) throws IOException;

    @Override
    void close();

    @FunctionalInterface
    interface SampleSink {
        void accept(byte[] sample, boolean sync) throws IOException;
    }

    @FunctionalInterface
    interface Factory {
        FrameEncoder create(RecordingFormat format, RecordingQuality quality, int threads) throws IOException;
    }
}
