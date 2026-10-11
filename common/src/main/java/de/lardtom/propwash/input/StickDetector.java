package de.lardtom.propwash.input;

public sealed interface StickDetector permits ChannelDetector, ThrottleDetector {
    static StickDetector forChannel(StickChannel channel, float[] baseline, boolean[] excluded) {
        return forChannel(channel, baseline, excluded, null);
    }

    static StickDetector forChannel(StickChannel channel, float[] baseline, boolean[] excluded, ChannelMapping expected) {
        if (channel.centered()) {
            return ChannelDetector.centered(baseline, excluded);
        }
        if (expected != null && expected.assigned()) {
            return new ThrottleDetector(baseline, excluded, expected.axis, expected.invert);
        }
        return new ThrottleDetector(baseline, excluded);
    }

    Detection update(float[] axes, long nanos);

    int leadingAxis();

    float progress(long nanos);

    DetectionStage stage();

    record Detection(int axis, boolean invert) {
        public ChannelMapping toMapping(ChannelMapping previous) {
            ChannelMapping mapping = previous != null && previous.axis == this.axis ? previous.copy() : new ChannelMapping();
            mapping.axis = this.axis;
            mapping.invert = this.invert;
            return mapping;
        }
    }
}
