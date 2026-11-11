package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GhostAccess;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class GhostLibrary {
    public static final long BOARD_REFRESH_NANOS = 20L * LapTracker.SECOND;
    public static final long RETRY_NANOS = 10L * LapTracker.SECOND;
    public static final long RETRY_REFUSED_NANOS = 60L * LapTracker.SECOND;

    public enum Shown {
        NONE,
        OWN,
        ASSISTED,
        BOARD
    }

    private int courseId = LapTracker.NO_COURSE;
    private String frame = "";
    private int layout;
    private @Nullable GhostTrack personal;
    private long[] personalSplits = new long[0];
    private @Nullable GhostTrack assistedPersonal;
    private long[] assistedSplits = new long[0];
    private boolean assistedReference;
    private GhostChoice.Mode mode = GhostChoice.Mode.OWN;
    private @Nullable GhostKey boardWanted;
    private @Nullable GhostTrack board;
    private EntryGhost.@Nullable Info boardInfo;
    private long[] boardSplits = new long[0];
    private int boardRequest;
    private GhostDownloads.@Nullable Status boardStatus;
    private long boardAttempt = Long.MIN_VALUE;
    private long leaderboardRequest = Long.MIN_VALUE;
    private int version;
    private boolean persist = true;
    private @Nullable CourseData ensured;

    public void clear() {
        this.ensured = null;
        this.courseId = LapTracker.NO_COURSE;
        this.frame = "";
        this.layout = 0;
        this.personal = null;
        this.personalSplits = new long[0];
        this.assistedPersonal = null;
        this.assistedSplits = new long[0];
        this.assistedReference = false;
        this.mode = GhostChoice.Mode.OWN;
        this.clearBoard();
        this.leaderboardRequest = Long.MIN_VALUE;
        this.version++;
    }

    private void clearBoard() {
        if (this.boardRequest != 0) {
            GhostDownloads.INSTANCE.cancel(this.boardRequest);
        }
        this.boardRequest = 0;
        this.boardWanted = null;
        this.board = null;
        this.boardInfo = null;
        this.boardSplits = new long[0];
        this.boardStatus = null;
        this.boardAttempt = Long.MIN_VALUE;
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }

    public int version() {
        return this.version;
    }

    public int courseId() {
        return this.courseId;
    }

    public String frame() {
        return this.frame;
    }

    public @Nullable GhostTrack personal() {
        return this.personal;
    }

    public GhostChoice.Mode mode() {
        return this.mode;
    }

    public @Nullable GhostTrack board() {
        return this.board;
    }

    public EntryGhost.@Nullable Info boardInfo() {
        return this.boardInfo;
    }

    public @Nullable GhostKey boardWanted() {
        return this.boardWanted;
    }

    public GhostDownloads.@Nullable Status boardStatus() {
        return this.boardStatus;
    }

    public @Nullable GhostTrack record() {
        return this.board;
    }

    public String recordHolder() {
        return this.boardInfo == null ? "" : this.boardInfo.name();
    }

    public long personalLapNanos() {
        return this.personal == null ? LapTracker.NONE : this.personal.lapNanos();
    }

    public @Nullable GhostTrack assistedPersonal() {
        return this.assistedPersonal;
    }

    public long assistedLapNanos() {
        return this.assistedPersonal == null ? LapTracker.NONE : this.assistedPersonal.lapNanos();
    }

    public boolean assistedReference() {
        return this.assistedReference;
    }

    public boolean showsAssisted() {
        return this.assistedReference && this.assistedPersonal != null;
    }

    public @Nullable GhostTrack shownPersonal() {
        return this.showsAssisted() ? this.assistedPersonal : this.personal;
    }

    public Shown shownKind() {
        if (this.mode == GhostChoice.Mode.OFF) {
            return Shown.NONE;
        }
        if (this.mode == GhostChoice.Mode.BOARD && this.board != null) {
            return Shown.BOARD;
        }
        if (this.showsAssisted()) {
            return Shown.ASSISTED;
        }
        return this.personal != null ? Shown.OWN : Shown.NONE;
    }

    public @Nullable GhostTrack shown() {
        return switch (this.shownKind()) {
            case BOARD -> this.board;
            case ASSISTED -> this.assistedPersonal;
            case OWN -> this.personal;
            case NONE -> null;
        };
    }

    public void useAssisted(boolean assisted) {
        if (this.assistedReference != assisted) {
            this.assistedReference = assisted;
            this.version++;
        }
    }

    public long recordLapNanos() {
        return this.board == null ? LapTracker.NONE : this.board.lapNanos();
    }

    public void ensure(Minecraft minecraft, CourseData course, String frame) {
        if (course != this.ensured || !Objects.equals(frame, this.frame)) {
            this.ensured = course;
            int layout = course.layoutHash();
            if (course.id() != this.courseId || !Objects.equals(frame, this.frame) || layout != this.layout) {
                boolean sameCourse = course.id() == this.courseId && layout == this.layout;
                this.courseId = course.id();
                this.frame = frame;
                this.layout = layout;
                if (!sameCourse || this.board != null && this.board.layout() != layout) {
                    this.clearBoard();
                }
                GhostTrack loaded = this.persist ? GhostStore.load(minecraft, course.id(), frame) : null;
                GhostTrack assisted = this.persist ? GhostStore.load(minecraft, course.id(), frame, true) : null;
                this.setAssisted(assisted != null && assisted.layout() == layout ? assisted : null, course);
                this.setPersonal(loaded != null && loaded.layout() == layout ? loaded : null, course);
            }
        }
        this.syncBoard(minecraft, course, Util.getNanos());
    }

    static long[] splits(GhostTrack track, CourseData course) {
        List<GateGeometry> gates = ClientCourses.geometry(course);
        long[] splits = track.splits(gates, ClientGateOpenings.of(course.id(), gates));
        if (course.pointToPoint() && splits.length > 0) {
            splits[splits.length - 1] = track.lapNanos();
        }
        return splits;
    }

    private void setAssisted(@Nullable GhostTrack track, CourseData course) {
        this.assistedPersonal = track;
        this.assistedSplits = track == null ? new long[0] : splits(track, course);
        this.version++;
    }

    private void setPersonal(@Nullable GhostTrack track, CourseData course) {
        this.personal = track;
        this.personalSplits = track == null ? new long[0] : splits(track, course);
        this.version++;
    }

    public boolean offerLap(Minecraft minecraft, CourseData course, GhostTrack track, boolean assisted) {
        return this.offerLap(minecraft, course, track, assisted, false);
    }

    public boolean offerLap(Minecraft minecraft, CourseData course, GhostTrack track, boolean assisted, boolean saveAssisted) {
        if (course.id() != this.courseId || track.layout() != this.layout) {
            return false;
        }
        if (assisted) {
            if (!saveAssisted || this.assistedPersonal != null && this.assistedPersonal.lapNanos() <= track.lapNanos()) {
                return false;
            }
            this.setAssisted(track, course);
            if (this.persist) {
                GhostStore.save(minecraft, course.id(), this.frame, track, true);
            }
            return true;
        }
        if (this.personal != null && this.personal.lapNanos() <= track.lapNanos()) {
            return false;
        }
        this.setPersonal(track, course);
        if (this.persist) {
            GhostStore.save(minecraft, course.id(), this.frame, track);
        }
        return true;
    }

    public static @Nullable GhostKey recordKey(RacePayloads.@Nullable LeaderboardS2C board, String frame) {
        if (board == null) {
            return null;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (!row.assisted() && row.classRank() == 1 && row.frame().equals(frame)) {
                return row.ghost() && !row.player().equals(RacePayloads.BoardRow.NO_PLAYER) ? row.key() : null;
            }
        }
        return null;
    }

    private void syncBoard(Minecraft minecraft, CourseData course, long now) {
        GhostChoice choice = GhostChoices.INSTANCE.get(minecraft, course.id());
        if (choice.mode() != this.mode) {
            this.mode = choice.mode();
            this.version++;
        }
        GhostKey wanted = null;
        if (choice.mode() == GhostChoice.Mode.BOARD) {
            RacePayloads.LeaderboardS2C known = ClientLeaderboards.get(course.id());
            wanted = choice.chosen() ? choice.key() : recordKey(known, this.frame);
            if (choice.record() && known == null) {
                this.requestLeaderboard(course.id(), now);
            }
        }
        if (!Objects.equals(wanted, this.boardWanted)) {
            boolean had = this.board != null;
            this.clearBoard();
            this.boardWanted = wanted;
            if (had) {
                this.version++;
            }
        }
        if (this.boardRequest != 0 && !GhostDownloads.INSTANCE.loading(this.boardRequest)) {
            this.boardRequest = 0;
        }
        if (wanted == null || this.board != null || this.boardRequest != 0) {
            return;
        }
        long wait = this.boardStatus == GhostDownloads.Status.DENIED || this.boardStatus == GhostDownloads.Status.MISSING ? RETRY_REFUSED_NANOS
                : RETRY_NANOS;
        if (this.boardAttempt != Long.MIN_VALUE && now - this.boardAttempt < wait) {
            return;
        }
        GhostDownloads downloads = GhostDownloads.INSTANCE;
        if (!downloads.available() && downloads.cached(course.id(), wanted) == null) {
            return;
        }
        this.boardAttempt = now;
        GhostKey key = wanted;
        int id = course.id();
        int[] request = new int[1];
        request[0] = downloads.fetch(id, key, GhostAccess.Purpose.OPPONENT, result -> this.acceptBoard(id, key, request[0], result));
        if (this.board == null && this.boardStatus == null) {
            this.boardRequest = request[0];
        }
    }

    private void acceptBoard(int course, GhostKey key, int request, GhostDownloads.Result result) {
        if (course != this.courseId || !key.equals(this.boardWanted)) {
            return;
        }
        if (request != 0 && request != this.boardRequest && this.boardRequest != 0) {
            return;
        }
        this.boardRequest = 0;
        CourseData data = ClientCourses.get(course);
        GhostTrack track = result.track();
        EntryGhost ghost = result.ghost();
        if (!result.loaded() || track == null || ghost == null || data == null) {
            this.boardStatus = result.status();
            return;
        }
        if (track.layout() != this.layout) {
            this.boardStatus = GhostDownloads.Status.MISSING;
            return;
        }
        this.board = track;
        this.boardInfo = ghost.info();
        this.boardSplits = splits(track, data);
        this.boardStatus = GhostDownloads.Status.LOADED;
        this.version++;
    }

    private void requestLeaderboard(int course, long now) {
        if (this.leaderboardRequest != Long.MIN_VALUE && now - this.leaderboardRequest < BOARD_REFRESH_NANOS) {
            return;
        }
        this.leaderboardRequest = now;
        ClientLeaderboards.request(course);
    }

    public void refreshBoard(CourseData course) {
        if (course.id() != this.courseId || this.mode != GhostChoice.Mode.BOARD) {
            return;
        }
        GhostChoice choice = GhostChoices.INSTANCE.get(Minecraft.getInstance(), course.id());
        if (choice.record()) {
            this.requestLeaderboard(course.id(), Util.getNanos());
        }
    }

    public long[] referenceSplits() {
        Shown shown = this.shownKind();
        if (shown == Shown.BOARD && GhostTrack.completeSplits(this.boardSplits)) {
            return this.boardSplits;
        }
        if (this.assistedReference && GhostTrack.completeSplits(this.assistedSplits)) {
            return this.assistedSplits;
        }
        if (GhostTrack.completeSplits(this.personalSplits)) {
            return this.personalSplits;
        }
        return new long[0];
    }
}
