package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

public final class EntryGhosts {
    public static final String FOLDER = "ghosts";
    public static final String ARCHIVE = "archive";
    public static final String LOG = "removals.log";
    public static final int MAX_INFOS = 4096;
    private final @Nullable Path root;
    private final Map<UUID, Set<String>> index = new HashMap<>();
    private final Map<String, EntryGhost.Info> infos = new LinkedHashMap<>(16, 0.75F, true);

    public EntryGhosts(@Nullable Path raceData) {
        this.root = raceData == null ? null : raceData.resolve(FOLDER);
    }

    public static EntryGhosts disabled() {
        return new EntryGhosts(null);
    }

    public boolean enabled() {
        return this.root != null;
    }

    public @Nullable Path root() {
        return this.root;
    }

    public @Nullable Path folder(UUID course) {
        return this.root == null ? null : this.root.resolve(course.toString());
    }

    public @Nullable Path file(UUID course, GhostKey key) {
        Path folder = this.folder(course);
        return folder == null ? null : folder.resolve(key.fileName());
    }

    private Set<String> names(UUID course) {
        Set<String> cached = this.index.get(course);
        if (cached != null) {
            return cached;
        }
        Set<String> names = new HashSet<>();
        Path folder = this.folder(course);
        if (folder != null && Files.isDirectory(folder)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*" + GhostKey.EXTENSION)) {
                for (Path file : files) {
                    if (Files.isRegularFile(file)) {
                        names.add(file.getFileName().toString());
                    }
                }
            } catch (IOException e) {
                Constants.LOG.warn("Geister der Bestenliste in {} nicht lesbar: {}", folder, e.toString());
            }
        }
        this.index.put(course, names);
        return names;
    }

    public boolean has(UUID course, GhostKey key) {
        return this.enabled() && this.names(course).contains(key.fileName());
    }

    public int count(UUID course) {
        return this.enabled() ? this.names(course).size() : 0;
    }

    public boolean put(UUID course, EntryGhost ghost) {
        Path file = this.file(course, ghost.key());
        if (file == null) {
            return false;
        }
        byte[] data;
        try {
            data = ghost.encode();
        } catch (RuntimeException e) {
            Constants.LOG.warn("Geist von {} auf Kurs {} nicht kodierbar: {}", ghost.info().name(), course, e.toString());
            return false;
        }
        if (data.length > EntryGhost.MAX_BYTES) {
            return false;
        }
        try {
            write(file, data);
        } catch (IOException e) {
            Constants.LOG.error("Geist von {} auf Kurs {} nicht gespeichert", ghost.info().name(), course, e);
            return false;
        }
        String name = ghost.key().fileName();
        this.names(course).add(name);
        this.remember(course, name, ghost.info());
        return true;
    }

    public byte @Nullable [] read(UUID course, GhostKey key) {
        Path file = this.file(course, key);
        if (file == null || !this.has(course, key)) {
            return null;
        }
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > EntryGhost.MAX_BYTES) {
                return null;
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            Constants.LOG.warn("Geist {} nicht lesbar: {}", file, e.toString());
            return null;
        }
    }

    public @Nullable EntryGhost get(UUID course, GhostKey key) {
        byte[] data = this.read(course, key);
        if (data == null) {
            return null;
        }
        try {
            EntryGhost ghost = EntryGhost.decode(data);
            return ghost.key().equals(key) ? ghost : null;
        } catch (IOException e) {
            Constants.LOG.warn("Geist von Kurs {} kaputt: {}", course, e.getMessage());
            return null;
        }
    }

    public EntryGhost.@Nullable Info info(UUID course, GhostKey key) {
        if (!this.has(course, key)) {
            return null;
        }
        String name = key.fileName();
        String cacheKey = course + "/" + name;
        EntryGhost.Info cached = this.infos.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        byte[] data = this.read(course, key);
        if (data == null) {
            return null;
        }
        try {
            EntryGhost.Info info = EntryGhost.info(data);
            if (!info.key().equals(key)) {
                return null;
            }
            this.remember(course, name, info);
            return info;
        } catch (IOException e) {
            Constants.LOG.warn("Kopf eines Geists von Kurs {} kaputt: {}", course, e.getMessage());
            return null;
        }
    }

    private void remember(UUID course, String name, EntryGhost.Info info) {
        this.infos.put(course + "/" + name, info);
        while (this.infos.size() > MAX_INFOS) {
            String eldest = this.infos.keySet().iterator().next();
            this.infos.remove(eldest);
        }
    }

    public int prune(UUID course, Collection<GhostKey> keep) {
        if (!this.enabled()) {
            return 0;
        }
        Set<String> wanted = new HashSet<>();
        for (GhostKey key : keep) {
            wanted.add(key.fileName());
        }
        Set<String> names = this.names(course);
        List<String> stale = new ArrayList<>();
        for (String name : names) {
            if (!wanted.contains(name)) {
                stale.add(name);
            }
        }
        Path folder = this.folder(course);
        int removed = 0;
        for (String name : stale) {
            names.remove(name);
            this.infos.remove(course + "/" + name);
            try {
                if (folder != null && Files.deleteIfExists(folder.resolve(name))) {
                    removed++;
                }
            } catch (IOException e) {
                Constants.LOG.warn("Geist {} nicht gelöscht: {}", name, e.toString());
            }
        }
        return removed;
    }

    public int archive(UUID course, int layout, Collection<Integer> keepLayouts) {
        Path folder = this.folder(course);
        if (folder == null) {
            return 0;
        }
        Set<String> names = this.names(course);
        int moved = 0;
        if (!names.isEmpty()) {
            Path target = folder.resolve(ARCHIVE).resolve(Integer.toString(layout));
            try {
                Files.createDirectories(target);
                for (String name : new ArrayList<>(names)) {
                    Path from = folder.resolve(name);
                    if (Files.isRegularFile(from)) {
                        Files.move(from, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                        moved++;
                    }
                }
            } catch (IOException e) {
                Constants.LOG.error("Geister von Kurs {} nicht archiviert", course, e);
            }
        }
        names.clear();
        this.forgetInfos(course);
        Path archive = folder.resolve(ARCHIVE);
        if (Files.isDirectory(archive)) {
            Set<String> keep = new HashSet<>();
            for (int kept : keepLayouts) {
                keep.add(Integer.toString(kept));
            }
            try (DirectoryStream<Path> layouts = Files.newDirectoryStream(archive)) {
                for (Path dir : layouts) {
                    if (Files.isDirectory(dir) && !keep.contains(dir.getFileName().toString())) {
                        deleteTree(dir);
                    }
                }
            } catch (IOException e) {
                Constants.LOG.warn("Altes Geister-Archiv von Kurs {} nicht aufgeräumt: {}", course, e.toString());
            }
        }
        return moved;
    }

    public List<Integer> archivedLayouts(UUID course) {
        Path folder = this.folder(course);
        Set<Integer> layouts = new TreeSet<>();
        if (folder == null || !Files.isDirectory(folder.resolve(ARCHIVE))) {
            return List.of();
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(folder.resolve(ARCHIVE))) {
            for (Path dir : dirs) {
                try {
                    layouts.add(Integer.parseInt(dir.getFileName().toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        return List.copyOf(layouts);
    }

    public int archivedCount(UUID course, int layout) {
        Path folder = this.folder(course);
        if (folder == null) {
            return 0;
        }
        Path dir = folder.resolve(ARCHIVE).resolve(Integer.toString(layout));
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files.filter(file -> file.getFileName().toString().endsWith(GhostKey.EXTENSION)).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public void removeCourse(UUID course) {
        Path folder = this.folder(course);
        this.index.remove(course);
        this.forgetInfos(course);
        if (folder != null && Files.exists(folder)) {
            try {
                deleteTree(folder);
            } catch (IOException e) {
                Constants.LOG.warn("Geister von Kurs {} nicht gelöscht: {}", course, e.toString());
            }
        }
    }

    private void forgetInfos(UUID course) {
        String prefix = course + "/";
        this.infos.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void forget() {
        this.index.clear();
        this.infos.clear();
    }

    public boolean log(String line) {
        if (this.root == null) {
            return false;
        }
        Path file = this.root.resolveSibling(LOG);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line.replace('\n', ' ').replace('\r', ' ') + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            Constants.LOG.error("Protokoll {} nicht geschrieben", file, e);
            return false;
        }
    }

    public @Nullable Path logFile() {
        return this.root == null ? null : this.root.resolveSibling(LOG);
    }

    static void write(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, data);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : all) {
                Files.deleteIfExists(path);
            }
        }
    }
}
