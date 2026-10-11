package de.lardtom.propwash.video.record;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ClipInfo(Path file, long sizeBytes, long modifiedMillis, @Nullable LocalDateTime recordedAt, double seconds, int width, int height,
                       double fps, boolean audio, boolean readable) {
    private static final Pattern NAME = Pattern.compile("propwash_(\\d{4})-(\\d{2})-(\\d{2})_(\\d{2})-(\\d{2})-(\\d{2})(?:_\\d+)?\\.mp4");
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    public String fileName() {
        return this.file.getFileName().toString();
    }

    public LocalDateTime when(ZoneId zone) {
        if (this.recordedAt != null) {
            return this.recordedAt;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(this.modifiedMillis), zone);
    }

    public String format() {
        if (this.height <= 0) {
            return "";
        }
        long rate = Math.round(this.fps);
        return rate > 0L ? this.height + "p" + rate : this.height + "p";
    }

    public String duration() {
        return duration(Math.round(this.seconds));
    }

    public String size() {
        return size(this.sizeBytes);
    }

    public static @Nullable LocalDateTime parseRecordedAt(String fileName) {
        Matcher matcher = NAME.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return LocalDateTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    public static String duration(long seconds) {
        long s = Math.max(0L, seconds);
        if (s >= 3600L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L);
        }
        return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
    }

    public static String size(long bytes) {
        long b = Math.max(0L, bytes);
        if (b < MB) {
            return String.format(Locale.ROOT, "%d KB", (b + KB - 1L) / KB);
        }
        if (b < GB) {
            return String.format(Locale.ROOT, "%.1f MB", b / (double) MB);
        }
        return String.format(Locale.ROOT, "%.2f GB", b / (double) GB);
    }
}
