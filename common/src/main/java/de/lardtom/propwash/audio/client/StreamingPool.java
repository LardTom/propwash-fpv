package de.lardtom.propwash.audio.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StreamingPool {
    public static final int RESERVED_CHANNELS = 2;
    public static final int UNKNOWN = -1;

    private static final Pattern STREAMING = Pattern.compile("\\+\\s*(\\d+)\\s*/\\s*(\\d+)");

    private StreamingPool() {
    }

    public static int parseMax(String channelDebug) {
        if (channelDebug == null) {
            return UNKNOWN;
        }
        Matcher matcher = STREAMING.matcher(channelDebug);
        if (!matcher.find()) {
            return UNKNOWN;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }

    public static int synthCap(int streamingMax) {
        if (streamingMax <= 0) {
            return AudioConfig.MAX_VOICES;
        }
        return Math.max(1, streamingMax - RESERVED_CHANNELS);
    }
}
