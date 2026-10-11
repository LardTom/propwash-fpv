package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

public final class GhostLibrary {
    private static final long REQUEST_INTERVAL_NANOS = 20L * LapTracker.SECOND;

    private int courseId = LapTracker.NO_COURSE;
    private String frame = "";
    private int layout;
    private @Nullable GhostTrack personal;
    private long[] personalSplits = new long[0];
    private @Nullable GhostTrack assistedPersonal;
    private long[] assistedSplits = new long[0];
    private boolean assistedReference;
    private @Nullable GhostTrack record;
    private long[] recordSplits = new long[0];
    private String recordHolder = "";
    private int recordRevision;
    private long lastRequest = Long.MIN_VALUE;
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
        this.clearRecord();
        this.version++;
    }

    private void clearRecord() {
        this.record = null;
        this.recordSplits = new long[0];
        this.recordHolder = "";
        this.recordRevision = 0;
        this.lastRequest = Long.MIN_VALUE;
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

    public @Nullable GhostTrack record() {
        return this.record;
    }

    public String recordHolder() {
        return this.recordHolder;
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

    public void useAssisted(boolean assisted) {
        if (this.assistedReference != assisted) {
            this.assistedReference = assisted;
            this.version++;
        }
    }

    public long recordLapNanos() {
        return this.record == null ? LapTracker.NONE : this.record.lapNanos();
    }

    public void ensure(Minecraft minecraft, CourseData course, String frame) {
        if (course == this.ensured && Objects.equals(frame, this.frame)) {
            return;
        }
        this.ensured = course;
        int layout = course.layoutHash();
        if (course.id() == this.courseId && Objects.equals(frame, this.frame) && layout == this.layout) {
            return;
        }
        this.courseId = course.id();
        this.frame = frame;
        this.layout = layout;
        this.clearRecord();
        GhostTrack loaded = this.persist ? GhostStore.load(minecraft, course.id(), frame) : null;
        GhostTrack assisted = this.persist ? GhostStore.load(minecraft, course.id(), frame, true) : null;
        this.setAssisted(assisted != null && assisted.layout() == layout ? assisted : null, course);
        this.setPersonal(loaded != null && loaded.layout() == layout ? loaded : null, course);
        this.requestRecord(course, true);
    }

    private void setAssisted(@Nullable GhostTrack track, CourseData course) {
        this.assistedPersonal = track;
        this.assistedSplits = track == null ? new long[0] : track.splits(ClientCourses.geometry(course));
        this.version++;
    }

    private void setPersonal(@Nullable GhostTrack track, CourseData course) {
        this.personal = track;
        this.personalSplits = track == null ? new long[0] : track.splits(ClientCourses.geometry(course));
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

    public void requestRecord(CourseData course, boolean force) {
        if (!RaceConfig.CONFIG.get().recordGhost || !ClientNet.canSend(RacePayloads.GhostRequestC2S.TYPE) || course.id() != this.courseId) {
            return;
        }
        long now = Util.getNanos();
        if (!force && this.lastRequest != Long.MIN_VALUE && now - this.lastRequest < REQUEST_INTERVAL_NANOS) {
            return;
        }
        this.lastRequest = now;
        ClientNet.sendToServer(new RacePayloads.GhostRequestC2S(course.id(), this.frame, this.recordRevision));
    }

    void accept(RacePayloads.GhostS2C payload) {
        if (payload.courseId() != this.courseId || !payload.frame().equals(this.frame)) {
            return;
        }
        if (payload.revision() == 0) {
            if (this.record != null) {
                this.record = null;
                this.recordSplits = new long[0];
                this.recordHolder = "";
                this.recordRevision = 0;
                this.version++;
            }
            return;
        }
        if (payload.data().length == 0) {
            return;
        }
        CourseData course = ClientCourses.get(this.courseId);
        try {
            GhostTrack track = GhostCodec.decode(payload.data());
            if (course == null || track.layout() != this.layout) {
                return;
            }
            this.record = track;
            this.recordSplits = track.splits(ClientCourses.geometry(course));
            this.recordHolder = payload.holder();
            this.recordRevision = payload.revision();
            this.version++;
        } catch (IOException e) {
            Constants.LOG.warn("Rekord-Geist für Kurs {} nicht lesbar: {}", payload.courseId(), e.getMessage());
        }
    }

    public long[] referenceSplits() {
        if (this.assistedReference && GhostTrack.completeSplits(this.assistedSplits)) {
            return this.assistedSplits;
        }
        if (GhostTrack.completeSplits(this.personalSplits)) {
            return this.personalSplits;
        }
        if (RaceConfig.CONFIG.get().recordGhost && GhostTrack.completeSplits(this.recordSplits)) {
            return this.recordSplits;
        }
        return new long[0];
    }
}
