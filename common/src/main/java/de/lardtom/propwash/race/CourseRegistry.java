package de.lardtom.propwash.race;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.race.ChangeKind;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.race.export.RaceDataEvents;
import de.lardtom.propwash.race.export.RaceDataMirror;
import de.lardtom.propwash.race.world.WorldCourseBoard;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class CourseRegistry {
    public static final int MAX_COURSES = RacePayloads.MAX_COURSES;
    public static final int SYNC_BATCH = 32;
    private static final String FILE_NAME = "propwash_race.json";
    public static final int FORMAT = 2;
    public static final int LEGACY_FORMAT = 1;
    private static final double MOVE_EPSILON = 1.0E-3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable CourseRegistry instance;

    private final @Nullable MinecraftServer server;
    private final Path path;
    private final RaceRecords records;
    private final RecordGhosts ghosts;
    private EntryGhosts entryGhosts = EntryGhosts.disabled();
    private final Map<Integer, Course> courses = new TreeMap<>();
    private final Map<UUID, Integer> gateCourse = new HashMap<>();
    private int nextCourseId = 1;
    private boolean dirty;
    private int assignedKeys;

    public static final class Course {
        public int id;
        public @Nullable String uuid;
        public transient @Nullable UUID key;
        public String dimension = "";
        public String owner = "";
        public String ownerName = "";
        public String name = "";
        public int laps = CourseLaps.UNSET;
        public @Nullable String mode;
        public @Nullable String creativeFc;
        public @Nullable Boolean raceLine;
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

        public CourseMode mode() {
            return PointToPoint.read(this.mode);
        }

        public CreativeFcRule creativeFc() {
            return CreativeFcRule.byKey(this.creativeFc);
        }

        public boolean raceLine() {
            return !Boolean.FALSE.equals(this.raceLine);
        }

        public boolean pointToPoint() {
            return PointToPoint.active(this.mode(), this.gates.size());
        }

        public UUID key() {
            UUID current = this.key;
            if (current == null) {
                current = CourseId.parse(this.uuid);
                if (!CourseId.usable(current)) {
                    current = CourseId.random();
                }
                this.assignKey(current);
            }
            return current;
        }

        public @Nullable UUID storedKey() {
            UUID current = this.key;
            return current != null ? current : CourseId.parse(this.uuid);
        }

        void assignKey(UUID next) {
            this.key = next;
            this.uuid = CourseId.text(next);
        }

        public List<CourseElement> kinds() {
            List<CourseElement> kinds = new ArrayList<>(this.gates.size());
            for (GateRecord gate : this.gates) {
                kinds.add(gate.kind());
            }
            return kinds;
        }

        public List<CourseElement> scoringKinds() {
            List<CourseElement> kinds = new ArrayList<>(this.gates.size());
            for (GateRecord gate : this.gates) {
                kinds.add(gate.scoringKind());
            }
            return kinds;
        }

        public int checkpoints() {
            return CourseCheckpoints.count(this.scoringKinds());
        }

        public CourseData toData() {
            List<CourseData.Gate> list = new ArrayList<>(this.gates.size());
            for (GateRecord gate : this.gates) {
                list.add(new CourseData.Gate(gate.index, gate.x, gate.y, gate.z, gate.yaw, GateSize.byKey(gate.size), gate.mount(),
                        gate.flipped, gate.tilt(), gate.width, gate.height, gate.kind(), gate.variant(), gate.region(), gate.scoreArea(),
                        gate.reversed()));
            }
            return new CourseData(this.id, this.dimension, list, this.name, this.laps, parseUuid(this.owner), this.ownerName, this.storedKey(),
                    this.mode(), this.creativeFc(), this.raceLine());
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
        public @Nullable Score score;
        public @Nullable Boolean reverse;

        public boolean reversed() {
            return Boolean.TRUE.equals(this.reverse);
        }

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

        public ScoreArea scoreArea() {
            Score stored = this.score;
            ScoreArea area = stored == null ? ScoreArea.DEFAULT : stored.area();
            return area.forCourse(this.kind());
        }

        public CourseElement scoringKind() {
            CourseElement kind = this.kind();
            return kind == CourseElement.GATE_POLE ? this.scoreArea().role().scoringKind() : kind;
        }
    }

    public static final class Score {
        public @Nullable String role;
        public @Nullable String side;
        public @Nullable Double reach;
        public @Nullable Double below;
        public @Nullable Double above;

        public ScoreArea area() {
            return new ScoreArea(this.role == null ? ScoreArea.Role.NONE : ScoreArea.Role.byKey(this.role),
                    this.side == null ? CourseElement.LEFT : ScoreArea.sideByKey(this.side),
                    this.reach == null ? ScoreArea.DEFAULT_REACH : this.reach,
                    this.below == null ? ScoreArea.DEFAULT_BELOW : this.below,
                    this.above == null ? ScoreArea.DEFAULT_ABOVE : this.above, false);
        }

        public static @Nullable Score of(ScoreArea area, CourseElement kind) {
            ScoreArea normalized = area.forCourse(kind);
            if (normalized.isDefault()) {
                return null;
            }
            Score score = new Score();
            if (kind == CourseElement.GATE_POLE) {
                score.role = normalized.role().key();
                score.side = ScoreArea.sideKey(normalized.side());
                score.reach = normalized.reach();
            }
            if (normalized.below() != ScoreArea.DEFAULT_BELOW) {
                score.below = normalized.below();
            }
            if (normalized.above() != ScoreArea.DEFAULT_ABOVE) {
                score.above = normalized.above();
            }
            return score;
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

    static final class FileData {
        int format;
        int nextCourseId = 1;
        List<Course> courses = new ArrayList<>();

        int format() {
            return this.format <= 0 ? LEGACY_FORMAT : this.format;
        }
    }

    static @Nullable FileData read(Reader reader) {
        return GSON.fromJson(reader, FileData.class);
    }

    static String write(FileData data) {
        data.format = FORMAT;
        return GSON.toJson(data);
    }

    private CourseRegistry(@Nullable MinecraftServer server, Path path, RaceRecords records, RecordGhosts ghosts) {
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
        if (registry.assignedKeys > 0) {
            Constants.LOG.info("Kurs-IDs für {} Kurse vergeben", registry.assignedKeys);
            registry.save();
        }
        registry.entryGhosts = new EntryGhosts(RaceDataMirror.root(server));
        int migrated = registry.migrateRecordGhosts();
        if (migrated > 0) {
            Constants.LOG.info("{} Rekord-Geister in die Geister der Bestenliste übernommen", migrated);
        }
        instance = registry;
        RaceDataMirror.start(server, registry);
    }

    public static void stop() {
        CourseRegistry registry = instance;
        instance = null;
        if (registry != null) {
            registry.removeUnusedCourses();
            registry.save();
            RaceDataMirror.stop(registry);
        } else {
            RaceDataMirror.stop(null);
        }
    }

    public static CourseRegistry offline(Path file, RaceRecords records, RecordGhosts ghosts) {
        CourseRegistry registry = new CourseRegistry(null, file, records, ghosts);
        registry.load();
        return registry;
    }

    public int assignedKeys() {
        return this.assignedKeys;
    }

    public int removeUnusedCourses() {
        int before = this.courses.size();
        this.courses.values().removeIf(course -> {
            if (!this.unused(course)) {
                return false;
            }
            this.entryGhosts.removeCourse(course.key());
            return true;
        });
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

    public EntryGhosts entryGhosts() {
        return this.entryGhosts;
    }

    public void useEntryGhosts(EntryGhosts store) {
        this.entryGhosts = store == null ? EntryGhosts.disabled() : store;
    }

    public static List<GhostKey> keys(Leaderboard board) {
        List<GhostKey> keys = new ArrayList<>();
        for (Leaderboard.Entry entry : board.storedTimes()) {
            keys.add(GhostKey.of(entry));
        }
        return keys;
    }

    public int pruneGhosts(Course course) {
        if (!this.entryGhosts.enabled()) {
            return 0;
        }
        return this.entryGhosts.prune(course.key(), keys(this.board(course)));
    }

    public int migrateRecordGhosts() {
        if (!this.entryGhosts.enabled()) {
            return 0;
        }
        int migrated = 0;
        for (RecordGhosts.Stored stored : this.ghosts.all()) {
            Course course = this.courses.get(stored.course());
            if (course == null || stored.layout() != course.toData().layoutHash()) {
                continue;
            }
            for (Leaderboard.Entry entry : this.board(course).storedTimes()) {
                if (!entry.frame().equals(stored.frame()) || entry.nanos() != stored.nanos() || !entry.name().equals(stored.holder())) {
                    continue;
                }
                GhostKey key = GhostKey.of(entry);
                if (this.entryGhosts.has(course.key(), key)) {
                    break;
                }
                EntryGhost.Info info = new EntryGhost.Info(entry.player(), entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), entry.assist(),
                        stored.layout(), Float.NaN, "", EntryGhost.Source.RECORD_GHOST);
                if (this.entryGhosts.put(course.key(), new EntryGhost(info, stored.data()))) {
                    migrated++;
                }
                break;
            }
        }
        return migrated;
    }

    public List<Leaderboard.Entry> removeTimes(Course course, UUID player, @Nullable String frame, Leaderboard.Section section) {
        Leaderboard board = this.board(course);
        List<Leaderboard.Entry> removed = board.removeTimes(player, frame, section);
        if (removed.isEmpty()) {
            return removed;
        }
        this.records.changed();
        for (Leaderboard.Entry entry : removed) {
            RecordGhosts.Stored legacy = this.ghosts.get(course.id, entry.frame());
            if (legacy != null && legacy.nanos() == entry.nanos() && legacy.holder().equals(entry.name())) {
                this.ghosts.remove(course.id, entry.frame());
            }
        }
        this.pruneGhosts(course);
        this.boardChanged(course);
        RaceDataMirror.touch(this, course.id);
        return removed;
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
            FileData data = read(reader);
            if (data == null) {
                return;
            }
            int format = data.format();
            if (format > FORMAT) {
                Constants.LOG.warn("Renn-Daten {} haben Format {}, bekannt ist {}; lade, was lesbar ist", this.path, format, FORMAT);
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
                    course.mode = PointToPoint.stored(course.mode());
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
            this.assignedKeys = CourseId.assign(new ArrayList<>(this.courses.values()), CourseId::random);
            if (this.assignedKeys > 0) {
                this.dirty = true;
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
                writer.write(write(data));
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

    public @Nullable Course course(UUID key) {
        for (Course course : this.courses.values()) {
            if (key.equals(course.key())) {
                return course;
            }
        }
        return null;
    }

    private UUID freshKey() {
        UUID key = CourseId.random();
        while (!CourseId.usable(key) || this.course(key) != null) {
            key = CourseId.random();
        }
        return key;
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
        course.assignKey(this.freshKey());
        course.dimension = dimension;
        if (owner != null) {
            course.owner = owner.getUUID().toString();
            course.ownerName = owner.getGameProfile().name();
            course.laps = CourseEdits.defaultLaps(owner.getUUID());
        }
        this.courses.put(course.id, course);
        this.dirty = true;
        this.broadcast(course);
        RaceDataEvents.created(this, course);
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
        return this.createWorldCourse(dimension, variant, site, board, CourseMode.LAPS);
    }

    public int createWorldCourse(String dimension, String variant, String site, int[] board, CourseMode mode) {
        int id = this.createCourse(dimension, null);
        Course course = this.courses.get(id);
        if (course != null) {
            course.worldCourse = variant;
            course.site = site;
            course.board = board.length == 3 ? board.clone() : null;
            course.boardDirty = true;
            course.mode = PointToPoint.stored(mode == null ? CourseMode.LAPS : mode);
        }
        return id;
    }

    public int insertGate(int courseId, GateEntity gate, int slot) {
        return this.insertPasses(courseId, gate, List.of(new CourseOrder.Pass(slot, false)));
    }

    public int insertPasses(int courseId, GateEntity gate, List<CourseOrder.Pass> slots) {
        Course course = this.courses.get(courseId);
        if (course == null || slots.isEmpty() || !course.dimension.equals(dimensionOf(gate.level()))
                || course.gates.size() + slots.size() > CourseData.MAX_GATES) {
            return GateEntity.NO_INDEX;
        }
        if (this.courseOf(gate.getUUID()) != GateEntity.NO_COURSE) {
            this.removeGate(gate.getUUID());
        }
        for (CourseOrder.Pass pass : slots) {
            GateRecord record = new GateRecord();
            record.uuid = gate.getUUID().toString();
            record.slot = Math.max(0, pass.index());
            record.reverse = pass.reverse() ? Boolean.TRUE : null;
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
        }
        this.gateCourse.put(gate.getUUID(), courseId);
        this.reindex(course);
        List<CourseOrder.Pass> placed = this.passes(course, gate.getUUID());
        gate.setPasses(courseId, placed);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
        return placed.getFirst().index();
    }

    public void refreshBoard(Course course) {
        MinecraftServer current = this.server;
        if (current != null) {
            course.boardDirty = !WorldCourseBoard.update(current, course, this.board(course).record(null).orElse(null));
        }
    }

    private void boardChanged(Course course) {
        if (!course.worldCourse.isEmpty()) {
            this.refreshBoard(course);
        }
    }

    public static boolean isGamemaster(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean isOperator(ServerPlayer player) {
        return isGamemaster(player) || ServerRules.isHost(player);
    }

    public static boolean canEdit(ServerPlayer player, Course course) {
        return CourseAccess.mayChange(course.ownedBy(player.getUUID()), isOperator(player));
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
            RaceDataMirror.touch(this, course.id);
            RaceDataEvents.changed(this, course, ChangeKind.OWNER, 0);
        }
    }

    public static Component label(Course course) {
        return CourseNames.named(course.name) ? Component.literal(course.name) : Component.translatable("propwash.race.course.number", course.id);
    }

    public static Component ref(Course course) {
        return Component.literal(CourseNames.named(course.name) ? course.name : Integer.toString(course.id));
    }

    public static Component formatLabel(Course course) {
        return course.mode() == CourseMode.POINT_TO_POINT ? Component.translatable("propwash.race.p2p.mode") : lapsLabel(course.laps);
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
        RaceDataEvents.changed(this, course, ChangeKind.NAME, 0);
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
        RaceDataEvents.changed(this, course, ChangeKind.LAPS, 0);
        return true;
    }

    public boolean setMode(Course course, CourseMode mode) {
        CourseMode next = mode == null ? CourseMode.LAPS : mode;
        if (next == course.mode()) {
            return false;
        }
        course.mode = PointToPoint.stored(next);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    public boolean setCreativeFc(Course course, CreativeFcRule rule) {
        CreativeFcRule next = rule == null ? CreativeFcRule.WORLD : rule;
        if (next == course.creativeFc()) {
            return false;
        }
        course.creativeFc = next.stored();
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    public boolean setRaceLine(Course course, boolean shown) {
        if (shown == course.raceLine()) {
            return false;
        }
        course.raceLine = shown ? null : Boolean.FALSE;
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    public boolean creativeFcLegal(Course course) {
        return course.creativeFc().legal(WorldRules.on(this.server, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL));
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
        if (uuid == null) {
            return false;
        }
        if (this.passCount(course, uuid) > 1) {
            course.gates.remove(index);
            this.layoutChanged(course);
            this.reindex(course);
            this.dirty = true;
            this.broadcast(course);
            return true;
        }
        GateEntity entity = this.loadedGate(course, uuid);
        if (!this.removeGate(uuid)) {
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

    public static MutableComponent notOwner(Course course) {
        return course.owner.isEmpty() ? Component.translatable("propwash.race.p2p.tool.ownerless", course.id)
                : Component.translatable("propwash.race.tool.not_owner", course.id, ownerName(course));
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
        this.entryGhosts.removeCourse(course.key());
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
        RaceDataMirror.deleted(this, course.key());
        RaceDataEvents.deleted(course);
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

    public int appendPass(int courseId, GateEntity gate, boolean reverse) {
        Course course = this.courses.get(courseId);
        if (course == null || this.courseOf(gate.getUUID()) != courseId || course.gates.size() >= CourseData.MAX_GATES) {
            return GateEntity.NO_INDEX;
        }
        GateRecord record = new GateRecord();
        record.uuid = gate.getUUID().toString();
        record.index = course.gates.size();
        record.reverse = reverse ? Boolean.TRUE : null;
        copyPose(gate, record);
        course.gates.add(record);
        this.syncPasses(course, gate.getUUID(), gate);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
        return record.index;
    }

    public boolean reversePass(Course course, int index) {
        if (!CourseOrder.inside(course.gates.size(), index)) {
            return false;
        }
        GateRecord record = course.gates.get(index);
        record.reverse = record.reversed() ? null : Boolean.TRUE;
        this.syncPasses(course, parseUuid(record.uuid), null);
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
        return true;
    }

    public int passCount(Course course, UUID uuid) {
        return this.records(course, uuid).size();
    }

    public List<CourseOrder.Pass> passes(Course course, UUID uuid) {
        List<CourseOrder.Pass> passes = new ArrayList<>();
        String key = uuid.toString();
        for (GateRecord gate : course.gates) {
            if (gate.uuid.equals(key)) {
                passes.add(new CourseOrder.Pass(gate.index, gate.reversed()));
            }
        }
        return List.copyOf(passes);
    }

    private List<GateRecord> records(Course course, UUID uuid) {
        List<GateRecord> found = new ArrayList<>(1);
        String key = uuid.toString();
        for (GateRecord gate : course.gates) {
            if (gate.uuid.equals(key)) {
                found.add(gate);
            }
        }
        return found;
    }

    private void syncPasses(Course course, @Nullable UUID uuid, @Nullable GateEntity known) {
        if (uuid == null) {
            return;
        }
        GateEntity entity = known != null ? known : this.loadedGate(course, uuid);
        if (entity == null) {
            return;
        }
        List<CourseOrder.Pass> passes = this.passes(course, uuid);
        if (passes.isEmpty()) {
            entity.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        } else {
            entity.setPasses(course.id, passes);
        }
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
            course.gates.get(i).index = i;
        }
        List<String> seen = new ArrayList<>(course.gates.size());
        for (GateRecord gate : course.gates) {
            if (!seen.contains(gate.uuid)) {
                seen.add(gate.uuid);
                this.syncPasses(course, parseUuid(gate.uuid), null);
            }
        }
    }

    private void layoutChanged(Course course) {
        this.ghosts.removeCourse(course.id);
        RaceDataMirror.touch(this, course.id);
        if (!this.records.archiveCurrent(course.id, course.layout, course.gates.size(), System.currentTimeMillis())) {
            this.pruneGhosts(course);
            RaceDataEvents.changed(this, course, ChangeKind.LAYOUT, 0);
            return;
        }
        int archivedLayout = course.layout;
        if (this.entryGhosts.enabled()) {
            List<Integer> layouts = new ArrayList<>();
            for (RaceRecords.Archived archived : this.records.archive(course.id)) {
                layouts.add(archived.layout());
            }
            this.entryGhosts.archive(course.key(), archivedLayout, layouts);
        }
        course.layout++;
        this.boardChanged(course);
        RaceDataEvents.changed(this, course, ChangeKind.LAYOUT, archivedLayout);
        MinecraftServer current = this.server;
        if (current == null) {
            return;
        }
        Component message = Component.translatable("propwash.race.server.leaderboard_archived", ref(course), archivedLayout)
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
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
        if (record == null || !course.dimension.equals(dimensionOf(gate.level())) || !reshaped(gate, record)) {
            this.reconcile(gate);
            return;
        }
        for (GateRecord each : this.records(course, uuid)) {
            copyPose(gate, each);
        }
        this.layoutChanged(course);
        this.dirty = true;
        this.broadcast(course);
    }

    public boolean restore(GateEntity gate) {
        UUID uuid = gate.getUUID();
        Integer courseId = this.gateCourse.get(uuid);
        Course course = courseId == null ? null : this.courses.get(courseId);
        GateRecord record = course == null ? null : this.record(course, uuid);
        if (record == null || !course.dimension.equals(dimensionOf(gate.level())) || Math.abs(gate.getX() - record.x) <= MOVE_EPSILON
                && Math.abs(gate.getY() - record.y) <= MOVE_EPSILON && Math.abs(gate.getZ() - record.z) <= MOVE_EPSILON) {
            return false;
        }
        Constants.LOG.info("Kurs {} ({}): {} {} stand bei {} statt an der Lage aus den Kursdaten {} und ist zurückgesetzt", course.id,
                ref(course).getString(), record.kind().key(), uuid, point(gate.getX(), gate.getY(), gate.getZ()), point(record.x, record.y, record.z));
        gate.restoreTo(record.x, record.y, record.z);
        return true;
    }

    private static String point(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", x, y, z);
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
            List<CourseOrder.Pass> passes = this.passes(course, uuid);
            if (gate.courseId() != course.id || !gate.passes().equals(passes)) {
                gate.setPasses(course.id, passes);
            }
            if (moved(gate, record) || !course.dimension.equals(dimensionOf(gate.level()))) {
                if (!course.dimension.equals(dimensionOf(gate.level()))) {
                    this.removeGate(uuid);
                    gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
                    return;
                }
                for (GateRecord each : this.records(course, uuid)) {
                    copyPose(gate, each);
                }
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
        course.assignKey(this.freshKey());
        course.dimension = dimensionOf(gate.level());
        this.courses.put(claimed, course);
        this.nextCourseId = claimed + 1;
        List<CourseOrder.Pass> claimedPasses = gate.passes().isEmpty() ? List.of(new CourseOrder.Pass(0, false)) : gate.passes();
        for (CourseOrder.Pass pass : claimedPasses) {
            GateRecord record = new GateRecord();
            record.uuid = uuid.toString();
            record.index = Math.max(0, pass.index());
            record.reverse = pass.reverse() ? Boolean.TRUE : null;
            copyPose(gate, record);
            course.gates.add(record);
        }
        course.gates.sort(Comparator.comparingInt(g -> g.index));
        this.gateCourse.put(uuid, claimed);
        this.dirty = true;
        this.broadcast(course);
        RaceDataEvents.created(this, course);
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
        return turned(gate, record) || shifted(gate, record);
    }

    private static boolean reshaped(GateEntity gate, GateRecord record) {
        return turned(gate, record) && CourseData.yawShapesLayout(gate.kind(), gate.region()) || shifted(gate, record);
    }

    private static boolean turned(GateEntity gate, GateRecord record) {
        return Math.abs(gate.facingYaw() - record.yaw) > 0.01F;
    }

    private static boolean shifted(GateEntity gate, GateRecord record) {
        return Math.abs(gate.getX() - record.x) > MOVE_EPSILON || Math.abs(gate.getY() - record.y) > MOVE_EPSILON
                || Math.abs(gate.getZ() - record.z) > MOVE_EPSILON
                || !gate.size().key().equals(record.size) || gate.mount() != record.mount() || gate.flipped() != record.flipped
                || !gate.tilt().equals(record.tilt()) || gate.kind() != record.kind() || gate.variant() != record.variant()
                || !gate.score().forCourse(gate.kind()).equals(record.scoreArea())
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
        record.score = Score.of(gate.score(), kind);
        if (kind == CourseElement.FREEFORM) {
            record.width = 0.0;
            record.height = 0.0;
        }
    }

    private @Nullable GateEntity loadedGate(Course course, @Nullable UUID uuid) {
        if (uuid == null || this.server == null) {
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
        return this.submitLap(course, player.getUUID(), player.getGameProfile().name(), frame, lapNanos, System.currentTimeMillis());
    }

    public Leaderboard.Outcome submitLap(Course course, UUID player, String name, String frame, long lapNanos, long now) {
        Leaderboard board = this.board(course);
        RaceDataEvents.Before before = RaceDataEvents.before(board, player, frame, false);
        Leaderboard.Outcome outcome = board.submit(player, name, frame, lapNanos, now);
        this.records.changed();
        this.pruneGhosts(course);
        if (outcome.record()) {
            this.boardChanged(course);
        }
        RaceDataMirror.touch(this, course.id);
        RaceDataEvents.lapSaved(this, course, player, frame, false, outcome.improved(), outcome == Leaderboard.Outcome.COURSE_RECORD, before);
        return outcome;
    }

    public Leaderboard.Result submitTime(Course course, ServerPlayer player, String frame, long lapNanos, AssistReasons fixed, RaceRuleSet rules,
                                         boolean keepAssisted) {
        return this.submitTime(course, player.getUUID(), player.getGameProfile().name(), frame, lapNanos, System.currentTimeMillis(), fixed, rules,
                keepAssisted);
    }

    public Leaderboard.Result submitTime(Course course, UUID player, String name, String frame, long lapNanos, long now, AssistReasons fixed,
                                         RaceRuleSet rules, boolean keepAssisted) {
        return this.submitTime(course, player, name, frame, lapNanos, now, fixed, rules, keepAssisted, null);
    }

    public Leaderboard.Result submitTime(Course course, UUID player, String name, String frame, long lapNanos, long now, AssistReasons fixed,
                                         RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server) {
        return this.submitTime(course, player, name, frame, lapNanos, now, fixed, rules, keepAssisted, server, false);
    }

    public Leaderboard.Result submitTime(Course course, UUID player, String name, String frame, long lapNanos, long now, AssistReasons fixed,
                                         RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server, boolean creativeFc) {
        Leaderboard board = this.board(course);
        boolean assisted = !board.scoring().clean(fixed.mask(), rules, server);
        RaceDataEvents.Before before = RaceDataEvents.before(board, player, frame, assisted);
        Leaderboard.Result result = board.submitTime(player, name, frame, lapNanos, now, fixed, rules, keepAssisted, server, creativeFc);
        this.records.changed();
        this.pruneGhosts(course);
        if (result.outcome().record()) {
            this.boardChanged(course);
        }
        RaceDataMirror.touch(this, course.id);
        RaceDataEvents.lapSaved(this, course, player, frame, assisted, result.improved(), result.outcome() == Leaderboard.Outcome.COURSE_RECORD,
                before);
        return result;
    }

    public Leaderboard.Result submitRaceTime(Course course, ServerPlayer player, int laps, long totalNanos, String frame, AssistReasons fixed,
                                             RaceRuleSet rules, boolean keepAssisted) {
        return this.submitRaceTime(course, player, laps, totalNanos, frame, fixed, rules, keepAssisted, null);
    }

    public Leaderboard.Result submitRaceTime(Course course, ServerPlayer player, int laps, long totalNanos, String frame, AssistReasons fixed,
                                             RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server) {
        return this.submitRaceTime(course, player, laps, totalNanos, frame, fixed, rules, keepAssisted, server, false);
    }

    public Leaderboard.Result submitRaceTime(Course course, ServerPlayer player, int laps, long totalNanos, String frame, AssistReasons fixed,
                                             RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server, boolean creativeFc) {
        return this.submitRaceTime(course, player.getUUID(), player.getGameProfile().name(), laps, totalNanos, frame, System.currentTimeMillis(), fixed,
                rules, keepAssisted, server, creativeFc);
    }

    public Leaderboard.Result submitRaceTime(Course course, UUID player, String name, int laps, long totalNanos, String frame, long now,
                                             AssistReasons fixed, RaceRuleSet rules, boolean keepAssisted) {
        return this.submitRaceTime(course, player, name, laps, totalNanos, frame, now, fixed, rules, keepAssisted, null);
    }

    public Leaderboard.Result submitRaceTime(Course course, UUID player, String name, int laps, long totalNanos, String frame, long now,
                                             AssistReasons fixed, RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server) {
        return this.submitRaceTime(course, player, name, laps, totalNanos, frame, now, fixed, rules, keepAssisted, server, false);
    }

    public Leaderboard.Result submitRaceTime(Course course, UUID player, String name, int laps, long totalNanos, String frame, long now,
                                             AssistReasons fixed, RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server,
                                             boolean creativeFc) {
        Leaderboard board = this.board(course);
        boolean assisted = !board.scoring().clean(fixed.mask(), rules, server);
        long previous = LapTracker.NONE;
        if (assisted) {
            Leaderboard.AssistedRace race = board.assistedRaceBest(player, laps);
            previous = race == null ? LapTracker.NONE : race.nanos();
        } else {
            previous = board.raceBest(player, laps);
        }
        Leaderboard.Result result = board.submitRaceTime(player, laps, totalNanos, name, frame, now, fixed, rules, keepAssisted, server, creativeFc);
        this.records.changed();
        RaceDataMirror.touch(this, course.id);
        if (result.improved()) {
            RaceDataEvents.raceSaved(this, course, player, laps, assisted, previous);
        }
        return result;
    }

    public boolean submitRace(Course course, ServerPlayer player, int laps, long totalNanos) {
        return this.submitRace(course, player, laps, totalNanos, "");
    }

    public boolean submitRace(Course course, ServerPlayer player, int laps, long totalNanos, String frame) {
        return this.submitRace(course, player.getUUID(), player.getGameProfile().name(), laps, totalNanos, frame, System.currentTimeMillis());
    }

    public boolean submitRace(Course course, UUID player, String name, int laps, long totalNanos, String frame, long now) {
        Leaderboard board = this.board(course);
        long previous = board.raceBest(player, laps);
        boolean best = board.submitRace(player, laps, totalNanos, name, frame, now);
        this.records.changed();
        RaceDataMirror.touch(this, course.id);
        if (best) {
            RaceDataEvents.raceSaved(this, course, player, laps, false, previous);
        }
        return best;
    }

    public boolean submitAssistedLap(Course course, ServerPlayer player, String frame, long lapNanos, AssistReasons assist) {
        return this.submitAssistedLap(course, player.getUUID(), player.getGameProfile().name(), frame, lapNanos, System.currentTimeMillis(), assist);
    }

    public boolean submitAssistedLap(Course course, UUID player, String name, String frame, long lapNanos, long now, AssistReasons assist) {
        Leaderboard board = this.board(course);
        RaceDataEvents.Before before = RaceDataEvents.before(board, player, frame, true);
        boolean best = board.submitAssisted(player, name, frame, lapNanos, now, assist);
        this.records.changed();
        this.pruneGhosts(course);
        RaceDataMirror.touch(this, course.id);
        RaceDataEvents.lapSaved(this, course, player, frame, true, best, false, before);
        return best;
    }

    public boolean submitAssistedRace(Course course, ServerPlayer player, int laps, long totalNanos, AssistReasons assist) {
        return this.submitAssistedRace(course, player, laps, totalNanos, assist, "");
    }

    public boolean submitAssistedRace(Course course, ServerPlayer player, int laps, long totalNanos, AssistReasons assist, String frame) {
        return this.submitAssistedRace(course, player.getUUID(), player.getGameProfile().name(), laps, totalNanos, assist, frame,
                System.currentTimeMillis());
    }

    public boolean submitAssistedRace(Course course, UUID player, String name, int laps, long totalNanos, AssistReasons assist, String frame,
                                      long now) {
        Leaderboard board = this.board(course);
        Leaderboard.AssistedRace before = board.assistedRaceBest(player, laps);
        boolean best = board.submitAssistedRace(player, laps, totalNanos, assist, name, frame, now);
        this.records.changed();
        RaceDataMirror.touch(this, course.id);
        if (best) {
            RaceDataEvents.raceSaved(this, course, player, laps, true, before == null ? LapTracker.NONE : before.nanos());
        }
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
            RaceDataMirror.touch(this, course.id);
        }
        if (playerName == null) {
            this.ghosts.removeCourse(course.id);
        }
        this.pruneGhosts(course);
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
        RaceDataMirror.touch(this, course.id);
        this.sendToAll(new RacePayloads.CourseSyncS2C(false, List.of(course.toData()), List.of()));
    }

    private void sendToAll(RacePayloads.CourseSyncS2C payload) {
        MinecraftServer current = this.server;
        if (current == null) {
            return;
        }
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            PropwashNet.sendToPlayer(player, payload);
        }
    }

    public @Nullable MinecraftServer server() {
        return this.server;
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
