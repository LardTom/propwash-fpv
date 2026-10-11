package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

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

    public record RaceReportC2S(ReportKind kind, int courseId, int lap, long lapNanos, long totalNanos, int flags, long penaltyNanos)
            implements CustomPacketPayload {
        public static final int FLAG_ASSISTED = 1;
        public static final int ASSIST_SHIFT = 1;
        public static final Type<RaceReportC2S> TYPE = new Type<>(Constants.id("race_report"));
        public static final StreamCodec<FriendlyByteBuf, RaceReportC2S> CODEC = StreamCodec.of(RaceReportC2S::write, RaceReportC2S::read);

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

        private static void write(FriendlyByteBuf buf, RaceReportC2S payload) {
            buf.writeByte(payload.kind.ordinal());
            buf.writeVarInt(payload.courseId);
            buf.writeVarInt(payload.lap);
            buf.writeVarLong(payload.lapNanos);
            buf.writeVarLong(payload.totalNanos);
            buf.writeVarInt(payload.flags);
            buf.writeVarLong(payload.penaltyNanos);
        }

        private static RaceReportC2S read(FriendlyByteBuf buf) {
            return new RaceReportC2S(ReportKind.byOrdinal(buf.readByte()), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarLong());
        }

        @Override
        public Type<RaceReportC2S> type() {
            return TYPE;
        }
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

    public record BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank, int assist) {
        public BoardRow(String name, String frame, long nanos, long dateMillis, boolean self, int classRank, int overallRank) {
            this(name, frame, nanos, dateMillis, self, classRank, overallRank, LapAssist.NONE);
        }

        public boolean assisted() {
            return this.assist != LapAssist.NONE;
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
        }

        static BoardRow read(FriendlyByteBuf buf) {
            return new BoardRow(buf.readUtf(Leaderboard.MAX_NAME), buf.readUtf(Leaderboard.MAX_FRAME), buf.readVarLong(), buf.readLong(), buf.readBoolean(),
                    buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() & LapAssist.ALL);
        }
    }

    public record LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows, int raceLaps, long raceBest,
                                 int archived)
            implements CustomPacketPayload {
        public static final int MAX_ROWS = 256;
        public static final Type<LeaderboardS2C> TYPE = new Type<>(Constants.id("race_leaderboard"));
        public static final StreamCodec<FriendlyByteBuf, LeaderboardS2C> CODEC = StreamCodec.of(LeaderboardS2C::write, LeaderboardS2C::read);

        public LeaderboardS2C {
            rows = List.copyOf(rows);
            raceLaps = CourseLaps.clamp(raceLaps);
            raceBest = raceBest > 0L ? raceBest : LapTracker.NONE;
            archived = Math.max(0, archived);
        }

        public LeaderboardS2C(int courseId, int layout, int gates, boolean open, int laps, List<BoardRow> rows) {
            this(courseId, layout, gates, open, laps, rows, CourseLaps.ENDLESS, LapTracker.NONE, 0);
        }

        public boolean hasRaceBest() {
            return this.raceLaps > CourseLaps.ENDLESS && this.raceBest > 0L;
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
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ROWS) {
                throw new DecoderException("Zu viele Zeilen in der Bestenliste: " + count);
            }
            List<BoardRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                rows.add(BoardRow.read(buf));
            }
            return new LeaderboardS2C(course, layout, gates, open, laps, rows, raceLaps, raceBest, archived);
        }

        @Override
        public Type<LeaderboardS2C> type() {
            return TYPE;
        }
    }

    public record GhostRequestC2S(int courseId, String frame, int knownRevision) implements CustomPacketPayload {
        public static final Type<GhostRequestC2S> TYPE = new Type<>(Constants.id("race_ghost_request"));
        public static final StreamCodec<FriendlyByteBuf, GhostRequestC2S> CODEC = StreamCodec.of(GhostRequestC2S::write, GhostRequestC2S::read);

        private static void write(FriendlyByteBuf buf, GhostRequestC2S payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeUtf(payload.frame, Leaderboard.MAX_FRAME);
            buf.writeVarInt(payload.knownRevision);
        }

        private static GhostRequestC2S read(FriendlyByteBuf buf) {
            return new GhostRequestC2S(buf.readVarInt(), buf.readUtf(Leaderboard.MAX_FRAME), buf.readVarInt());
        }

        @Override
        public Type<GhostRequestC2S> type() {
            return TYPE;
        }
    }

    public record GhostS2C(int courseId, String frame, int revision, String holder, long nanos, byte[] data) implements CustomPacketPayload {
        public static final Type<GhostS2C> TYPE = new Type<>(Constants.id("race_ghost"));
        public static final StreamCodec<FriendlyByteBuf, GhostS2C> CODEC = StreamCodec.of(GhostS2C::write, GhostS2C::read);

        private static void write(FriendlyByteBuf buf, GhostS2C payload) {
            buf.writeVarInt(payload.courseId);
            buf.writeUtf(payload.frame, Leaderboard.MAX_FRAME);
            buf.writeVarInt(payload.revision);
            buf.writeUtf(payload.holder, Leaderboard.MAX_NAME);
            buf.writeVarLong(payload.nanos);
            buf.writeByteArray(payload.data);
        }

        private static GhostS2C read(FriendlyByteBuf buf) {
            return new GhostS2C(buf.readVarInt(), buf.readUtf(Leaderboard.MAX_FRAME), buf.readVarInt(), buf.readUtf(Leaderboard.MAX_NAME), buf.readVarLong(),
                    buf.readByteArray(GhostCodec.MAX_ENCODED_BYTES));
        }

        @Override
        public Type<GhostS2C> type() {
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
                              float openingHeight, int variant) implements CustomPacketPayload {
        public static final Type<GateEditC2S> TYPE = new Type<>(Constants.id("race_gate_edit"));
        public static final StreamCodec<FriendlyByteBuf, GateEditC2S> CODEC = StreamCodec.of(GateEditC2S::write, GateEditC2S::read);

        public GateEditC2S(int entityId, GateEditAction action, float heading, float pitch, float roll, float openingWidth, float openingHeight) {
            this(entityId, action, heading, pitch, roll, openingWidth, openingHeight, 0);
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
        }

        private static GateEditC2S read(FriendlyByteBuf buf) {
            return new GateEditC2S(buf.readVarInt(), buf.readEnum(GateEditAction.class), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readUnsignedByte() & 0x03);
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
