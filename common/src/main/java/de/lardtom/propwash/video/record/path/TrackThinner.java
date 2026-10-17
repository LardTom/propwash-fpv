package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

import java.util.Arrays;

public final class TrackThinner {
    public static final int MAX_VERIFY_ROUNDS = 64;

    private final PathFrames frames;
    private final Simplification tolerance;
    private final IntArray first = new IntArray();
    private final IntArray kept = new IntArray();
    private int processed = -1;
    private int anchor;
    private int reach;
    private int nextSegment;
    private boolean finished;

    public TrackThinner(PathFrames frames, Simplification tolerance) {
        this.frames = frames;
        this.tolerance = tolerance;
    }

    public Simplification tolerance() {
        return this.tolerance;
    }

    public void advance() {
        if (this.finished) {
            return;
        }
        int n = this.frames.size();
        while (this.processed + 1 < n) {
            int j = ++this.processed;
            if (j == 0) {
                this.first.add(0);
                this.kept.add(0);
                this.anchor = 0;
                this.reach = 0;
                continue;
            }
            this.step(j);
        }
        while (this.nextSegment + 2 < this.first.size()) {
            this.refine(this.first.get(this.nextSegment), this.first.get(this.nextSegment + 1), this.first.get(this.nextSegment + 2));
            this.nextSegment++;
        }
    }

    private void step(int j) {
        if (j == this.anchor + 1) {
            this.reach = j;
            return;
        }
        if (j - this.anchor <= this.tolerance.span() && this.fits(this.anchor, j)) {
            this.reach = j;
            return;
        }
        this.first.add(this.reach);
        this.anchor = this.reach;
        this.reach = j;
    }

    public int keyframes() {
        if (this.processed < 0) {
            return 0;
        }
        if (this.finished) {
            return this.kept.size();
        }
        int pending = this.first.size() - 1 - this.nextSegment;
        return this.kept.size() + pending + (this.reach > this.anchor ? 1 : 0);
    }

    public int[] finish(boolean verify) {
        this.advance();
        if (!this.finished && this.processed >= 0) {
            if (this.reach > this.anchor) {
                this.first.add(this.reach);
                this.anchor = this.reach;
            }
            while (this.nextSegment + 1 < this.first.size()) {
                int a = this.first.get(this.nextSegment);
                int b = this.first.get(this.nextSegment + 1);
                int q = this.nextSegment + 2 < this.first.size() ? this.first.get(this.nextSegment + 2) : b;
                this.refine(a, b, q);
                this.nextSegment++;
            }
            this.finished = true;
            if (verify && !this.tolerance.off()) {
                this.verify();
            }
        }
        return this.kept.toArray();
    }

    private void verify() {
        for (int round = 0; round < MAX_VERIFY_ROUNDS; round++) {
            IntArray next = new IntArray();
            boolean inserted = false;
            int size = this.kept.size();
            for (int i = 0; i + 1 < size; i++) {
                int a = this.kept.get(i);
                int b = this.kept.get(i + 1);
                int p = i > 0 ? this.kept.get(i - 1) : a;
                int q = i + 2 < size ? this.kept.get(i + 2) : b;
                next.add(a);
                long worst = this.worst(p, a, b, q);
                if (worst >= 0L) {
                    next.add((int) worst);
                    inserted = true;
                }
            }
            if (size > 0) {
                next.add(this.kept.get(size - 1));
            }
            this.kept.replace(next);
            if (!inserted) {
                return;
            }
        }
    }

    private void refine(int a, int b, int q) {
        int size = this.kept.size();
        int p = size >= 2 ? this.kept.get(size - 2) : a;
        long worst = this.worst(p, a, b, q);
        if (worst < 0L) {
            this.kept.add(b);
            return;
        }
        int m = (int) worst;
        this.refine(a, m, b);
        this.refine(m, b, q);
    }

    private long worst(int p, int a, int b, int q) {
        if (b - a < 2 || this.tolerance.off()) {
            return -1L;
        }
        PathKeyframe kp = this.frames.keyframe(p);
        PathKeyframe ka = this.frames.keyframe(a);
        PathKeyframe kb = this.frames.keyframe(b);
        PathKeyframe kq = this.frames.keyframe(q);
        double worstError = Simplification.REFINE_FACTOR;
        int worstIndex = -1;
        double slack = Math.max(this.frames.keyframeAngleError(a), this.frames.keyframeAngleError(b));
        for (int k = a + 1; k < b; k++) {
            FlashbackSpline.Pose pose = FlashbackSpline.segment(kp, ka, kb, kq, this.frames.tick(k));
            double error = error(this.frames, k, pose, this.tolerance, slack);
            if (error > worstError) {
                worstError = error;
                worstIndex = k;
            }
        }
        return worstIndex;
    }

    static double error(PathFrames frames, int index, FlashbackSpline.Pose pose, Simplification tolerance, double angleSlack) {
        TickPose truth = frames.pose(index);
        double dx = pose.x() - truth.x();
        double dy = pose.y() - truth.y();
        double dz = pose.z() - truth.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double angle = CameraAngles.degreesBetween(pose.angles().rotation(), frames.rotation(index));
        return Math.max(distance / tolerance.position(), Math.max(0.0, angle - angleSlack) / tolerance.angle());
    }

    private boolean fits(int from, int to) {
        TickPose a = this.frames.pose(from);
        TickPose b = this.frames.pose(to);
        CameraAngles angleFrom = this.frames.angles(from);
        CameraAngles angleTo = this.frames.angles(to);
        double span = to - from;
        double positionLimit = this.tolerance.position() * this.tolerance.position();
        for (int k = from + 1; k < to; k++) {
            double t = (k - from) / span;
            TickPose truth = this.frames.pose(k);
            double dx = a.x() + (b.x() - a.x()) * t - truth.x();
            double dy = a.y() + (b.y() - a.y()) * t - truth.y();
            double dz = a.z() + (b.z() - a.z()) * t - truth.z();
            if (dx * dx + dy * dy + dz * dz > positionLimit) {
                return false;
            }
            Quaterniond guess = angleFrom.lerpShortest(angleTo, t).rotation();
            if (CameraAngles.degreesBetween(guess, this.frames.rotation(k)) > this.tolerance.angle()) {
                return false;
            }
        }
        return true;
    }

    static final class IntArray {
        private int[] values = new int[64];
        private int size;

        void add(int value) {
            if (this.size == this.values.length) {
                this.values = Arrays.copyOf(this.values, this.size * 2);
            }
            this.values[this.size++] = value;
        }

        int get(int index) {
            return this.values[index];
        }

        int size() {
            return this.size;
        }

        int[] toArray() {
            return Arrays.copyOf(this.values, this.size);
        }

        void replace(IntArray other) {
            this.values = other.values;
            this.size = other.size;
        }
    }
}
