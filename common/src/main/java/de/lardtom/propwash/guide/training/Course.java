package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Course {
    public static final double LEAVE_MARGIN = 24.0;

    private final Lesson lesson;
    private final TrainingFrame frame;
    private final TrainingScale scale;
    private final List<GateGeometry> rings;
    private final double[] padHeights;

    public Course(Lesson lesson, TrainingFrame frame, TrainingScale scale, double[] padHeights) {
        this.lesson = lesson;
        this.frame = frame;
        this.scale = scale;
        this.padHeights = new double[lesson.pads().size()];
        for (int i = 0; i < this.padHeights.length; i++) {
            this.padHeights[i] = padHeights != null && i < padHeights.length && Double.isFinite(padHeights[i]) ? padHeights[i] : frame.y();
        }
        List<GateGeometry> world = new ArrayList<>();
        for (Ring ring : lesson.rings()) {
            world.add(geometry(ring, frame));
        }
        this.rings = List.copyOf(world);
    }

    public static Course flat(Lesson lesson, TrainingFrame frame, TrainingScale scale) {
        double[] heights = new double[lesson.pads().size()];
        Arrays.fill(heights, frame.y());
        return new Course(lesson, frame, scale, heights);
    }

    public static GateGeometry geometry(Ring ring, TrainingFrame frame) {
        double s = frame.scale();
        double width = ring.width() * s;
        double height = ring.height() * s;
        double thickness = Ring.FRAME * s;
        double depth = Ring.DEPTH * s;
        float yaw = frame.worldYaw(ring.heading());
        double cx = frame.worldX(ring.x(), ring.z());
        double cy = frame.worldY(ring.y());
        double cz = frame.worldZ(ring.x(), ring.z());
        if (!ring.flat()) {
            return new GateGeometry(cx, cy - height * 0.5, cz, yaw, width, height, thickness, depth, GateMount.STANDING, false);
        }
        double r = Math.toRadians(yaw);
        double upX = -Math.sin(r);
        double upZ = Math.cos(r);
        return new GateGeometry(cx - upX * height * 0.5, cy, cz - upZ * height * 0.5, yaw, width, height, thickness, depth,
                GateMount.FLOOR_FLAT, false);
    }

    public Lesson lesson() {
        return this.lesson;
    }

    public TrainingFrame frame() {
        return this.frame;
    }

    public TrainingScale scale() {
        return this.scale;
    }

    public List<GateGeometry> rings() {
        return this.rings;
    }

    public List<GateGeometry> rings(int from, int to) {
        return this.rings.subList(Math.max(0, from), Math.min(this.rings.size(), to));
    }

    public double padHeight(int pad) {
        return pad >= 0 && pad < this.padHeights.length ? this.padHeights[pad] : this.frame.y();
    }

    public double[] padHeights() {
        return this.padHeights.clone();
    }

    public double padX(int pad) {
        Pad p = this.lesson.pads().get(pad);
        return this.frame.worldX(p.x(), p.z());
    }

    public double padZ(int pad) {
        Pad p = this.lesson.pads().get(pad);
        return this.frame.worldZ(p.x(), p.z());
    }

    public double padRadius(int pad) {
        return this.frame.length(this.lesson.pads().get(pad).radius());
    }

    public double padDistance(int pad, double x, double z) {
        return Math.hypot(x - this.padX(pad), z - this.padZ(pad));
    }

    public boolean inZone(int zone, double x, double y, double z) {
        Zone box = this.lesson.zones().get(zone);
        return box.contains(this.frame.localX(x, z), this.frame.localY(y), this.frame.localZ(x, z));
    }

    public double zoneCenterX(int zone) {
        Zone box = this.lesson.zones().get(zone);
        return this.frame.worldX(box.centerX(), box.centerZ());
    }

    public double zoneCenterY(int zone) {
        return this.frame.worldY(this.lesson.zones().get(zone).centerY());
    }

    public double zoneCenterZ(int zone) {
        Zone box = this.lesson.zones().get(zone);
        return this.frame.worldZ(box.centerX(), box.centerZ());
    }

    public double markerX(int marker) {
        Marker m = this.lesson.markers().get(marker);
        return this.frame.worldX(m.x(), m.z());
    }

    public double markerZ(int marker) {
        Marker m = this.lesson.markers().get(marker);
        return this.frame.worldZ(m.x(), m.z());
    }

    public double markerHeight(int marker) {
        return this.frame.length(this.lesson.markers().get(marker).height());
    }

    public double pylonX(int pylon) {
        Pylon p = this.lesson.pylons().get(pylon);
        return this.frame.worldX(p.x(), p.z());
    }

    public double pylonZ(int pylon) {
        Pylon p = this.lesson.pylons().get(pylon);
        return this.frame.worldZ(p.x(), p.z());
    }

    public double pylonHeight(int pylon) {
        return this.frame.length(this.lesson.pylons().get(pylon).height());
    }

    public boolean outside(double x, double y, double z) {
        double[] b = this.lesson.bounds();
        double margin = LEAVE_MARGIN;
        double lx = this.frame.localX(x, z) * this.frame.scale();
        double ly = this.frame.localY(y) * this.frame.scale();
        double lz = this.frame.localZ(x, z) * this.frame.scale();
        double s = this.frame.scale();
        return lx < b[0] * s - margin || lx > b[3] * s + margin || ly > b[4] * s + margin || ly < b[1] * s - margin
                || lz < b[2] * s - margin || lz > b[5] * s + margin;
    }

    public static boolean touches(GateGeometry ring, double x, double y, double z, double radius) {
        double depth = ring.signedDistance(x, y, z);
        if (Math.abs(depth) > ring.depth() * 0.5 + radius) {
            return false;
        }
        double side = ring.localSide(x, y, z);
        double up = ring.localUp(x, y, z);
        double half = ring.width() * 0.5;
        if (Math.abs(side) > half + radius || up < -radius || up > ring.height() + radius) {
            return false;
        }
        double inner = half - ring.frame();
        boolean clearInside = Math.abs(side) < inner - radius && up > ring.frame() + radius && up < ring.height() - ring.frame() - radius;
        return !clearInside;
    }
}
