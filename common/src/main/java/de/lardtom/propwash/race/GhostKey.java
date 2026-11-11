package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public record GhostKey(UUID player, String frame, long nanos, long dateMillis) {
    public static final String EXTENSION = ".pwghost";
    private static final int UUID_LENGTH = 36;

    public GhostKey {
        frame = frame == null ? "" : frame.length() > Leaderboard.MAX_FRAME ? frame.substring(0, Leaderboard.MAX_FRAME) : frame;
    }

    public static GhostKey of(Leaderboard.Entry entry) {
        return new GhostKey(entry.player(), entry.frame(), entry.nanos(), entry.dateMillis());
    }

    public boolean matches(Leaderboard.Entry entry) {
        return entry.player().equals(this.player) && entry.frame().equals(this.frame) && entry.nanos() == this.nanos
                && entry.dateMillis() == this.dateMillis;
    }

    public String fileName() {
        return this.player + "_" + frameText(this.frame) + "_" + Long.toHexString(this.nanos) + "_" + Long.toHexString(this.dateMillis) + EXTENSION;
    }

    public static String frameText(String frame) {
        if (frame.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder(frame.length());
        for (char c : frame.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-' ? c : c == ':' ? '~' : '+');
        }
        return out.toString();
    }

    public static @Nullable UUID playerOf(String fileName) {
        if (fileName.length() <= UUID_LENGTH || !fileName.endsWith(EXTENSION) || fileName.charAt(UUID_LENGTH) != '_') {
            return null;
        }
        try {
            return UUID.fromString(fileName.substring(0, UUID_LENGTH));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
