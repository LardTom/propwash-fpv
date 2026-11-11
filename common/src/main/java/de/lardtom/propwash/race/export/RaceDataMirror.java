package de.lardtom.propwash.race.export;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.race.CourseId;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.zone.ZoneData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RaceDataMirror {
    public static final String FOLDER = "propwash/race-data";
    public static final long QUIET_MILLIS = 2_000L;
    public static final long MAX_DELAY_MILLIS = 10_000L;
    public static final long ZONE_CHECK_MILLIS = 60_000L;
    private static final long STOP_WAIT_MILLIS = 5_000L;
    private static @Nullable RaceDataMirror instance;

    private final MinecraftServer server;
    private final CourseRegistry registry;
    private final Path root;
    private final ScheduledExecutorService worker;
    private final Set<Integer> dirty = new LinkedHashSet<>();
    private final Set<UUID> removed = new LinkedHashSet<>();
    private final Map<String, Long> written = new HashMap<>();
    private boolean everything = true;
    private boolean coursesDirty = true;
    private @Nullable ScheduledFuture<?> pending;
    private long firstMark;
    private int zoneVersion = -1;
    private boolean creativeWorld;
    private int writes;

    private record Write(Path path, @Nullable String content) {
    }

    private RaceDataMirror(MinecraftServer server, CourseRegistry registry, Path root) {
        this.server = server;
        this.registry = registry;
        this.root = root;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Propwash-Renndaten");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.worker = executor;
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).normalize().resolve(FOLDER);
    }

    public static synchronized void start(MinecraftServer server, CourseRegistry registry) {
        RaceDataMirror old = instance;
        if (old != null) {
            old.shutdown();
        }
        RaceDataMirror mirror = new RaceDataMirror(server, registry, root(server));
        instance = mirror;
        mirror.schedule();
        mirror.worker.scheduleWithFixedDelay(() -> server.execute(mirror::checkZones), ZONE_CHECK_MILLIS, ZONE_CHECK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public static synchronized void stop(@Nullable CourseRegistry registry) {
        RaceDataMirror mirror = instance;
        instance = null;
        if (mirror == null) {
            return;
        }
        if (registry == mirror.registry) {
            mirror.everything = true;
            mirror.coursesDirty = true;
            mirror.flush();
        }
        mirror.shutdown();
    }

    public static @Nullable RaceDataMirror get() {
        return instance;
    }

    public static void touch(CourseRegistry registry, int courseId) {
        RaceDataMirror mirror = instance;
        if (mirror != null && mirror.registry == registry && mirror.server.isSameThread()) {
            mirror.mark(courseId);
        }
    }

    public static void touchAll(CourseRegistry registry) {
        RaceDataMirror mirror = instance;
        if (mirror != null && mirror.registry == registry && mirror.server.isSameThread()) {
            for (CourseRegistry.Course course : registry.courses()) {
                mirror.dirty.add(course.id);
            }
            mirror.coursesDirty = true;
            mirror.schedule();
        }
    }

    public static void deleted(CourseRegistry registry, UUID key) {
        RaceDataMirror mirror = instance;
        if (mirror != null && mirror.registry == registry && mirror.server.isSameThread()) {
            mirror.removed.add(key);
            mirror.coursesDirty = true;
            mirror.schedule();
        }
    }

    public Path root() {
        return this.root;
    }

    public int writes() {
        return this.writes;
    }

    private void mark(int courseId) {
        this.dirty.add(courseId);
        this.coursesDirty = true;
        this.schedule();
    }

    private void schedule() {
        long now = System.currentTimeMillis();
        ScheduledFuture<?> current = this.pending;
        if (current == null || current.isDone()) {
            this.firstMark = now;
        } else if (now - this.firstMark >= MAX_DELAY_MILLIS - QUIET_MILLIS) {
            return;
        } else {
            current.cancel(false);
        }
        long delay = Math.min(QUIET_MILLIS, Math.max(0L, this.firstMark + MAX_DELAY_MILLIS - now));
        try {
            this.pending = this.worker.schedule(() -> this.server.execute(this::flush), delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            this.pending = null;
        }
    }

    private void checkZones() {
        if (instance != this) {
            return;
        }
        int version = zoneVersion(this.server);
        if (version != this.zoneVersion) {
            if (this.zoneVersion >= 0) {
                this.coursesDirty = true;
                this.schedule();
            }
            this.zoneVersion = version;
        }
        boolean creative = RaceSnapshots.creativeFcWorld();
        if (creative != this.creativeWorld) {
            this.creativeWorld = creative;
            touchAll(this.registry);
        }
    }

    public void flushNow() {
        if (!this.server.isSameThread()) {
            throw new IllegalStateException("Renndaten-Spiegel nur auf dem Server-Thread schreiben");
        }
        this.flush();
        try {
            this.worker.submit(() -> {
            }).get(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Constants.LOG.warn("Renndaten-Spiegel: Warten auf das Schreiben abgebrochen", e);
        }
    }

    private void flush() {
        ScheduledFuture<?> current = this.pending;
        if (current != null) {
            current.cancel(false);
            this.pending = null;
        }
        if (!this.everything && !this.coursesDirty && this.dirty.isEmpty() && this.removed.isEmpty()) {
            return;
        }
        this.zoneVersion = zoneVersion(this.server);
        this.creativeWorld = RaceSnapshots.creativeFcWorld();
        List<Write> writes = new ArrayList<>();
        List<CourseInfo> infos = new ArrayList<>();
        Map<Integer, CourseInfo> byNumber = new HashMap<>();
        for (CourseRegistry.Course course : this.registry.courses()) {
            CourseInfo info = RaceDataEvents.info(this.registry, course);
            infos.add(info);
            byNumber.put(course.id, info);
        }
        if (this.everything || this.coursesDirty) {
            writes.add(new Write(this.root.resolve(RaceDataJson.COURSES), RaceDataJson.courses(this.registry, infos)));
        }
        Set<Integer> boards = this.everything ? byNumber.keySet() : this.dirty;
        for (int id : boards) {
            CourseRegistry.Course course = this.registry.course(id);
            CourseInfo info = byNumber.get(id);
            if (course != null && info != null) {
                writes.add(new Write(this.root.resolve(RaceDataJson.leaderboardPath(info)), RaceDataJson.leaderboard(info, course, this.registry.records())));
            }
        }
        Set<String> keep = new HashSet<>();
        for (CourseInfo info : infos) {
            keep.add(info.id() + ".json");
        }
        for (UUID key : this.removed) {
            if (!keep.contains(key + ".json")) {
                writes.add(new Write(this.root.resolve(RaceDataJson.LEADERBOARDS).resolve(key + ".json"), null));
            }
        }
        boolean cleanup = this.everything;
        this.everything = false;
        this.coursesDirty = false;
        this.dirty.clear();
        this.removed.clear();
        List<Write> changed = new ArrayList<>();
        for (Write write : writes) {
            String name = write.path().toString();
            if (write.content() == null) {
                this.written.remove(name);
                changed.add(write);
                continue;
            }
            long fingerprint = ((long) write.content().hashCode() << 32) ^ write.content().length();
            Long before = this.written.put(name, fingerprint);
            if (before == null || before != fingerprint) {
                changed.add(write);
            }
        }
        if (changed.isEmpty() && !cleanup) {
            return;
        }
        Path folder = this.root.resolve(RaceDataJson.LEADERBOARDS);
        try {
            this.worker.execute(() -> this.perform(changed, cleanup ? keep : null, folder));
        } catch (RuntimeException e) {
            this.perform(changed, cleanup ? keep : null, folder);
        }
    }

    private void perform(List<Write> writes, @Nullable Set<String> keep, Path folder) {
        for (Write write : writes) {
            try {
                if (write.content() == null) {
                    Files.deleteIfExists(write.path());
                } else {
                    writeAtomic(write.path(), write.content());
                }
                this.writes++;
            } catch (IOException e) {
                Constants.LOG.error("Renndaten-Spiegel: {} nicht geschrieben", write.path(), e);
            }
        }
        if (keep != null && Files.isDirectory(folder)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.json")) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    if (CourseId.parse(name.substring(0, name.length() - 5)) != null && !keep.contains(name)) {
                        Files.deleteIfExists(file);
                    }
                }
            } catch (IOException e) {
                Constants.LOG.error("Renndaten-Spiegel: alte Bestenlisten in {} nicht aufgeräumt", folder, e);
            }
        }
    }

    private static int zoneVersion(MinecraftServer server) {
        try {
            return ZoneData.get(server).version();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static void writeAtomic(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void shutdown() {
        this.worker.shutdown();
        try {
            if (!this.worker.awaitTermination(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                Constants.LOG.warn("Renndaten-Spiegel: Schreiben beim Stoppen nicht fertig");
                this.worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
