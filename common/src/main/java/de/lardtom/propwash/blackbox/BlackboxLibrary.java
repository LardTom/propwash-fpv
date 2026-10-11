package de.lardtom.propwash.blackbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class BlackboxLibrary {
    public static final String EXTENSION = ".bbl";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private final Path directory;

    public BlackboxLibrary(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return this.directory;
    }

    public record LogFile(Path path, String name, long bytes, long modifiedMillis, long createdMillis) {
        public long durationMillis() {
            return Math.max(0L, this.modifiedMillis - this.createdMillis);
        }
    }

    public List<LogFile> list() {
        List<LogFile> out = new ArrayList<>();
        if (!Files.isDirectory(this.directory)) {
            return out;
        }
        try (Stream<Path> files = Files.list(this.directory)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                String name = path.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(EXTENSION) || !Files.isRegularFile(path)) {
                    continue;
                }
                try {
                    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                    long modified = attributes.lastModifiedTime().toMillis();
                    long started = startedMillis(name, attributes.creationTime().toMillis());
                    out.add(new LogFile(path, name, attributes.size(), modified, Math.min(started, modified)));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparingLong(LogFile::modifiedMillis).reversed().thenComparing(LogFile::name));
        return out;
    }

    public long totalBytes() {
        long total = 0L;
        for (LogFile file : this.list()) {
            total += file.bytes();
        }
        return total;
    }

    public int pruneTo(long maxBytes, Path keep) {
        List<LogFile> files = this.list();
        long total = 0L;
        for (LogFile file : files) {
            total += file.bytes();
        }
        int deleted = 0;
        for (int i = files.size() - 1; i >= 0 && total > maxBytes; i--) {
            LogFile oldest = files.get(i);
            if (keep != null && oldest.path().equals(keep)) {
                continue;
            }
            try {
                if (Files.deleteIfExists(oldest.path())) {
                    total -= oldest.bytes();
                    deleted++;
                }
            } catch (IOException ignored) {
            }
        }
        return deleted;
    }

    public boolean delete(Path path) {
        if (path == null || !path.toAbsolutePath().normalize().startsWith(this.directory.toAbsolutePath().normalize())) {
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    static long startedMillis(String name, long fallback) {
        if (name.length() < 28 || !name.startsWith("propwash_")) {
            return fallback;
        }
        try {
            LocalDateTime time = LocalDateTime.parse(name.substring(9, 28), STAMP);
            return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    public static String fileName(String startedAt, String craftName) {
        String stamp = startedAt.length() >= 19 ? startedAt.substring(0, 19).replace('T', '_').replace(':', '-') : startedAt;
        StringBuilder sb = new StringBuilder("propwash_").append(stamp);
        String craft = sanitize(craftName);
        if (!craft.isEmpty()) {
            sb.append('_').append(craft);
        }
        return sb.append(EXTENSION).toString();
    }

    static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            for (int i = 0; i < name.length() && sb.length() < 24; i++) {
                char c = name.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                    sb.append(c);
                } else if (c == ' ' && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
            }
        }
        while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    public Path uniquePath(String fileName) {
        Path path = this.directory.resolve(fileName);
        if (!Files.exists(path)) {
            return path;
        }
        String base = fileName.substring(0, fileName.length() - EXTENSION.length());
        for (int i = 2; i < 1000; i++) {
            Path candidate = this.directory.resolve(base + "_" + i + EXTENSION);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return this.directory.resolve(base + "_" + System.nanoTime() + EXTENSION);
    }
}
