package de.lardtom.propwash.race;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.race.world.WorldCourseBoard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class CourseRegistry {
    public static final int MAX_COURSES = RacePayloads.MAX_COURSES;
    public static final int SYNC_BATCH = 32;
    private static final String FILE_NAME = "propwash_race.json";
    private static final double MOVE_EPSILON = 1.0E-3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable CourseRegistry instance;

    private final MinecraftServer server;
    private final Path path;
    private final RaceRecords records;
    private final RecordGhosts ghosts;
    private final Map<Integer, Course> courses = new TreeMap<>();
    private final Map<UUID, Integer> gateCourse = new HashMap<>();
    private int nextCourseId = 1;
    private boolean dirty;

    public static final class Course {
        public int id;
        public String dimension = "";
        public String owner = "";
        public String ownerName = "";
        public String name = "";
        public int laps = CourseLaps.UNSET;
        public int layout = 1;
        public String worldCourse = "";
        public String site = "";
        public int @Nullable [] board;
        public transient boolean boardDirty;
        public List<GateRecord> gates = new ArrayList<>();
        public List<Entry> leaderboard = new ArrayList<>();
        public List<ArchivedBoard> archive = new ArrayList<>();

        public boolean ownedBy(UUID player) {
            return this.owner.equals(player.toString());
        }

        public List<CourseElement> kinds() {
            List<CourseElement> kinds = new ArrayList<>(this.gates.size());
            for (GateRecord gate : this.gates) {
                kinds.add(gate.kind());
            }
            return kinds;
        }

        public int checkpoints() {
            return CourseCheckpoints.count(this.kinds());
        }

        public CourseData toData() {
            List<CourseData.Gate> list = new ArrayList<>(this.gates.size());
            for (GateRecord gate : this.gates) {
                list.add(new CourseData.Gate(gate.index, gate.x, gate.y, gate.z, gate.yaw, GateSize.byKey(gate.size), gate.mount(),
                        gate.flipped, gate.tilt(), gate.width, gate.height, gate.kind(), gate.variant(), gate.region()));
            }
            return new CourseData(this.id, this.dimension, list, this.name, this.laps, parseUuid(this.owner), this.ownerName);
        }
    }

    public static final class GateRecord {
        public String uuid = "";
        public int index;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public String size = GateSize.MEDIUM.key();
        public String mount = GateMount.STANDING.key();
        public boolean flipped;
        public float turn;
        public float pitch;
        public float roll;
        public double width;
        public double height;
        public int slot = -1;
        public @Nullable String kind;
        public @Nullable Integer variant;
        public @Nullable String region;

        public GateMount mount() {
            return GateMount.byKey(this.mount == null ? "" : this.mount);
        }

        public CourseElement kind() {
            return this.kind == null ? CourseElement.GATE : CourseElement.byKey(this.kind);
        }

        public int variant() {
            return this.variant == null ? 0 : this.kind().variant(this.variant);
        }

        public GateTilt tilt() {
            return new GateTilt(this.turn, this.pitch, this.roll);
        }

        public @Nullable FreeformRegion region() {
            return this.kind() == CourseElement.FREEFORM ? FreeformRegion.decode(this.region) : null;
        }
    }

    public static final class ArchivedBoard {
        public int layout;
        public long archivedAt;
        public int gates;
        public List<Entry> leaderboard = new ArrayList<>();
    }

    public static final class Entry {
        public String uuid = "";
        public String name = "";
        public long bestLap = -1L;
        public long bestLapAt;
        public int laps;
        public Map<String, Long> races = new TreeMap<>();
    }

    private static final class FileData {
        int nextCourseId = 1;
        List<Course> courses = new ArrayList<>();
    }

    private CourseRegistry(MinecraftServer server, Path path, RaceRecords records, RecordGhosts ghosts) {
        this.server = server;
        this.path = path;
        this.records = records;
        this.ghosts = ghosts;
    }

    public static @Nullable CourseRegistry get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        CourseRegistry registry = new CourseRegistry(server, server.getWorldPath(LevelResource.DATA).resolve(FILE_NAME),
                RaceRecords.get(server), RecordGhosts.get(server));
        registry.load();
        instance = registry;
    }

    public static void stop() {
        CourseRegistry registry = instance;
        instance = null;
        if (registry != null) {
            registry.removeUnusedCourses();
            registry.save();
        }
    }

    public int removeUnusedCourses() {
        int before = this.courses.size();
        this.courses.values().removeIf(this::unused);
        int removed = before - this.courses.size();
        if (removed > 0) {
            this.dirty = true;
            Constants.LOG.info("{} leere Kurse entfernt", removed);
        }
        return removed;
    }

    public boolean unused(Course course) {
        return course.gates.isEmpty() && this.records.isEmpty(course.id);
    }

    public RaceRecords records() {
        return this.records;
    }

    public RecordGhosts ghosts() {
        return this.ghosts;
    }

    public Leaderboard board(Course course) {
        return this.records.board(course.id);
    }

    private void migrateLegacy(Course course) {
        if (course.leaderboard.isEmpty() && course.archive.isEmpty()) {
            return;
        }
        if (this.records.isEmpty(course.id)) {
            for (ArchivedBoard board : course.archive) {
                Leaderboard current = this.records.board(course.id);
                for (Entry entry : board.leaderboard) {
                    UUID uuid = parseUuid(entry.uuid);
                    if (uuid != null) {
                        current.importLegacy(uuid, entry.name, entry.bestLap, entry.bestLapAt, entry.laps, entry.races);
                    }
                }
                this.records.archiveCurrent(course.id, board.layout, board.gates, board.archivedAt);
            }
            Leaderboard current = this.records.board(course.id);
            for (Entry entry : course.leaderboard) {
                UUID uuid = parseUuid(entry.uuid);
                if (uuid != null) {
                    current.importLegacy(uuid, entry.name, entry.bestLap, entry.bestLapAt, entry.laps, entry.races);
                }
            }
            this.records.changed();
            Constants.LOG.info("Bestenliste von Kurs {} in die Weltdaten übernommen", course.id);
        }
        course.leaderboard = new ArrayList<>();
        course.archive = new ArrayList<>();
        this.dirty = true;
    }

    public static String dimensionOf(Level level) {
        return level.dimension().identifier().toString();
    }

    private void load() {
        if (!Files.isRegularFile(this.path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
            FileData data = GSON.fromJson(reader, FileData.class);
            if (data == null) {
                return;
            }
            this.nextCourseId = Math.max(1, data.nextCourseId);
            if (data.courses != null) {
                for (Course course : data.courses) {
                    if (course == null || course.id <= 0) {
                        continue;
                    }
                    if (course.gates == null) {
                        course.gates = new ArrayList<>();
                    }
                    if (course.leaderboard == null) {
                        course.leaderboard = new ArrayList<>();
                    }
                    if (course.dimension == null) {
                        course.dimension = "";
                    }
                    if (course.owner == null || (!course.owner.isEmpty() && parseUuid(course.owner) == null)) {
                        course.owner = "";
                    }
                    if (course.ownerName == null) {
                        course.ownerName = "";
                    }
                    course.name = CourseNames.clean(course.name);
                    course.laps = CourseLaps.stored(course.laps);
                    if (course.worldCourse == null) {
                        course.worldCourse = "";
                    }
                    if (course.site == null) {
                        course.site = "";
                    }
                    course.boardDirty = !course.worldCourse.isEmpty();
                    if (course.archive == null) {
                        course.archive = new ArrayList<>();
                    }
                    course.archive.removeIf(board -> board == null || board.leaderboard == null);
                    for (ArchivedBoard board : course.archive) {
                        board.leaderboard.removeIf(entry -> entry == null || parseUuid(entry.uuid) == null);
                    }
                    course.layout = Math.max(1, course.layout);
                    course.gates.removeIf(gate -> gate == null || parseUuid(gate.uuid) == null);
                    course.leaderboard.removeIf(entry -> entry == null || parseUuid(entry.uuid) == null);
                    for (Entry entry : course.leaderboard) {
                        if (entry.races == null) {
                            entry.races = new TreeMap<>();
                        }
                        if (entry.name == null) {
                            entry.name = "";
                        }
                    }
                    course.gates.sort(Comparator.comparingInt(g -> g.index));
                    this.migrateLegacy(course);
                    if (this.unused(course)) {
                        this.nextCourseId = Math.max(this.nextCourseId, course.id + 1);
                        this.dirty = true;
                        continue;
                    }
                    this.courses.put(course.id, course);
                    this.nextCourseId = Math.max(this.nextCourseId, course.id + 1);
                    for (GateRecord gate : course.gates) {
                        this.gateCourse.put(parseUuid(gate.uuid), course.id);
                    }
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Constants.LOG.error("Renn-Daten {} nicht lesbar, starte leer", this.path, e);
            try {
                Files.move(this.path, this.path.resolveSibling(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                Constants.LOG.error("Kaputte Renn-Daten konnten nicht verschoben werden", moveError);
            }
        }
    }

    public void save() {
        if (!this.dirty) {
            return;
        }
        FileData data = new FileData();
        data.nextCourseId = this.nextCourseId;
        data.courses = new ArrayList<>(this.courses.values());
        try {
            Files.createDirectories(this.path.getParent());
            Path temp = this.path.resolveSibling(FILE_NAME + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
        } catch (IOException e) {
            Constants.LOG.error("Renn-Daten {} konnten nicht gespeichert werden", this.path, e);
        }
    }

    private static @Nullable UUID parseUuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<Course> courses() {
        return List.copyOf(this.courses.values());
    }

    public @Nullable Course course(int id) {
        return this.courses.get(id);
    }

    public int nextCourseId() {
        return this.nextCourseId;
    }

    public boolean full() {
        return this.courses.size() >= MAX_COURSES;
    }

    public int createCourse(String dimension, @Nullable ServerPlayer owner) {
        if (this.full()) {
            return GateEntity.NO_COURSE;
        }
        Course course = new Course();
        course.id = this.nextCourseId++;
        course.dimension = dimension;
        if (owner != null) {
            course.owner = owner.getUUID().toString();
            course.ownerName = owner.getGameProfile().name();
            course.laps = CourseEdits.defaultLaps(owner.getUUID());
        }
        this.courses.put(course.id, course);
        this.dirty = true;
        this.broadcast(course);
        return course.id;
    }

    public @Nullable Course worldCourse(String dimension, String site) {
        for (Course course : this.courses.values()) {
            if (!course.worldCourse.isEmpty() && course.site.equals(site) && course.dimension.equals(dimension)) {
                return course;
            }
        }
        return null;
    }

    public int createWorldCourse(String dimension, String variant, String site, int[] board) {
        int id = this.createCourse(dimension, null);
        Course course = this.courses.get(id);
        if (course != null) {
            course.worldCourse = variant;
            course.site = site;
            course.board = board.length == 3 ? board.clone() : null;
            course.boardDirty = true;
        }
        return id;
    }

    public int insertGate(int courseId, GateEntity gate, int slot) {
        Course course = this.courses.get(courseId);
        if (course == null || !course.dimension.equals(dimensionOf(gate.level())) || course.gates.size() >= CourseData.MAX_GATES) {
            return GateEntity.NO_INDEX;
        }
        if (this.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
            this.removeGate(gate.getUUID());
        }
        GateRecord record = new GateRecord();
        record.uuid = gate.getUUID().toString();
        record.slot = Math.max(0, slot);
        copyPose(gate, record);
        int at = course.gates.size();
        for (int i = 0; i < course.gates.size(); i++) {
            int other = course.gates.get(i).slot;
            if (other < 0 || other > record.slot) {
                at = i;
                break;
            }
        }
        course.gates.add(at, record);
        this.gateCourse.put(gate.getUUID(), courseId);
        this.reindex(course);
        gate.setCourse(courseId, record.index);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
        return record.index;
    }

    public void refreshBoard(Course course) {
        course.boardDirty = !WorldCourseBoard.update(this.server, course, this.board(course).record(null).orElse(null));
    }

    private void boardChanged(Course course) {
        if (!course.worldCourse.isEmpty()) {
            this.refreshBoard(course);
        }
    }

    public static boolean isGamemaster(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean canEdit(ServerPlayer player, Course course) {
        return course.owner.isEmpty() || course.ownedBy(player.getUUID()) || isGamemaster(player);
    }

    public boolean mayEditGate(ServerPlayer player, UUID gate) {
        Course course = this.course(this.courseOf(gate));
        return course == null || canEdit(player, course);
    }

    public void claim(Course course, ServerPlayer player) {
        if (course.owner.isEmpty()) {
            course.owner = player.getUUID().toString();
            course.ownerName = player.getGameProfile().name();
            this.dirty = true;
        }
    }

    public static Component label(Course course) {
        return CourseNames.named(course.name) ? Component.literal(course.name) : Component.translatable("propwash.race.course.number", course.id);
    }

    public static Component ref(Course course) {
        return Component.literal(CourseNames.named(course.name) ? course.name : Integer.toString(course.id));
    }

    public static Component lapsLabel(int laps) {
        if (!CourseLaps.set(laps)) {
            return Component.translatable("propwash.race.course.laps_personal");
        }
        return laps == CourseLaps.ENDLESS ? Component.translatable("propwash.race.msg.endless") : Component.translatable("propwash.race.msg.laps", laps);
    }

    public boolean rename(Course course, String name) {
        String clean = CourseNames.clean(name);
        if (clean.equals(course.name)) {
            return false;
        }
        course.name = clean;
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    public boolean setLaps(Course course, int laps) {
        int stored = CourseLaps.stored(laps);
        if (stored == course.laps) {
            return false;
        }
        course.laps = stored;
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    public boolean moveGate(Course course, int from, int to) {
        if (!CourseOrder.canMove(course.kinds(), from, to)) {
            return false;
        }
        CourseOrder.move(course.gates, from, to);
        this.reordered(course);
        return true;
    }

    public boolean makeStart(Course course, int index) {
        if (!CourseOrder.canStart(course.kinds(), index)) {
            return false;
        }
        CourseOrder.rotate(course.gates, index);
        this.reordered(course);
        return true;
    }

    public boolean detachGate(Course course, int index) {
        if (!CourseOrder.canRemove(course.kinds(), index)) {
            return false;
        }
        UUID uuid = parseUuid(course.gates.get(index).uuid);
        GateEntity entity = this.loadedGate(course, uuid);
        if (uuid == null || !this.removeGate(uuid)) {
            return false;
        }
        if (entity != null) {
            entity.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        }
        return true;
    }

    private void reordered(Course course) {
        this.layoutChanged(course);
        this.reindex(course);
        this.dirty = true;
        this.broadcast(course);
    }

    public static Component ownerName(Course course) {
        return course.ownerName.isEmpty() ? Component.translatable("propwash.race.server.unknown_owner") : Component.literal(course.ownerName);
    }

    public boolean deleteCourse(int id) {
        Course course = this.courses.remove(id);
        this.records.remove(id);
        this.ghosts.removeCourse(id);
        if (course == null) {
            return false;
        }
        for (GateRecord gate : course.gates) {
            UUID uuid = parseUuid(gate.uuid);
            this.gateCourse.remove(uuid);
            GateEntity entity = this.loadedGate(course, uuid);
            if (entity != null) {
                entity.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
            }
        }
        this.dirty = true;
        this.sendToAll(new RacePayloads.CourseSyncS2C(false, List.of(), List.of(id)));
        return true;
    }

    public int courseOf(UUID gate) {
        return this.gateCourse.getOrDefault(gate, GateEntity.NO_COURSE);
    }

    public int appendGate(int courseId, GateEntity gate) {
        Course course = this.courses.get(courseId);
        if (course == null || !course.dimension.equals(dimensionOf(gate.level())) || course.gates.size() >= CourseData.MAX_GATES) {
            return GateEntity.NO_INDEX;
        }
        int existing = this.courseOf(gate.getUUID());
        if (existing != GateEntity.NO_COURSE) {
            this.removeGate(gate.getUUID());
        }
        GateRecord record = new GateRecord();
        record.uuid = gate.getUUID().toString();
        record.index = course.gates.size();
        copyPose(gate, record);
        course.gates.add(record);
        this.gateCourse.put(gate.getUUID(), courseId);
        gate.setCourse(courseId, record.index);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
        return record.index;
    }

    public boolean removeGate(UUID uuid) {
        Integer courseId = this.gateCourse.remove(uuid);
        if (courseId == null) {
            return false;
        }
        Course course = this.courses.get(courseId);
        if (course == null) {
            return false;
        }
        String key = uuid.toString();
        course.gates.removeIf(gate -> gate.uuid.equals(key));
        this.layoutChanged(course);
        this.reindex(course);
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    private void reindex(Course course) {
        for (int i = 0; i < course.gates.size(); i++) {
            GateRecord gate = course.gates.get(i);
            if (gate.index != i) {
                gate.index = i;
                GateEntity entity = this.loadedGate(course, parseUuid(gate.uuid));
                if (entity != null) {
                    entity.setCourse(course.id, i);
                }
            }
        }
    }

    private void layoutChanged(Course course) {
        this.ghosts.removeCourse(course.id);
        if (!this.records.archiveCurrent(course.id, course.layout, course.gates.size(), System.currentTimeMillis())) {
            return;
        }
        int archivedLayout = course.layout;
        course.layout++;
        this.boardChanged(course);
        Component message = Component.translatable("propwash.race.server.leaderboard_archived", ref(course), archivedLayout)
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            if (sameDimension(course, player.level())) {
                player.sendSystemMessage(message);
            }
        }
    }

    public void gateDestroyed(GateEntity gate) {
        if (this.removeGate(gate.getUUID())) {
            gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        }
    }

    public void gateRotated(GateEntity gate) {
        UUID uuid = gate.getUUID();
        Integer courseId = this.gateCourse.get(uuid);
        Course course = courseId == null ? null : this.courses.get(courseId);
        GateRecord record = course == null ? null : this.record(course, uuid);
        if (record == null || !course.dimension.equals(dimensionOf(gate.level())) || !moved(gate, record)) {
            this.reconcile(gate);
            return;
        }
        copyPose(gate, record);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
    }

    public void reconcile(GateEntity gate) {
        UUID uuid = gate.getUUID();
        Integer courseId = this.gateCourse.get(uuid);
        if (courseId != null) {
            Course course = this.courses.get(courseId);
            GateRecord record = course == null ? null : this.record(course, uuid);
            if (course == null || record == null) {
                this.gateCourse.remove(uuid);
                return;
            }
            if (gate.courseId() != course.id || gate.gateIndex() != record.index) {
                gate.setCourse(course.id, record.index);
            }
            if (moved(gate, record) || !course.dimension.equals(dimensionOf(gate.level()))) {
                if (!course.dimension.equals(dimensionOf(gate.level()))) {
                    this.removeGate(uuid);
                    gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
                    return;
                }
                copyPose(gate, record);
                this.dirty = true;
                this.broadcast(course);
            }
            if (course.boardDirty) {
                this.refreshBoard(course);
            }
            return;
        }
        int claimed = gate.courseId();
        if (claimed == GateEntity.NO_COURSE) {
            return;
        }
        Course course = this.courses.get(claimed);
        if (course != null || claimed < this.nextCourseId || this.full()) {
            gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
            return;
        }
        course = new Course();
        course.id = claimed;
        course.dimension = dimensionOf(gate.level());
        this.courses.put(claimed, course);
        this.nextCourseId = claimed + 1;
        GateRecord record = new GateRecord();
        record.uuid = uuid.toString();
        record.index = Math.max(0, gate.gateIndex());
        copyPose(gate, record);
        course.gates.add(record);
        course.gates.sort(Comparator.comparingInt(g -> g.index));
        this.gateCourse.put(uuid, claimed);
        this.dirty = true;
        this.broadcast(course);
    }

    private @Nullable GateRecord record(Course course, UUID uuid) {
        String key = uuid.toString();
        for (GateRecord gate : course.gates) {
            if (gate.uuid.equals(key)) {
                return gate;
            }
        }
        return null;
    }

    private static boolean moved(GateEntity gate, GateRecord record) {
        return Math.abs(gate.getX() - record.x) > MOVE_EPSILON || Math.abs(gate.getY() - record.y) > MOVE_EPSILON
                || Math.abs(gate.getZ() - record.z) > MOVE_EPSILON || Math.abs(gate.facingYaw() - record.yaw) > 0.01F
                || !gate.size().key().equals(record.size) || gate.mount() != record.mount() || gate.flipped() != record.flipped
                || !gate.tilt().equals(record.tilt()) || gate.kind() != record.kind() || gate.variant() != record.variant()
                || (gate.kind() == CourseElement.FREEFORM ? !gate.regionText().equals(record.region == null ? "" : record.region)
                : Math.abs(recordWidth(gate, record) - gate.width()) > MOVE_EPSILON || Math.abs(recordHeight(gate, record) - gate.height()) > MOVE_EPSILON);
    }

    private static double recordWidth(GateEntity gate, GateRecord record) {
        if (record.width > 0.0) {
            return record.width;
        }
        CourseElement kind = record.kind();
        return kind.gate() ? gate.size().width() : kind.outerWidth(kind.defaultWidth());
    }

    private static double recordHeight(GateEntity gate, GateRecord record) {
        if (record.height > 0.0) {
            return record.height;
        }
        CourseElement kind = record.kind();
        return kind.gate() ? gate.size().height() : kind.outerHeight(kind.defaultHeight());
    }

    private static void copyPose(GateEntity gate, GateRecord record) {
        record.x = gate.getX();
        record.y = gate.getY();
        record.z = gate.getZ();
        record.yaw = gate.facingYaw();
        record.size = gate.size().key();
        record.mount = gate.mount().key();
        record.flipped = gate.flipped();
        GateTilt tilt = gate.tilt();
        record.turn = tilt.yaw();
        record.pitch = tilt.pitch();
        record.roll = tilt.roll();
        CourseElement kind = gate.kind();
        boolean standard = kind.gate() ? gate.size().standard(gate.width(), gate.height()) : !gate.customOpening();
        record.width = standard ? 0.0 : gate.width();
        record.height = standard ? 0.0 : gate.height();
        record.kind = kind.gate() ? null : kind.key();
        record.variant = kind.gate() ? null : gate.variant();
        record.region = kind == CourseElement.FREEFORM ? gate.regionText() : null;
        if (kind == CourseElement.FREEFORM) {
            record.width = 0.0;
            record.height = 0.0;
        }
    }

    private @Nullable GateEntity loadedGate(Course course, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(course.dimension);
        if (id == null) {
            return null;
        }
        ServerLevel level = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof GateEntity gate ? gate : null;
    }

    public List<GateGeometry> geometry(Course course) {
        return course.toData().geometry();
    }

    public Leaderboard.Outcome submitLap(Course course, ServerPlayer player, String frame, long lapNanos) {
        Leaderboard.Outcome outcome = this.board(course).submit(player.getUUID(), player.getGameProfile().name(), frame, lapNanos,
                System.currentTimeMillis());
        this.records.changed();
        if (outcome.record()) {
            this.boardChanged(course);
        }
        return outcome;
    }

    public boolean submitRace(Course course, ServerPlayer player, int laps, long totalNanos) {
        boolean best = this.board(course).submitRace(player.getUUID(), laps, totalNanos);
        this.records.changed();
        return best;
    }

    public boolean submitAssistedLap(Course course, ServerPlayer player, String frame, long lapNanos, int assist) {
        boolean best = this.board(course).submitAssisted(player.getUUID(), player.getGameProfile().name(), frame, lapNanos,
                System.currentTimeMillis(), assist);
        this.records.changed();
        return best;
    }

    public boolean submitAssistedRace(Course course, ServerPlayer player, int laps, long totalNanos, int assist) {
        boolean best = this.board(course).submitAssistedRace(player.getUUID(), laps, totalNanos, assist);
        this.records.changed();
        return best;
    }

    public int rankOf(Course course, UUID player, @Nullable String frame) {
        return this.board(course).rank(player, frame);
    }

    public int resetLeaderboard(Course course, @Nullable String playerName) {
        Leaderboard board = this.board(course);
        int removed = playerName == null ? board.clear() : board.removePlayer(playerName);
        if (removed > 0) {
            this.records.changed();
            this.boardChanged(course);
        }
        if (playerName == null) {
            this.ghosts.removeCourse(course.id);
        }
        return removed;
    }

    public @Nullable Course nearestCourse(ServerPlayer player, double maxDistance) {
        String dimension = dimensionOf(player.level());
        Course best = null;
        double bestDistance = maxDistance * maxDistance;
        for (Course course : this.courses.values()) {
            if (!course.dimension.equals(dimension) || course.gates.isEmpty()) {
                continue;
            }
            for (GateRecord gate : course.gates) {
                double distance = player.distanceToSqr(gate.x, gate.y, gate.z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = course;
                }
            }
        }
        return best;
    }

    public void sendAll(ServerPlayer player) {
        List<CourseData> data = new ArrayList<>(this.courses.size());
        for (Course course : this.courses.values()) {
            data.add(course.toData());
        }
        for (RacePayloads.CourseSyncS2C payload : joinBatches(data)) {
            PropwashNet.sendToPlayer(player, payload);
        }
    }

    public static List<RacePayloads.CourseSyncS2C> joinBatches(List<CourseData> courses) {
        List<RacePayloads.CourseSyncS2C> batches = new ArrayList<>();
        int start = 0;
        do {
            int end = start;
            int bytes = 0;
            while (end < courses.size() && end - start < SYNC_BATCH) {
                int next = courses.get(end).estimatedBytes();
                if (end > start && bytes + next > CourseData.MAX_BATCH_BYTES) {
                    break;
                }
                bytes += next;
                end++;
            }
            batches.add(new RacePayloads.CourseSyncS2C(start == 0, courses.subList(start, end), List.of()));
            start = end;
        } while (start < courses.size());
        return batches;
    }

    private void broadcast(Course course) {
        this.sendToAll(new RacePayloads.CourseSyncS2C(false, List.of(course.toData()), List.of()));
    }

    private void sendToAll(RacePayloads.CourseSyncS2C payload) {
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            PropwashNet.sendToPlayer(player, payload);
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public Path path() {
        return this.path;
    }

    static boolean sameDimension(Course course, Level level) {
        return Objects.equals(course.dimension, dimensionOf(level));
    }
}
