package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class VtxSettings {
    public static final String CHANNEL = "vtx.channel";
    public static final String MANUAL = "vtx.manual";
    public static final List<String> KEYS = List.of(CHANNEL, MANUAL);

    private VtxSettings() {
    }

    public static boolean isKey(String key) {
        return CHANNEL.equals(key) || MANUAL.equals(key);
    }

    public static float sanitize(String key, float value) {
        if (!Float.isFinite(value)) {
            return MANUAL.equals(key) ? 0.0F : VideoChannels.UNASSIGNED;
        }
        if (MANUAL.equals(key)) {
            return value >= 0.5F ? 1.0F : 0.0F;
        }
        return Math.max(VideoChannels.UNASSIGNED, Math.min(VideoChannels.MAX_COUNT - 1, Math.round(value)));
    }

    public static int channel(Map<String, Float> tune, VideoSystem system) {
        Float value = tune.get(CHANNEL);
        if (value == null) {
            return VideoChannels.UNASSIGNED;
        }
        return VideoChannels.clamp(system, (int) sanitize(CHANNEL, value));
    }

    public static boolean manual(Map<String, Float> tune) {
        Float value = tune.get(MANUAL);
        return value != null && sanitize(MANUAL, value) >= 0.5F && tune.containsKey(CHANNEL);
    }

    public static String label(Map<String, Float> tune, VideoSystem system) {
        return VideoChannels.label(system, channel(tune, system));
    }

    public static Map<String, Float> with(Map<String, Float> tune, int channel, boolean manual) {
        TreeMap<String, Float> out = new TreeMap<>(tune);
        if (channel < 0) {
            out.remove(CHANNEL);
            out.remove(MANUAL);
            return out;
        }
        out.put(CHANNEL, (float) channel);
        if (manual) {
            out.put(MANUAL, 1.0F);
        } else {
            out.remove(MANUAL);
        }
        return out;
    }

    public static Map<String, Float> auto(Map<String, Float> tune) {
        TreeMap<String, Float> out = new TreeMap<>(tune);
        out.remove(MANUAL);
        return out;
    }

    public static void copyClean(Map<String, Float> from, Map<String, Float> to) {
        for (String key : KEYS) {
            Float value = from.get(key);
            if (value == null || !Float.isFinite(value)) {
                continue;
            }
            float clean = sanitize(key, value);
            if (CHANNEL.equals(key) && clean < 0.0F) {
                continue;
            }
            if (MANUAL.equals(key) && clean < 0.5F) {
                continue;
            }
            to.put(key, clean);
        }
    }
}
