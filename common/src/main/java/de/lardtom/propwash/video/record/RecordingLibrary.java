package de.lardtom.propwash.video.record;

import de.lardtom.propwash.video.record.mp4.Mp4Info;
import de.lardtom.propwash.video.record.mp4.Mp4Reader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecordingLibrary {
    public static final String THUMBNAILS = ".thumbs";
    public static final String EXTENSION = ".mp4";
    public static final String THUMBNAIL_EXTENSION = ".png";
    private static final String PART = ".part";

    private final Path directory;
    private final Executor executor;
    private final Map<Path, ClipInfo> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final AtomicBoolean again = new AtomicBoolean();
    private volatile List<ClipInfo> clips = List.of();
    private volatile long revision;
    private volatile boolean scanned;

    public RecordingLibrary(Path directory, Executor executor) {
        this.directory = directory;
        this.executor = executor;
    }

    public Path directory() {
        return this.directory;
    }

    public List<ClipInfo> clips() {
        return this.clips;
    }

    public long revision() {
        return this.revision;
    }

    public boolean scanned() {
        return this.scanned;
    }

    public boolean scanning() {
        return this.scanning.get();
    }

    public void refresh() {
        if (!this.scanning.compareAndSet(false, true)) {
            this.again.set(true);
            return;
        }
        try {
            this.executor.execute(this::scanLoop);
        } catch (RuntimeException e) {
            this.scanning.set(false);
            throw e;
        }
    }

    private void scanLoop() {
        try {
            do {
                this.again.set(false);
                List<ClipInfo> found = new ArrayList<>(this.scanNow());
                found.removeIf(clip -> !Files.exists(clip.file()));
                this.publish(List.copyOf(found));
            } while (this.again.get());
        } finally {
            this.scanning.set(false);
        }
    }

    private void publish(List<ClipInfo> found) {
        this.clips = found;
        this.revision++;
        this.scanned = true;
    }

    public List<ClipInfo> scanNow() {
        if (!Files.isDirectory(this.directory)) {
            this.cache.clear();
            return List.of();
        }
        List<ClipInfo> found = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory, "*" + EXTENSION)) {
            for (Path file : stream) {
                ClipInfo info = this.describe(file);
                if (info != null) {
                    found.add(info);
                    seen.add(file);
                }
            }
        } catch (IOException ignored) {
        }
        this.cache.keySet().retainAll(seen);
        found.sort(Comparator.comparingLong(ClipInfo::modifiedMillis).reversed().thenComparing(ClipInfo::fileName));
        return List.copyOf(found);
    }

    private @Nullable ClipInfo describe(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            ClipInfo cached = this.cache.get(file);
            if (cached != null && cached.sizeBytes() == size && cached.modifiedMillis() == modified) {
                return cached;
            }
            ClipInfo info = read(file, size, modified);
            this.cache.put(file, info);
            return info;
        } catch (IOException e) {
            return null;
        }
    }

    static ClipInfo read(Path file, long size, long modified) {
        LocalDateTime at = ClipInfo.parseRecordedAt(file.getFileName().toString());
        try {
            Mp4Info info = Mp4Reader.read(file);
            return new ClipInfo(file, size, modified, at, info.durationSeconds(), info.width(), info.height(), info.frameRate(), info.hasAudio(), true);
        } catch (IOException | RuntimeException e) {
            return new ClipInfo(file, size, modified, at, 0.0, 0, 0, 0.0, false, false);
        }
    }

    public void forget(Path clip) {
        this.cache.remove(clip);
        List<ClipInfo> kept = new ArrayList<>(this.clips);
        if (kept.removeIf(info -> info.file().equals(clip))) {
            this.publish(List.copyOf(kept));
        }
    }

    public boolean delete(Path clip) throws IOException {
        Path normalized = clip.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !parent.equals(this.directory.toAbsolutePath().normalize()) || !normalized.getFileName().toString().endsWith(EXTENSION)) {
            throw new IOException("not a recording: " + clip);
        }
        Files.deleteIfExists(thumbnailOf(normalized));
        boolean deleted = Files.deleteIfExists(normalized);
        this.forget(clip);
        return deleted;
    }

    public static Path thumbnailOf(Path clip) {
        String name = clip.getFileName().toString();
        String base = name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
        return clip.resolveSibling(THUMBNAILS).resolve(base + THUMBNAIL_EXTENSION);
    }

    public static int pruneThumbnails(Path directory) {
        Path thumbnails = directory.resolve(THUMBNAILS);
        if (!Files.isDirectory(thumbnails)) {
            return 0;
        }
        int removed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(thumbnails, "*" + THUMBNAIL_EXTENSION)) {
            for (Path thumbnail : stream) {
                String name = thumbnail.getFileName().toString();
                String clip = name.substring(0, name.length() - THUMBNAIL_EXTENSION.length()) + EXTENSION;
                if (!Files.exists(directory.resolve(clip)) && !Files.exists(directory.resolve(clip + PART))) {
                    Files.deleteIfExists(thumbnail);
                    removed++;
                }
            }
        } catch (IOException ignored) {
        }
        return removed;
    }
}
