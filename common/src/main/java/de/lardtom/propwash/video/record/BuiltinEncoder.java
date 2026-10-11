package de.lardtom.propwash.video.record;

import de.lardtom.propwash.video.record.h264.EncodedFrame;
import de.lardtom.propwash.video.record.h264.EncoderSettings;
import de.lardtom.propwash.video.record.h264.H264Encoder;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class BuiltinEncoder implements FrameEncoder {
    public static final String LABEL = "Propwash H.264";
    public static final Factory FACTORY = BuiltinEncoder::new;

    private final H264Encoder encoder;

    public BuiltinEncoder(RecordingFormat format, RecordingQuality quality, int threads) {
        this.encoder = new H264Encoder(format.width(), format.height(), format.fps(),
                EncoderSettings.of(threads, quality.maxBitrate(format), format.fps(), quality.qp()));
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public boolean hardware() {
        return false;
    }

    @Override
    public byte[] sps() {
        return this.encoder.sps();
    }

    @Override
    public byte[] pps() {
        return this.encoder.pps();
    }

    @Override
    public void encode(ByteBuffer frame, int preset, SampleSink sink) throws IOException {
        EncodedFrame encoded = this.encoder.encode(frame, preset);
        sink.accept(encoded.toSample(), encoded.keyframe());
    }

    @Override
    public void repeat(SampleSink sink) throws IOException {
        if (this.encoder.frames() == 0L) {
            return;
        }
        sink.accept(this.encoder.repeat().toSample(), false);
    }

    @Override
    public void finish(SampleSink sink) {
    }

    @Override
    public void close() {
        this.encoder.close();
    }
}
