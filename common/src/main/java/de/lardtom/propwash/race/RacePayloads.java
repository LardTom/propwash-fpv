package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RacePayloads {
    public static final int MAX_COURSES = 512;

    private RacePayloads() {
    }

    public record CourseSyncS2C(boolean replaceAll, List<CourseData> courses, List<Integer> removed) implements CustomPacketPayload {
        public static final Type<CourseSyncS2C> TYPE = new Type<>(Constants.id("race_courses"));
        public static final StreamCodec<FriendlyByteBuf, CourseSyncS2C> CODEC = StreamCodec.of(CourseSyncS2C::write, CourseSyncS2C::read);

        public CourseSyncS2C {
            courses = List.copyOf(courses);
            removed = List.copyOf(removed);
        }

        private static void write(FriendlyByteBuf buf, CourseSyncS2C payload) {
            buf.writeBoolean(payload.replaceAll);
            buf.writeVarInt(payload.courses.size());
            for (CourseData course : payload.courses) {
                CourseData.STREAM_CODEC.encode(buf, course);
            }
            buf.writeVarInt(payload.removed.size());
            for (int id : payload.removed) {
                buf.writeVarInt(id);
            }
        }

        private static CourseSyncS2C read(FriendlyByteBuf buf) {
            boolean replaceAll = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_COURSES) {
                throw new DecoderException("Zu viele Kurse: " + count);
            }
            List<CourseData> courses = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                courses.add(CourseData.STREAM_CODEC.decode(buf));
            }
            int removedCount = buf.readVarInt();
            if (removedCount < 0 || removedCount > MAX_COURSES) {
                throw new DecoderException("Zu viele entfernte Kurse: " + removedCount);
            }
            List<Integer> removed = new ArrayList<>(removedCount);
            for (int i = 0; i < removedCount; i++) {
                removed.add(buf.readVarInt());
            }
            return new CourseSyncS2C(replaceAll, courses, removed);
        }

        @Override
        public Type<CourseSyncS2C> type() {
            return TYPE;
        }
    }

    public enum ReportKind {
        START,
        LAP,
        FINISH,
        ABORT;

        public static ReportKind byOrdinal(int ordinal) {
            ReportKind[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ABORT;
        }
    }

    public record RaceReportC2S(ReportKind kind, int courseId, int lap, long lapNanos, long totalNanos, int flags, long penaltyNanos,
                                List<AssistDetail> details)
            implements CustomPacketPayload {
        public static final int FLAG_ASSISTED = 1;
        public static final int ASSIST_SHIFT = 1;
        public static final int MAX_DETAILS = AssistReasons.MAX_DETAILS;
        public static final Type<RaceReportC2S> TYPE = new Type<>(Constants.id("race_report"));
        public static final StreamCodec<FriendlyByteBuf, RaceReportC2S> CODEC = StreamCodec.of(RaceReportC2S::write, RaceReportC2S::read);

        public RaceReportC2S {
            details = details == null ? List.of() : List.copyOf(details.size() > MAX_DETAILS ? details.subList(0, MAX_DETAILS) : details);
        }

        public RaceReportC2S(ReportKind kind, int courseId, int lap, long lapNanos, long totalNanos, int flags, long penaltyNanos) {
            this(kind, courseId, lap, lapNanos, totalNanos, flags, penaltyNanos, List.of());
        }

        public RaceReportC2S(ReportKind kind, int courseId, int lap, long lapNanos, long totalNanos) {
            this(kind, courseId, lap, lapNanos, totalNanos, 0, 0L);
        }

        public static int assistFlags(int mask) {
            int reasons = mask & LapAssist.ALL;
            return reasons == LapAssist.NONE ? 0 : FLAG_ASSISTED | reasons << ASSIST_SHIFT;
        }

        public boolean assisted() {
            return (this.flags & FLAG_ASSISTED) != 0;
        }

        public int assistMask() {
            if (!this.assisted()) {
                return LapAssist.NONE;
            }
            int reasons = this.flags >>> ASSIST_SHIFT & LapAssist.ALL;
            return reasons == LapAssist.NONE ? LapAssist.OTHER.bit() : reasons;
        }

        public AssistReasons reasons() {
            return AssistReasons.of(this.assistMask(), this.details);
        }

        private static void write(FriendlyByteBuf buf, RaceReportC2S payload) {
            buf.writeByte(payload.kind.ordinal());
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.lap);
            buf.writeVarLong(payload.lapNanos);
            buf.writeVarLong(payload.totalNanos);
            buf.writeVarInt(payload.flags);
            buf.writeVarLong(payload.penaltyNanos);
            writeDetails(buf, payload.details);
        }

        private static RaceReportC2S read(FriendlyByteBuf buf) {
            return new RaceReportC2S(ReportKind.byOrdinal(buf.readByte()), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarLong(), readDetails(buf));
        }

        @Override
        public Type<RaceReportC2S> type() {
            return TYPE;
        }
    }

    static void writeDetails(FriendlyByteBuf buf, List<AssistDetail> details) {
        int count = Math.min(details.size(), AssistReasons.MAX_DETAILS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            AssistDetail detail = details.get(i);
            buf.writeByte(detail.reason().ordinal());
            buf.writeUtf(detail.subject(), AssistDetail.MAX_SUBJECT);
            buf.writeVarInt(detail.value());
            buf.writeVarInt(detail.reference());
        }
    }

    static List<AssistDetail> readDetails(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > AssistReasons.MAX_DETAILS) {
            throw new DecoderException("Zu viele Flughilfe-Gründe: " + count);
        }
        List<AssistDetail> out = new ArrayList<>(count);
        LapAssist[] reasons = LapAssist.values();
        for (int i = 0; i < count; i++) {
            int ordinal = buf.readUnsignedByte();
            String subject = buf.readUtf(AssistDetail.MAX_SUBJECT);
            int value = buf.readVarInt();
            int reference = buf.readVarInt();
            out.add(new AssistDetail(ordinal < reasons.length ? reasons[ordinal] : LapAssist.OTHER, subject, value, reference));
        }
        return out;
    }

    public record RaceSettingsS2C(int laps) implements CustomPacketPayload {
        public static final Type<RaceSettingsS2C> TYPE = new Type<>(Constants.id("race_settings"));
        public static final StreamCodec<ByteBuf, RaceSettingsS2C> CODEC = ByteBufCodecs.VAR_INT.map(RaceSettingsS2C::new, RaceSettingsS2C::laps);

        @Override
        public Type<RaceSettingsS2C> type() {
            return TYPE;
        }
    }

    public record LeaderboardRequestC2S(int courseId) implements CustomPacketPayload {
        public static final Type<LeaderboardRequestC2S> TYPE = new Type<>(Constants.id("race_leaderboard_request"));
        public static final StreamCodec<ByteBuf, LeaderboardRequestC2S> CODEC = ByteBufCodecs.VAR_INT.map(LeaderboardRequestC2S::new,
                LeaderboardRequestC2S::courseId);

        @Override
        public Type<LeaderboardRequestC2S> type() {
            return TYPE;
        }
    }

    public record BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank, int assist,
                           List<AssistDetail> details, UUID player, String code, boolean ghost, @Nullable RaceStandard server) {
        public static final int MAX_DETAILS = AssistReasons.MAX_DETAILS;
        public static final UUID NO_PLAYER = new UUID(0L, 0L);

        public BoardRow {
            assist = assist & LapAssist.ALL;
            List<AssistDetail> kept = AssistReasons.of(assist, details).details();
            details = kept.size() > MAX_DETAILS ? List.copyOf(kept.subList(0, MAX_DETAILS)) : kept;
            player = player == null ? NO_PLAYER : player;
            code = code == null ? "" : code.length() > EntryGhost.MAX_CODE ? "" : code;
        }

        public BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank, int assist,
                        List<AssistDetail> details, UUID player, String code, boolean ghost) {
            this(name, frame, nanos, dateMillis, self, classRank, overallRank, assist, details, player, code, ghost, null);
        }

        public BoardRow withServer(@Nullable RaceStandard value) {
            return new BoardRow(this.name, this.frame, this.nanos, this.dateMillis, this.self, this.classRank, this.overallRank, this.assist, this.details,
                    this.player, this.code, this.ghost, value);
        }

        public BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank, int assist,
                        List<AssistDetail> details) {
            this(name, frame, nanos, dateMillis, self, classRank, overallRank, assist, details, NO_PLAYER, "", false);
        }

        public GhostKey key() {
            return new GhostKey(this.player, this.frame, this.nanos, this.dateMillis);
        }

        public BoardRow withGhost(UUID player, String code, boolean ghost) {
            return new BoardRow(this.name, this.frame, this.nanos, this.dateMillis, this.self, this.classRank, this.overallRank, this.assist, this.details,
                    player, code, ghost, this.server);
        }

        public BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank, int assist) {
            this(name, frame, nanos, dateMillis, self, classRank, overallRank, assist, List.of());
        }

        public BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank) {
            this(name, frame, nanos, dateMillis, self, classRank, overallRank, LapAssist.NONE);
        }

        public boolean assisted() {
            return this.assist != LapAssist.NONE;
        }

        public AssistReasons reasons() {
            return AssistReasons.of(this.assist, this.details);
        }

        public int rank(@Nullable String frame) {
            return frame == null ? this.overallRank : this.frame.equals(frame) ? this.classRank : -1;
        }

        public int bytes() {
            return 64 + 3 * (this.name.length() + this.frame.length() + this.code.length()) + 8 * this.details.size()
                    + (this.server == null ? 0 : 24 * this.server.values().size());
        }

        static void write(FriendlyByteBuf buf, BoardRow row) {
            buf.writeUtf(row.name, Leaderboard.MAX_NAME);
            buf.writeUtf(row.frame, Leaderboard.MAX_FRAME);
            buf.writeVarLong(row.nanos);
            buf.writeLong(row.dateMillis);
            buf.writeBoolean(row.self);
            buf.writeVarInt(row.classRank + 1);
            buf.writeVarInt(row.overallRank + 1);
            buf.writeVarInt(row.assist & LapAssist.ALL);
            writeDetails(buf, row.details);
            buf.writeUUID(row.player);
            buf.writeUtf(row.code, EntryGhost.MAX_CODE);
            buf.writeBoolean(row.ghost);
            buf.writeBoolean(row.server != null);
            if (row.server != null) {
                row.server.write(buf);
            }
        }

        static BoardRow read(FriendlyByteBuf buf) {
            return new BoardRow(buf.readUtf(Leaderboard.MAX_NAME), buf.readUtf(Leaderboard.MAX_FRAME), buf.readVarLong(), buf.readLong(), buf.readBoolean(),
                    buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() & LapAssist.ALL, readDetails(buf), buf.readUUID(),
                    buf.readUtf(EntryGhost.MAX_CODE), buf.readBoolean(), buf.readBoolean() ? RaceStandard.read(buf) : null);
        }
    }

    public record ClassTotal(String frame, int clean, int assisted) {
        public ClassTotal {
            frame = frame == null ? "" : frame;
            clean = Math.clamp(clean, 0, BoardTotals.MAX_TIMES);
            assisted = Math.clamp(assisted, 0, BoardTotals.MAX_TIMES);
        }
    }

    public record BoardTotals(int revision, int clean, int assisted, List<ClassTotal> classes) {
        public static final int MAX_CLASSES = 512;
        public static final int MAX_TIMES = 1_000_000;

        public BoardTotals {
            clean = Math.clamp(clean, 0, MAX_TIMES);
            assisted = Math.clamp(assisted, 0, MAX_TIMES);
            classes = List.copyOf(classes);
        }

        public static BoardTotals of(List<BoardRow> rows) {
            int clean = 0;
            int assisted = 0;
            Map<String, int[]> classes = new LinkedHashMap<>();
            for (BoardRow row : rows) {
                int[] count = classes.computeIfAbsent(row.frame(), frame -> new int[2]);
                if (row.assisted()) {
                    assisted = Math.max(assisted, row.overallRank());
                    count[1] = Math.max(count[1], row.classRank());
                } else {
                    clean = Math.max(clean, row.overallRank());
                    count[0] = Math.max(count[0], row.classRank());
                }
            }
            List<ClassTotal> list = new ArrayList<>();
            classes.forEach((frame, count) -> list.add(new ClassTotal(frame, count[0], count[1])));
            return new BoardTotals(0, clean, assisted, list);
        }

        public boolean paged() {
            return this.revision != 0;
        }

        public int total(@Nullable String frame, boolean assisted) {
            if (frame == null) {
                return assisted ? this.assisted : this.clean;
            }
            for (ClassTotal total : this.classes) {
                if (total.frame().equals(frame)) {
                    return assisted ? total.assisted() : total.clean();
                }
            }
            return 0;
        }

        static void write(FriendlyByteBuf buf, BoardTotals totals) {
            buf.writeVarInt(totals.revision);
            buf.writeVarInt(totals.clean);
            buf.writeVarInt(totals.assisted);
            buf.writeVarInt(totals.classes.size());
            for (ClassTotal total : totals.classes) {
                buf.writeUtf(total.frame(), Leaderboard.MAX_FRAME);
                buf.writeVarInt(total.clean());
                buf.writeVarInt(total.assisted());
            }
        }

        static BoardTotals read(FriendlyByteBuf buf) {
            int revision = buf.readVarInt();
            int clean = buf.readVarInt();
            int assisted = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_CLASSES) {
                throw new DecoderException("Zu viele Klassen in der Bestenliste: " + count);
            }
            List<ClassTotal> classes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                classes.add(new ClassTotal(buf.readUtf(Leaderboard.MAX_FRAME), buf.readVarInt(), buf.readVarInt()));
            }
            return new BoardTotals(revision, clean, assisted, classes);
        }
    }

    public record LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows, int raceLaps, long raceBest,
                                 int archived, int access, int times, BoardTotals totals)
            implements CustomPacketPayload {
        public static final int MAX_ROWS = 256;
        public static final Type<LeaderboardS2C> TYPE = new Type<>(Constants.id("race_leaderboard"));
        public static final StreamCodec<FriendlyByteBuf, LeaderboardS2C> CODEC = StreamCodec.of(LeaderboardS2C::write, LeaderboardS2C::read);

        public LeaderboardS2C {
            rows = List.copyOf(rows);
            raceLaps = CourseLaps.clamp(raceLaps);
            raceBest = raceBest > 0L ? raceBest : LapTracker.NONE;
            archived = Math.max(0, archived);
            access = access & (GhostAccess.REPLAY | GhostAccess.OPPONENT | GhostAccess.REMOVE);
            times = Math.max(times, rows.size());
            totals = totals == null ? BoardTotals.of(rows) : totals;
        }

        public LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows, int raceLaps, long raceBest,
                              int archived, int access, int times) {
            this(courseId, layout, gates, open, laps, rows, raceLaps, raceBest, archived, access, times, null);
        }

        public LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows, int raceLaps, long raceBest,
                              int archived, int access) {
            this(courseId, layout, gates, open, laps, rows, raceLaps, raceBest, archived, access, 0);
        }

        public LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows, int raceLaps, long raceBest,
                              int archived) {
            this(courseId, layout, gates, open, laps, rows, raceLaps, raceBest, archived, 0);
        }

        public LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows) {
            this(courseId, layout, gates, open, laps, rows, CourseLaps.ENDLESS, LapTracker.NONE, 0);
        }

        public boolean mayReplayOthers() {
            return (this.access & GhostAccess.REPLAY) != 0;
        }

        public boolean mayRaceOthers() {
            return (this.access & GhostAccess.OPPONENT) != 0;
        }

        public boolean mayRemove() {
            return (this.access & GhostAccess.REMOVE) != 0;
        }

        public boolean hasRaceBest() {
            return this.raceLaps > CourseLaps.ENDLESS && this.raceBest > 0L;
        }

        public int revision() {
            return this.totals.revision();
        }

        public int total(@Nullable String frame, boolean assisted) {
            return this.totals.total(frame, assisted);
        }

        public LeaderboardS2C withRows(List<BoardRow> next) {
            return new LeaderboardS2C(this.courseId, this.layout, this.gates, this.open, this.laps, next, this.raceLaps, this.raceBest, this.archived,
                    this.access, this.times, this.totals);
        }

        public LeaderboardS2C merge(LeaderboardPageS2C page) {
            if (page.courseId() != this.courseId || !this.totals.paged() || page.revision() != this.totals.revision() || page.rows().isEmpty()) {
                return this;
            }
            Map<String, BoardRow> merged = new LinkedHashMap<>();
            for (BoardRow row : this.rows) {
                merged.put(identity(row), row);
            }
            for (BoardRow row : page.rows()) {
                merged.put(identity(row), row);
            }
            return this.withRows(new ArrayList<>(merged.values()));
        }

        private static String identity(BoardRow row) {
            return (row.assisted() ? "a|" : "c|") + row.classRank() + "|" + row.frame();
        }

        private static void write(FriendlyByteBuf buf, LeaderboardS2C payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.layout);
            buf.writeVarInt(payload.gates);
            buf.writeBoolean(payload.open);
            buf.writeVarInt(payload.laps);
            buf.writeVarInt(payload.raceLaps);
            buf.writeVarLong(Math.max(0L, payload.raceBest));
            buf.writeVarInt(payload.archived);
            buf.writeVarInt(payload.access);
            buf.writeVarInt(payload.times);
            BoardTotals.write(buf, payload.totals);
            buf.writeVarInt(payload.rows.size());
            for (BoardRow row : payload.rows) {
                BoardRow.write(buf, row);
            }
        }

        private static LeaderboardS2C read(FriendlyByteBuf buf) {
            int course = buf.readVarInt();
            int layout = buf.readVarInt();
            int gates = buf.readVarInt();
            boolean open = buf.readBoolean();
            int laps = buf.readVarInt();
            int raceLaps = buf.readVarInt();
            long raceBest = buf.readVarLong();
            int archived = buf.readVarInt();
            int access = buf.readVarInt();
            int times = buf.readVarInt();
            BoardTotals totals = BoardTotals.read(buf);
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ROWS) {
                throw new DecoderException("Zu viele Zeilen in der Bestenliste: " + count);
            }
            List<BoardRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                rows.add(BoardRow.read(buf));
            }
            return new LeaderboardS2C(course, layout, gates, open, laps, rows, raceLaps, raceBest, archived, access, times, totals);
        }

        @Override
        public Type<LeaderboardS2C> type() {
            return TYPE;
        }
    }

    public record LeaderboardPageC2S(int courseId, int revision, boolean all, String frame, boolean assisted, int offset) implements CustomPacketPayload {
        public static final Type<LeaderboardPageC2S> TYPE = new Type<>(Constants.id("race_leaderboard_page_request"));
        public static final StreamCodec<FriendlyByteBuf, LeaderboardPageC2S> CODEC = StreamCodec.of(LeaderboardPageC2S::write, LeaderboardPageC2S::read);

        public LeaderboardPageC2S {
            frame = all || frame == null ? "" : Leaderboard.clipFrame(frame);
            offset = Math.max(0, offset);
        }

        public static LeaderboardPageC2S of(int courseId, int revision, @Nullable String frame, boolean assisted, int offset) {
            return new LeaderboardPageC2S(courseId, revision, frame == null, frame, assisted, offset);
        }

        public @Nullable String view() {
            return this.all ? null : this.frame;
        }

        private static void write(FriendlyByteBuf buf, LeaderboardPageC2S payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.revision);
            buf.writeBoolean(payload.all);
            buf.writeUtf(payload.frame, Leaderboard.MAX_FRAME);
            buf.writeBoolean(payload.assisted);
            buf.writeVarInt(payload.offset);
        }

        private static LeaderboardPageC2S read(FriendlyByteBuf buf) {
            return new LeaderboardPageC2S(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(Leaderboard.MAX_FRAME), buf.readBoolean(),
                    buf.readVarInt());
        }

        @Override
        public Type<LeaderboardPageC2S> type() {
            return TYPE;
        }
    }

    public record LeaderboardPageS2C(int courseId, int revision, List<BoardRow> rows) implements CustomPacketPayload {
        public static final int PAGE = 50;
        public static final int MAX_BYTES = 512 * 1024;
        public static final Type<LeaderboardPageS2C> TYPE = new Type<>(Constants.id("race_leaderboard_page"));
        public static final StreamCodec<FriendlyByteBuf, LeaderboardPageS2C> CODEC = StreamCodec.of(LeaderboardPageS2C::write, LeaderboardPageS2C::read);

        public LeaderboardPageS2C {
            rows = List.copyOf(rows);
        }

        private static void write(FriendlyByteBuf buf, LeaderboardPageS2C payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.revision);
            buf.writeVarInt(payload.rows.size());
            for (BoardRow row : payload.rows) {
                BoardRow.write(buf, row);
            }
        }

        private static LeaderboardPageS2C read(FriendlyByteBuf buf) {
            int course = buf.readVarInt();
            int revision = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > PAGE) {
                throw new DecoderException("Zu viele Zeilen auf einer Seite der Bestenliste: " + count);
            }
            List<BoardRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                rows.add(BoardRow.read(buf));
            }
            return new LeaderboardPageS2C(course, revision, rows);
        }

        @Override
        public Type<LeaderboardPageS2C> type() {
            return TYPE;
        }
    }

    public record GhostFetchC2S(int request, int courseId, UUID player, String frame, long nanos, long dateMillis, GhostAccess.Purpose purpose)
            implements CustomPacketPayload {
        public static final Type<GhostFetchC2S> TYPE = new Type<>(Constants.id("race_ghost_fetch"));
        public static final StreamCodec<FriendlyByteBuf, GhostFetchC2S> CODEC = StreamCodec.of(GhostFetchC2S::write, GhostFetchC2S::read);

        public GhostFetchC2S {
            frame = frame == null ? "" : frame;
            purpose = purpose == null ? GhostAccess.Purpose.REPLAY : purpose;
        }

        public static GhostFetchC2S of(int request, int courseId, GhostKey key, GhostAccess.Purpose purpose) {
            return new GhostFetchC2S(request, courseId, key.player(), key.frame(), key.nanos(), key.dateMillis(), purpose);
        }

        public GhostKey key() {
            return new GhostKey(this.player, this.frame, this.nanos, this.dateMillis);
        }

        private static void write(FriendlyByteBuf buf, GhostFetchC2S payload) {
            buf.writeVarInt(payload.request);
            buf.writeVarInt(payload.courseId);
            buf.writeUUID(payload.player);
            buf.writeUtf(payload.frame, Leaderboard.MAX_FRAME);
            buf.writeVarLong(payload.nanos);
            buf.writeLong(payload.dateMillis);
            buf.writeVarInt(payload.purpose.ordinal());
        }

        private static GhostFetchC2S read(FriendlyByteBuf buf) {
            return new GhostFetchC2S(buf.readVarInt(), buf.readVarInt(), buf.readUUID(), buf.readUtf(Leaderboard.MAX_FRAME), buf.readVarLong(), buf.readLong(),
                    GhostAccess.Purpose.of(buf.readVarInt()));
        }

        @Override
        public Type<GhostFetchC2S> type() {
            return TYPE;
        }
    }

    public enum GhostStatus {
        DATA,
        MISSING,
        DENIED,
        BUSY;

        public static GhostStatus byOrdinal(int ordinal) {
            GhostStatus[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MISSING;
        }
    }

    public record GhostChunkS2C(int request, int courseId, GhostStatus status, int part, int parts, int total, byte[] data) implements CustomPacketPayload {
        public static final int CHUNK = 24 * 1024;
        public static final int MAX_PARTS = (EntryGhost.MAX_BYTES + CHUNK - 1) / CHUNK;
        public static final Type<GhostChunkS2C> TYPE = new Type<>(Constants.id("race_ghost_chunk"));
        public static final StreamCodec<FriendlyByteBuf, GhostChunkS2C> CODEC = StreamCodec.of(GhostChunkS2C::write, GhostChunkS2C::read);

        public GhostChunkS2C {
            status = status == null ? GhostStatus.MISSING : status;
            data = data == null ? new byte[0] : data;
        }

        public static GhostChunkS2C refused(int request, int courseId, GhostStatus status) {
            return new GhostChunkS2C(request, courseId, status, 0, 0, 0, new byte[0]);
        }

        private static void write(FriendlyByteBuf buf, GhostChunkS2C payload) {
            buf.writeVarInt(payload.request);
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.status.ordinal());
            buf.writeVarInt(payload.part);
            buf.writeVarInt(payload.parts);
            buf.writeVarInt(payload.total);
            buf.writeByteArray(payload.data);
        }

        private static GhostChunkS2C read(FriendlyByteBuf buf) {
            int request = buf.readVarInt();
            int course = buf.readVarInt();
            GhostStatus status = GhostStatus.byOrdinal(buf.readVarInt());
            int part = buf.readVarInt();
            int parts = buf.readVarInt();
            int total = buf.readVarInt();
            if (parts < 0 || parts > MAX_PARTS || part < 0 || part > parts || total < 0 || total > EntryGhost.MAX_BYTES) {
                throw new DecoderException("Ungültiges Geister-Stück " + part + "/" + parts + " (" + total + " Bytes)");
            }
            return new GhostChunkS2C(request, course, status, part, parts, total, buf.readByteArray(CHUNK));
        }

        @Override
        public Type<GhostChunkS2C> type() {
            return TYPE;
        }
    }

    public record TimeRemoveC2S(int courseId, UUID player, String frame, boolean assisted) implements CustomPacketPayload {
        public static final Type<TimeRemoveC2S> TYPE = new Type<>(Constants.id("race_time_remove"));
        public static final StreamCodec<FriendlyByteBuf, TimeRemoveC2S> CODEC = StreamCodec.of(TimeRemoveC2S::write, TimeRemoveC2S::read);

        public TimeRemoveC2S {
            frame = frame == null ? "" : frame;
        }

        private static void write(FriendlyByteBuf buf, TimeRemoveC2S payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeUUID(payload.player);
            buf.writeUtf(payload.frame, Leaderboard.MAX_FRAME);
            buf.writeBoolean(payload.assisted);
        }

        private static TimeRemoveC2S read(FriendlyByteBuf buf) {
            return new TimeRemoveC2S(buf.readVarInt(), buf.readUUID(), buf.readUtf(Leaderboard.MAX_FRAME), buf.readBoolean());
        }

        @Override
        public Type<TimeRemoveC2S> type() {
            return TYPE;
        }
    }

    public record GhostUploadS2C(int token, int courseId, int lap, long lapNanos) implements CustomPacketPayload {
        public static final Type<GhostUploadS2C> TYPE = new Type<>(Constants.id("race_ghost_upload_request"));
        public static final StreamCodec<FriendlyByteBuf, GhostUploadS2C> CODEC = StreamCodec.of(GhostUploadS2C::write, GhostUploadS2C::read);

        private static void write(FriendlyByteBuf buf, GhostUploadS2C payload) {
            buf.writeVarInt(payload.token);
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.lap);
            buf.writeVarLong(payload.lapNanos);
        }

        private static GhostUploadS2C read(FriendlyByteBuf buf) {
            return new GhostUploadS2C(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
        }

        @Override
        public Type<GhostUploadS2C> type() {
            return TYPE;
        }
    }

    public record GhostUploadC2S(int token, int part, int parts, int total, byte[] data) implements CustomPacketPayload {
        public static final int CHUNK = 8 * 1024;
        public static final int MAX_BYTES = GhostCodec.MAX_ENCODED_BYTES;
        public static final int MAX_PARTS = (MAX_BYTES + CHUNK - 1) / CHUNK;
        public static final Type<GhostUploadC2S> TYPE = new Type<>(Constants.id("race_ghost_upload"));
        public static final StreamCodec<FriendlyByteBuf, GhostUploadC2S> CODEC = StreamCodec.of(GhostUploadC2S::write, GhostUploadC2S::read);

        public GhostUploadC2S {
            data = data == null ? new byte[0] : data;
        }

        public static GhostUploadC2S decline(int token) {
            return new GhostUploadC2S(token, 0, 0, 0, new byte[0]);
        }

        public boolean declined() {
            return this.parts == 0;
        }

        private static void write(FriendlyByteBuf buf, GhostUploadC2S payload) {
            buf.writeVarInt(payload.token);
            buf.writeVarInt(payload.part);
            buf.writeVarInt(payload.parts);
            buf.writeVarInt(payload.total);
            buf.writeByteArray(payload.data);
        }

        private static GhostUploadC2S read(FriendlyByteBuf buf) {
            int token = buf.readVarInt();
            int part = buf.readVarInt();
            int parts = buf.readVarInt();
            int total = buf.readVarInt();
            if (parts < 0 || parts > MAX_PARTS || part < 0 || part >= Math.max(1, parts) || total < 0 || total > MAX_BYTES) {
                throw new DecoderException("Ungültiges Stück einer Geisterspur " + part + "/" + parts + " (" + total + " Bytes)");
            }
            return new GhostUploadC2S(token, part, parts, total, buf.readByteArray(CHUNK));
        }

        @Override
        public Type<GhostUploadC2S> type() {
            return TYPE;
        }
    }

    public enum LobbyAction {
        JOIN,
        LEAVE;

        public static LobbyAction byOrdinal(int ordinal) {
            return ordinal == 0 ? JOIN : LEAVE;
        }
    }

    public record LobbyC2S(LobbyAction action, int courseId, int countdownSeconds) implements CustomPacketPayload {
        public static final Type<LobbyC2S> TYPE = new Type<>(Constants.id("race_lobby_action"));
        public static final StreamCodec<FriendlyByteBuf, LobbyC2S> CODEC = StreamCodec.of(LobbyC2S::write, LobbyC2S::read);

        private static void write(FriendlyByteBuf buf, LobbyC2S payload) {
            buf.writeByte(payload.action.ordinal());
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.countdownSeconds);
        }

        private static LobbyC2S read(FriendlyByteBuf buf) {
            return new LobbyC2S(LobbyAction.byOrdinal(buf.readByte()), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<LobbyC2S> type() {
            return TYPE;
        }
    }

    public record LobbyS2C(int courseId, boolean member, List<String> pilots, long remainingNanos) implements CustomPacketPayload {
        public static final int MAX_PILOTS = 32;
        public static final Type<LobbyS2C> TYPE = new Type<>(Constants.id("race_lobby"));
        public static final StreamCodec<FriendlyByteBuf, LobbyS2C> CODEC = StreamCodec.of(LobbyS2C::write, LobbyS2C::read);

        public LobbyS2C {
            pilots = List.copyOf(pilots);
        }

        private static void write(FriendlyByteBuf buf, LobbyS2C payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeBoolean(payload.member);
            buf.writeVarInt(payload.pilots.size());
            for (String pilot : payload.pilots) {
                buf.writeUtf(pilot, Leaderboard.MAX_NAME);
            }
            buf.writeVarLong(payload.remainingNanos);
        }

        private static LobbyS2C read(FriendlyByteBuf buf) {
            int course = buf.readVarInt();
            boolean member = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_PILOTS) {
                throw new DecoderException("Zu viele Piloten: " + count);
            }
            List<String> pilots = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                pilots.add(buf.readUtf(Leaderboard.MAX_NAME));
            }
            return new LobbyS2C(course, member, pilots, buf.readVarLong());
        }

        @Override
        public Type<LobbyS2C> type() {
            return TYPE;
        }
    }

    public record RaceStartS2C(int courseId, long countdownNanos, int pilots) implements CustomPacketPayload {
        public static final Type<RaceStartS2C> TYPE = new Type<>(Constants.id("race_start"));
        public static final StreamCodec<FriendlyByteBuf, RaceStartS2C> CODEC = StreamCodec.of(RaceStartS2C::write, RaceStartS2C::read);

        private static void write(FriendlyByteBuf buf, RaceStartS2C payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeVarLong(payload.countdownNanos);
            buf.writeVarInt(payload.pilots);
        }

        private static RaceStartS2C read(FriendlyByteBuf buf) {
            return new RaceStartS2C(buf.readVarInt(), buf.readVarLong(), buf.readVarInt());
        }

        @Override
        public Type<RaceStartS2C> type() {
            return TYPE;
        }
    }

    public record CourseEditC2S(int courseId, CourseEdits.Action action, int first, int second, String text) implements CustomPacketPayload {
        public static final int MAX_TEXT = CourseNames.MAX * 2;
        public static final Type<CourseEditC2S> TYPE = new Type<>(Constants.id("race_course_edit"));
        public static final StreamCodec<FriendlyByteBuf, CourseEditC2S> CODEC = StreamCodec.of(CourseEditC2S::write, CourseEditC2S::read);

        public CourseEditC2S {
            first = Math.max(0, first);
            second = Math.max(0, second);
            text = text == null ? "" : text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
        }

        public CourseEditC2S(int courseId, CourseEdits.Action action, int first, int second) {
            this(courseId, action, first, second, "");
        }

        public CourseEditC2S(int courseId, CourseEdits.Action action) {
            this(courseId, action, 0, 0, "");
        }

        public static CourseEditC2S rename(int courseId, String name) {
            return new CourseEditC2S(courseId, CourseEdits.Action.RENAME, 0, 0, name);
        }

        private static void write(FriendlyByteBuf buf, CourseEditC2S payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeByte(payload.action.ordinal());
            buf.writeVarInt(payload.first);
            buf.writeVarInt(payload.second);
            buf.writeUtf(payload.text, MAX_TEXT);
        }

        private static CourseEditC2S read(FriendlyByteBuf buf) {
            return new CourseEditC2S(buf.readVarInt(), CourseEdits.Action.byOrdinal(buf.readUnsignedByte()), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT));
        }

        @Override
        public Type<CourseEditC2S> type() {
            return TYPE;
        }
    }

    public enum LapResult {
        COUNTED,
        MISSED,
        REJECTED,
        RACE;

        private static final LapResult[] VALUES = values();

        public static LapResult byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : REJECTED;
        }
    }

    public record LapVerdictS2C(int courseId, int lap, LapResult result, int element, int counted) implements CustomPacketPayload {
        public static final int NO_ELEMENT = -1;
        public static final Type<LapVerdictS2C> TYPE = new Type<>(Constants.id("race_lap_verdict"));
        public static final StreamCodec<FriendlyByteBuf, LapVerdictS2C> CODEC = StreamCodec.of(LapVerdictS2C::write, LapVerdictS2C::read);

        public LapVerdictS2C {
            lap = Math.max(0, lap);
            result = result == null ? LapResult.REJECTED : result;
            element = Math.max(NO_ELEMENT, element);
            counted = Math.max(0, counted);
        }

        private static void write(FriendlyByteBuf buf, LapVerdictS2C payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.lap);
            buf.writeByte(payload.result.ordinal());
            buf.writeVarInt(payload.element + 1);
            buf.writeVarInt(payload.counted);
        }

        private static LapVerdictS2C read(FriendlyByteBuf buf) {
            return new LapVerdictS2C(buf.readVarInt(), buf.readVarInt(), LapResult.byOrdinal(buf.readUnsignedByte()), buf.readVarInt() - 1, buf.readVarInt());
        }

        @Override
        public Type<LapVerdictS2C> type() {
            return TYPE;
        }
    }

    public record CourseCreativeFcC2S(int courseId, CreativeFcRule rule) implements CustomPacketPayload {
        public static final Type<CourseCreativeFcC2S> TYPE = new Type<>(Constants.id("race_course_creative_fc"));
        public static final StreamCodec<FriendlyByteBuf, CourseCreativeFcC2S> CODEC = StreamCodec.of(CourseCreativeFcC2S::write,
                CourseCreativeFcC2S::read);

        public CourseCreativeFcC2S {
            rule = rule == null ? CreativeFcRule.WORLD : rule;
        }

        private static void write(FriendlyByteBuf buf, CourseCreativeFcC2S payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeByte(payload.rule.ordinal());
        }

        private static CourseCreativeFcC2S read(FriendlyByteBuf buf) {
            return new CourseCreativeFcC2S(buf.readVarInt(), CreativeFcRule.byOrdinal(buf.readUnsignedByte()));
        }

        @Override
        public Type<CourseCreativeFcC2S> type() {
            return TYPE;
        }
    }

    public record CourseDefaultsC2S(int laps) implements CustomPacketPayload {
        public static final Type<CourseDefaultsC2S> TYPE = new Type<>(Constants.id("race_course_defaults"));
        public static final StreamCodec<ByteBuf, CourseDefaultsC2S> CODEC = ByteBufCodecs.VAR_INT.map(CourseDefaultsC2S::new, CourseDefaultsC2S::laps);

        public CourseDefaultsC2S {
            laps = CourseLaps.clamp(laps);
        }

        @Override
        public Type<CourseDefaultsC2S> type() {
            return TYPE;
        }
    }

    public record GatePreferenceC2S(GatePlacement.Preference preference) implements CustomPacketPayload {
        public static final Type<GatePreferenceC2S> TYPE = new Type<>(Constants.id("race_gate_preference"));
        public static final StreamCodec<FriendlyByteBuf, GatePreferenceC2S> CODEC = StreamCodec.of(GatePreferenceC2S::write,
                GatePreferenceC2S::read);

        private static void write(FriendlyByteBuf buf, GatePreferenceC2S payload) {
            for (GateMount.Surface surface : GateMount.Surface.values()) {
                buf.writeByte(payload.preference.mount(surface).ordinal());
                buf.writeByte(payload.preference.turn(surface));
                buf.writeFloat(payload.preference.fine(surface));
            }
            buf.writeByte(payload.preference.variant());
        }

        private static GatePreferenceC2S read(FriendlyByteBuf buf) {
            GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT;
            for (GateMount.Surface surface : GateMount.Surface.values()) {
                GateMount mount = GateMount.byOrdinal(buf.readUnsignedByte());
                int turn = buf.readUnsignedByte();
                float fine = buf.readFloat();
                if (mount.surface() == surface) {
                    preference = preference.with(mount, turn, fine);
                }
            }
            return new GatePreferenceC2S(preference.withVariant(buf.readUnsignedByte()));
        }

        @Override
        public Type<GatePreferenceC2S> type() {
            return TYPE;
        }
    }

    public record GateRotateC2S(int entityId, boolean changeMount, float step) implements CustomPacketPayload {
        public static final Type<GateRotateC2S> TYPE = new Type<>(Constants.id("race_gate_rotate"));
        public static final StreamCodec<FriendlyByteBuf, GateRotateC2S> CODEC = StreamCodec.of(GateRotateC2S::write, GateRotateC2S::read);

        public GateRotateC2S(int entityId, boolean changeMount) {
            this(entityId, changeMount, GatePlacement.QUARTER);
        }

        private static void write(FriendlyByteBuf buf, GateRotateC2S payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeBoolean(payload.changeMount);
            buf.writeFloat(payload.step);
        }

        private static GateRotateC2S read(FriendlyByteBuf buf) {
            return new GateRotateC2S(buf.readVarInt(), buf.readBoolean(), GatePlacement.validStep(buf.readFloat()));
        }

        @Override
        public Type<GateRotateC2S> type() {
            return TYPE;
        }
    }

    public enum GateEditAction {
        APPLY,
        UNLINK,
        RESCAN
    }

    public record GateEditC2S(int entityId, GateEditAction action, float heading, float pitch, float roll, float openingWidth,
                              float openingHeight, int variant, int score, float dx, float dy, float dz) implements CustomPacketPayload {
        public static final Type<GateEditC2S> TYPE = new Type<>(Constants.id("race_gate_edit"));
        public static final StreamCodec<FriendlyByteBuf, GateEditC2S> CODEC = StreamCodec.of(GateEditC2S::write, GateEditC2S::read);

        public GateEditC2S(int entityId, GateEditAction action, float heading, float pitch, float roll, float openingWidth, float openingHeight) {
            this(entityId, action, heading, pitch, roll, openingWidth, openingHeight, 0);
        }

        public GateEditC2S(int entityId, GateEditAction action, float heading, float pitch, float roll, float openingWidth, float openingHeight,
                           int variant) {
            this(entityId, action, heading, pitch, roll, openingWidth, openingHeight, variant, ScoreArea.DEFAULT.pack(), 0.0F, 0.0F, 0.0F);
        }

        private static void write(FriendlyByteBuf buf, GateEditC2S payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeEnum(payload.action);
            buf.writeFloat(payload.heading);
            buf.writeFloat(payload.pitch);
            buf.writeFloat(payload.roll);
            buf.writeFloat(payload.openingWidth);
            buf.writeFloat(payload.openingHeight);
            buf.writeByte(payload.variant);
            buf.writeVarInt(payload.score);
            buf.writeFloat(payload.dx);
            buf.writeFloat(payload.dy);
            buf.writeFloat(payload.dz);
        }

        private static GateEditC2S read(FriendlyByteBuf buf) {
            return new GateEditC2S(buf.readVarInt(), buf.readEnum(GateEditAction.class), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readUnsignedByte() & 0x0F, buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat());
        }

        @Override
        public Type<GateEditC2S> type() {
            return TYPE;
        }
    }

    public record GateSignalC2S(int entityId, int signal) implements CustomPacketPayload {
        public static final Type<GateSignalC2S> TYPE = new Type<>(Constants.id("race_gate_signal"));
        public static final StreamCodec<FriendlyByteBuf, GateSignalC2S> CODEC = StreamCodec.of(GateSignalC2S::write, GateSignalC2S::read);

        private static void write(FriendlyByteBuf buf, GateSignalC2S payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeVarInt(payload.signal);
        }

        private static GateSignalC2S read(FriendlyByteBuf buf) {
            return new GateSignalC2S(buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<GateSignalC2S> type() {
            return TYPE;
        }
    }
}
