package de.lardtom.propwash.race.client;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.ReplayExport;
import de.lardtom.propwash.race.ReplayMarks;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.client.VideoSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.LongSupplier;

public final class ReplaySession {
    public static final ReplaySession INSTANCE = new ReplaySession();
    public static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0, 4.0};
    public static final int NORMAL_SPEED = 2;
    public static final long SKIP_NANOS = 5L * LapTracker.SECOND;
    public static final double CHASE_DISTANCE = 1.6;
    public static final double CHASE_HEIGHT = 0.5;
    private static final long MAX_FRAME_NANOS = 250_000_000L;
    private static final long HEADING_WINDOW_NANOS = 150_000_000L;
    private static final double MIN_HEADING_SPEED = 1.0;

    public enum View {
        FPV,
        CHASE;

        public View next() {
            return this == FPV ? CHASE : FPV;
        }
    }

    private boolean active;
    private int courseId = LapTracker.NO_COURSE;
    private RacePayloads.@Nullable BoardRow row;
    private @Nullable EntryGhost ghost;
    private @Nullable GhostTrack track;
    private GhostLook look = GhostLook.preset("");
    private ReplayMarks marks = ReplayMarks.NONE;
    private long time;
    private boolean playing;
    private int speed = NORMAL_SPEED;
    private View view = View.FPV;
    private long lastFrame = Long.MIN_VALUE;
    private @Nullable LongSupplier exportClock;
    private boolean hidGui;
    private float uptilt = (float) AirframeGeometry.DEFAULT_CAMERA_TILT;
    private double comAboveFoot;
    private final Vector3f lens = new Vector3f();
    private final double[] feet = new double[3];
    private final double[] ahead = new double[3];
    private final Quaternionf body = new Quaternionf();
    private final Vector3f scratch = new Vector3f();
    private long poses;
    private long started;

    private ReplaySession() {
    }

    public boolean active() {
        return this.active;
    }

    public void start(Minecraft minecraft, int courseId, RacePayloads.BoardRow row, EntryGhost ghost, GhostTrack track) {
        this.stop();
        this.active = true;
        this.courseId = courseId;
        this.row = row;
        this.ghost = ghost;
        this.track = track;
        GhostLook stored = track.look();
        this.look = stored != null ? stored : GhostRenderer.lookFor(track);
        AirframeSpec spec = AirframeSpec.of(this.look.build());
        this.lens.set((float) spec.lensRight, (float) spec.lensUp, (float) -spec.lensForward);
        this.comAboveFoot = -spec.hitboxFootOffset;
        this.uptilt = ghost.info().hasUptilt() ? ghost.info().uptilt() : (float) AirframeGeometry.DEFAULT_CAMERA_TILT;
        this.marks = marksOf(courseId, track);
        this.time = track.beginNanos();
        this.playing = true;
        this.speed = NORMAL_SPEED;
        this.view = View.FPV;
        this.lastFrame = Long.MIN_VALUE;
        this.poses = 0L;
        this.started++;
        this.hidGui = !minecraft.gui.hud.isHidden();
        if (this.hidGui) {
            minecraft.gui.hud.toggle();
        }
    }

    public void stop() {
        if (!this.active) {
            return;
        }
        this.active = false;
        this.exportClock = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (this.hidGui && minecraft != null && minecraft.gui != null && minecraft.gui.hud.isHidden()) {
            minecraft.gui.hud.toggle();
        }
        this.hidGui = false;
        this.track = null;
        this.ghost = null;
        this.row = null;
        this.marks = ReplayMarks.NONE;
        this.playing = false;
    }

    private static ReplayMarks marksOf(int courseId, GhostTrack track) {
        CourseData course = ClientCourses.get(courseId);
        if (course != null) {
            return ReplayMarks.of(course.gates().size(), GhostLibrary.splits(track, course), track.lapNanos());
        }
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(courseId);
        return ReplayMarks.of(board == null ? 0 : board.gates(), new long[0], track.lapNanos());
    }

    public void onFrame(Minecraft minecraft) {
        GhostTrack shown = this.track;
        if (!this.active || shown == null) {
            this.lastFrame = Long.MIN_VALUE;
            return;
        }
        long now = Util.getNanos();
        LongSupplier clock = this.exportClock;
        if (clock != null) {
            this.time = ReplayExport.time(shown.beginNanos(), shown.durationNanos(), clock.getAsLong());
            this.lastFrame = now;
            return;
        }
        if (this.lastFrame != Long.MIN_VALUE && this.playing) {
            long step = Math.min(MAX_FRAME_NANOS, Math.max(0L, now - this.lastFrame));
            this.time += Math.round(step * SPEEDS[this.speed]);
            if (this.time >= this.end()) {
                this.time = this.end();
                this.playing = false;
            }
        }
        this.lastFrame = now;
    }

    public void beginExport(LongSupplier clock) {
        this.time = this.begin();
        this.speed = NORMAL_SPEED;
        this.playing = true;
        this.exportClock = clock;
    }

    public void endExport() {
        this.exportClock = null;
        this.playing = false;
        this.lastFrame = Long.MIN_VALUE;
    }

    public boolean exporting() {
        return this.exportClock != null;
    }

    public long begin() {
        GhostTrack shown = this.track;
        return shown == null ? 0L : shown.beginNanos();
    }

    public long end() {
        GhostTrack shown = this.track;
        return shown == null ? 0L : shown.durationNanos();
    }

    public long duration() {
        return this.end() - this.begin();
    }

    public long time() {
        return this.time;
    }

    public long lapTime() {
        GhostTrack shown = this.track;
        return shown == null ? 0L : shown.lapClock(this.time);
    }

    public void seek(long nanos) {
        GhostTrack shown = this.track;
        this.time = shown == null ? 0L : shown.clamp(nanos);
    }

    public void skip(long nanos) {
        this.seek(this.time + nanos);
    }

    public boolean playing() {
        return this.playing;
    }

    public void togglePlay() {
        if (!this.playing && this.time >= this.end()) {
            this.time = this.begin();
        }
        this.playing = !this.playing;
        this.lastFrame = Long.MIN_VALUE;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        this.lastFrame = Long.MIN_VALUE;
    }

    public int speedIndex() {
        return this.speed;
    }

    public double speed() {
        return SPEEDS[this.speed];
    }

    public void setSpeedIndex(int index) {
        this.speed = Math.max(0, Math.min(SPEEDS.length - 1, index));
    }

    public void cycleSpeed() {
        this.speed = (this.speed + 1) % SPEEDS.length;
    }

    public View view() {
        return this.view;
    }

    public void setView(View view) {
        this.view = view == null ? View.FPV : view;
    }

    public int courseId() {
        return this.courseId;
    }

    public RacePayloads.@Nullable BoardRow row() {
        return this.row;
    }

    public @Nullable EntryGhost ghost() {
        return this.ghost;
    }

    public @Nullable GhostTrack track() {
        return this.track;
    }

    public GhostLook look() {
        return this.look;
    }

    public long[] marks() {
        return this.marks.times().clone();
    }

    public int gates() {
        return this.marks.gates();
    }

    public float uptilt() {
        return this.uptilt;
    }

    public long poses() {
        return this.poses;
    }

    public long started() {
        return this.started;
    }

    public boolean ghostPose(double[] position, Quaternionf rotation) {
        GhostTrack shown = this.track;
        return this.active && shown != null && shown.smoothPose(this.time, position, rotation);
    }

    public boolean drawsGhost() {
        return this.active && this.view == View.CHASE;
    }

    public boolean cameraPose(Vector3d outPosition, Quaternionf outRotation) {
        GhostTrack shown = this.track;
        if (!this.active || shown == null || !shown.smoothPose(this.time, this.feet, this.body)) {
            return false;
        }
        double comX = this.feet[0];
        double comY = this.feet[1] + this.comAboveFoot;
        double comZ = this.feet[2];
        if (this.view == View.FPV) {
            this.scratch.set(this.lens);
            this.body.transform(this.scratch);
            outPosition.set(comX + this.scratch.x, comY + this.scratch.y, comZ + this.scratch.z);
            outRotation.set(this.body).rotateX((float) Math.toRadians(this.uptilt));
        } else {
            double yaw = this.heading(shown);
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            outPosition.set(comX - fx * CHASE_DISTANCE, comY + CHASE_HEIGHT, comZ - fz * CHASE_DISTANCE);
            double dx = comX - outPosition.x;
            double dy = comY - outPosition.y;
            double dz = comZ - outPosition.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float pitch = (float) Math.atan2(dy, horizontal);
            float turn = (float) Math.atan2(-dx, -dz);
            outRotation.identity().rotateY(turn).rotateX(pitch);
        }
        this.poses++;
        return true;
    }

    private double heading(GhostTrack shown) {
        long from = Math.max(shown.beginNanos(), this.time - HEADING_WINDOW_NANOS);
        long to = Math.min(shown.durationNanos(), this.time + HEADING_WINDOW_NANOS);
        Quaternionf unused = new Quaternionf();
        if (to > from && shown.pose(from, this.ahead, unused)) {
            double x0 = this.ahead[0];
            double z0 = this.ahead[2];
            if (shown.pose(to, this.ahead, unused)) {
                double dx = this.ahead[0] - x0;
                double dz = this.ahead[2] - z0;
                double seconds = (to - from) / 1.0E9;
                if (Math.sqrt(dx * dx + dz * dz) / seconds >= MIN_HEADING_SPEED) {
                    return Math.atan2(-dx, dz);
                }
            }
        }
        this.scratch.set(0.0F, 0.0F, -1.0F);
        this.body.transform(this.scratch);
        return Math.atan2(-this.scratch.x, this.scratch.z);
    }

    public float fov(float fallback) {
        if (!this.active || this.view != View.FPV) {
            return fallback;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        double aspect = width > 0 && height > 0 ? (double) width / height : 16.0 / 9.0;
        double horizontal = VideoSettings.get().horizontalFov(null, this.look.build().video());
        float vertical = FovMath.verticalFromHorizontal(horizontal, aspect);
        return vertical > 1.0F && vertical < 170.0F ? vertical : fallback;
    }
}
