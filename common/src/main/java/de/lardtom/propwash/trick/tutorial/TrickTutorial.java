package de.lardtom.propwash.trick.tutorial;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.trick.TrickType;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

public final class TrickTutorial {
    public static final String DRONE = "drone";
    public static final String SCENE_PREFIX = "trick_";
    public static final float TICKS_PER_SECOND = 20.0F;
    public static final double PRE_ROLL = 1.2;
    public static final float MIN_STEP_TICKS = 20.0F;
    public static final float MAX_STRETCH = 2.5F;
    public static final double TARGET_SPAN = 4.5;
    public static final double MIN_SCALE = 0.06;
    public static final double MAX_SCALE = 0.25;
    public static final float FLOOR_TOP = 1.0F;
    public static final float FLOOR_CLEARANCE = 1.2F;
    public static final float DRONE_CLEARANCE = 1.0F;
    public static final float DRONE_SCALE = 3.5F;
    public static final int MAX_FLOOR = 12;
    public static final float CAMERA_YAW = 65.0F;
    public static final float CAMERA_PITCH = 14.0F;
    public static final float CHASE_YAW = 160.0F;
    public static final float CHASE_PITCH = 22.0F;
    public static final float VIEW_RADIUS = 3.0F;
    public static final BlockSpec FLOOR = BlockSpec.of("minecraft:grass_block");
    public static final BlockSpec OBSTACLE = BlockSpec.of("minecraft:stone_bricks");
    public static final String PATH_PREFIX = "path_";
    public static final String PATH_ITEM = "minecraft:light_gray_concrete";
    public static final float PATH_ITEM_SCALE = 0.3F;
    public static final float PATH_LIFT = 0.15F;
    public static final double PATH_STEP = 0.1;
    public static final int MAX_PATH_MARKS = 60;

    private final TrickChoreography choreography;
    private final TrickFlight flight;
    private final Scene scene;
    private final List<String> stages;
    private final double scale;
    private final int obstacleBlocks;
    private final int pathMarks;

    private TrickTutorial(TrickChoreography choreography, TrickFlight flight, Scene scene, List<String> stages, double scale, int obstacleBlocks,
                          int pathMarks) {
        this.choreography = choreography;
        this.flight = flight;
        this.scene = scene;
        this.stages = List.copyOf(stages);
        this.scale = scale;
        this.obstacleBlocks = obstacleBlocks;
        this.pathMarks = pathMarks;
    }

    public static String sceneId(TrickType type) {
        return SCENE_PREFIX + type.key();
    }

    public static TrickTutorial create(TrickType type) {
        return create(TrickChoreographies.of(type));
    }

    public static TrickTutorial create(TrickChoreography choreography) {
        TrickFlight flight = TrickFlight.fly(choreography, null);
        return new Builder(choreography, flight).build();
    }

    public TrickChoreography choreography() {
        return this.choreography;
    }

    public TrickType trick() {
        return this.choreography.trick();
    }

    public TrickFlight flight() {
        return this.flight;
    }

    public Scene scene() {
        return this.scene;
    }

    public double scale() {
        return this.scale;
    }

    public int obstacleBlocks() {
        return this.obstacleBlocks;
    }

    public int pathMarks() {
        return this.pathMarks;
    }

    public String titleKey() {
        return this.choreography.titleKey();
    }

    public String stepTitleKey(int step) {
        return TrickChoreography.stageTitleKey(this.stages.get(Math.clamp(step, 0, this.stages.size() - 1)));
    }

    public String stepTextKey(int step) {
        return this.choreography.stageTextKey(this.stages.get(Math.clamp(step, 0, this.stages.size() - 1)));
    }

    public static float[] sceneRotation(double qx, double qy, double qz, double qw, float[] previous) {
        Quaterniond model = new Quaterniond(qx, qy, qz, qw).normalize().rotateY(Math.PI);
        return euler(model.x, model.y, model.z, model.w, previous);
    }

    private static float[] euler(double qx, double qy, double qz, double qw, float[] previous) {
        double r00 = 1.0 - 2.0 * (qy * qy + qz * qz);
        double r01 = 2.0 * (qx * qy - qz * qw);
        double r02 = 2.0 * (qx * qz + qy * qw);
        double r10 = 2.0 * (qx * qy + qz * qw);
        double r11 = 1.0 - 2.0 * (qx * qx + qz * qz);
        double r12 = 2.0 * (qy * qz - qx * qw);
        double r20 = 2.0 * (qx * qz - qy * qw);
        double r21 = 2.0 * (qy * qz + qx * qw);
        double r22 = 1.0 - 2.0 * (qx * qx + qy * qy);
        double b = Math.asin(Math.clamp(-r12, -1.0, 1.0));
        double a;
        double c;
        if (Math.abs(Math.cos(b)) < 1.0E-4) {
            a = Math.toRadians(180.0 - previous[0]);
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            c = Math.atan2(-(ca * r01 - sa * r21), ca * r00 - sa * r20);
        } else {
            a = Math.atan2(r02, r22);
            c = Math.atan2(r10, r11);
        }
        float yaw = (float) (180.0 - Math.toDegrees(a));
        float pitch = (float) -Math.toDegrees(b);
        float roll = (float) Math.toDegrees(c);
        float[] first = {near(yaw, previous[0]), near(pitch, previous[1]), near(roll, previous[2])};
        float[] second = {near(yaw - 180.0F, previous[0]), near(-pitch - 180.0F, previous[1]), near(roll + 180.0F, previous[2])};
        return distance(first, previous) <= distance(second, previous) ? first : second;
    }

    private static float near(float angle, float reference) {
        float d = angle - reference;
        d -= 360.0F * Math.round(d / 360.0F);
        return reference + d;
    }

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class Builder {
        private final TrickChoreography choreography;
        private final TrickFlight flight;
        private final List<TrickFlight.Frame> frames;
        private final List<String> stages;
        private final double start;
        private final double scale;
        private final double centerX;
        private final double centerZ;
        private final double bottomY;
        private final Quaterniond q0 = new Quaterniond();
        private final Quaterniond q1 = new Quaterniond();
        private float[] rotation = {0.0F, 0.0F, 0.0F};
        private int obstacleBlocks;
        private int pathMarks;

        Builder(TrickChoreography choreography, TrickFlight flight) {
            this.choreography = choreography;
            this.flight = flight;
            this.frames = flight.frames();
            this.stages = flight.stages();
            this.start = this.stages.size() > 1 ? Math.max(0.0, flight.stageStart(1) - PRE_ROLL) : 0.0;
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (TrickFlight.Frame frame : this.frames) {
                if (frame.time() + 1.0E-9 < this.start) {
                    continue;
                }
                lo[0] = Math.min(lo[0], frame.x());
                lo[1] = Math.min(lo[1], frame.y());
                lo[2] = Math.min(lo[2], frame.z());
                hi[0] = Math.max(hi[0], frame.x());
                hi[1] = Math.max(hi[1], frame.y());
                hi[2] = Math.max(hi[2], frame.z());
            }
            double span = Math.max(1.0E-3, Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])));
            this.scale = Math.clamp(TARGET_SPAN / span, MIN_SCALE, MAX_SCALE);
            this.centerX = (lo[0] + hi[0]) * 0.5;
            this.centerZ = (lo[2] + hi[2]) * 0.5;
            this.bottomY = lo[1];
        }

        TrickTutorial build() {
            SceneBuilder scene = SceneBuilder.scene(sceneId(this.choreography.trick())).icon(SceneKit.DRONE_ITEM).view(VIEW_RADIUS);
            int firstCore = Math.min(1, this.stages.size() - 1);
            ScenePos focus = this.position(this.sample(this.flight.stageStart(firstCore)));
            int side = this.obstacleSide();
            if (side == 0 && !this.flight.obstacles().isEmpty()) {
                scene.camera(focus.add(0.0F, 0.3F, 0.0F), CHASE_YAW, CHASE_PITCH, 1.0F);
            } else {
                scene.camera(focus.add(0.0F, 0.3F, 0.0F), side < 0 ? CAMERA_YAW : -CAMERA_YAW, CAMERA_PITCH, 1.0F);
            }
            for (int i = 0; i < this.stages.size(); i++) {
                int stage = i;
                double from = Math.max(this.start, this.flight.stageStart(i));
                double to = Math.max(from, this.flight.stageEnd(i));
                int ticks = ticks(this.stages.get(i), to - from);
                scene.step(this.stages.get(i), ticks, s -> {
                    if (stage == 0) {
                        this.setup(s, from);
                    }
                    for (int k = 1; k <= ticks; k++) {
                        TrickFlight.Frame frame = this.sample(from + (to - from) * k / ticks);
                        s.at(k - 1.0F);
                        this.pose(s, frame, 1.0F);
                    }
                });
            }
            return new TrickTutorial(this.choreography, this.flight, scene.build(), this.stages, this.scale, this.obstacleBlocks, this.pathMarks);
        }

        private static int ticks(String stage, double seconds) {
            float real = (float) seconds * TICKS_PER_SECOND;
            if (TrickChoreographies.APPROACH.equals(stage) || TrickChoreographies.EXIT.equals(stage)) {
                return Math.max(1, Math.round(real));
            }
            return Math.max(1, Math.round(Math.min(Math.max(real, MIN_STEP_TICKS), real * MAX_STRETCH)));
        }

        private void setup(SceneBuilder.StepBuilder s, double from) {
            s.at(0.0F);
            this.floor(s);
            this.obstacles(s);
            this.path(s);
            TrickFlight.Frame first = this.sample(from);
            this.rotation = sceneRotation(first.qx(), first.qy(), first.qz(), first.qw(), new float[]{0.0F, 0.0F, 0.0F});
            s.spawn(DRONE, new ActorSpec.Drone(DroneBuild.FREESTYLE, true, DRONE_SCALE), this.position(first),
                    this.rotation[0], Cue.Appear.INSTANT, 0.0F);
            s.arm(DRONE, true);
            this.pose(s, first, 0.0F);
        }

        private void path(SceneBuilder.StepBuilder s) {
            double to = this.flight.duration();
            int count = Math.clamp((int) Math.round((to - this.start) / PATH_STEP), 2, MAX_PATH_MARKS);
            ActorSpec.Item mark = new ActorSpec.Item(PATH_ITEM, PATH_ITEM_SCALE, false);
            for (int i = 0; i <= count; i++) {
                ScenePos at = this.position(this.sample(this.start + (to - this.start) * i / count)).add(0.0F, PATH_LIFT, 0.0F);
                s.spawn(PATH_PREFIX + i, mark, at, 0.0F, Cue.Appear.INSTANT, 0.0F);
            }
            this.pathMarks = count + 1;
        }

        private void pose(SceneBuilder.StepBuilder s, TrickFlight.Frame frame, float length) {
            this.rotation = sceneRotation(frame.qx(), frame.qy(), frame.qz(), frame.qw(), this.rotation);
            s.move(DRONE, length, Ease.LINEAR, this.position(frame));
            s.turn(DRONE, this.rotation[0], this.rotation[1], this.rotation[2], length, Ease.LINEAR);
            s.sticks(DRONE, (float) frame.yaw(), (float) (frame.throttle() * 2.0 - 1.0), (float) frame.roll(), (float) frame.pitch(), length);
            s.throttle(DRONE, (float) frame.motor(), length);
        }

        private ScenePos position(TrickFlight.Frame frame) {
            return ScenePos.of((frame.x() - this.centerX) * this.scale, FLOOR_TOP + FLOOR_CLEARANCE + (frame.y() - this.bottomY) * this.scale,
                    (frame.z() - this.centerZ) * this.scale);
        }

        private TrickFlight.Frame sample(double time) {
            int lo = 0;
            int hi = this.frames.size() - 1;
            if (time <= this.frames.get(lo).time()) {
                return this.frames.get(lo);
            }
            if (time >= this.frames.get(hi).time()) {
                return this.frames.get(hi);
            }
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (this.frames.get(mid).time() <= time) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            TrickFlight.Frame a = this.frames.get(lo);
            TrickFlight.Frame b = this.frames.get(hi);
            double t = (time - a.time()) / Math.max(1.0E-9, b.time() - a.time());
            this.q0.set(a.qx(), a.qy(), a.qz(), a.qw());
            this.q1.set(b.qx(), b.qy(), b.qz(), b.qw());
            this.q0.slerp(this.q1, t);
            return new TrickFlight.Frame(time, a.stage(), lerp(a.x(), b.x(), t), lerp(a.y(), b.y(), t), lerp(a.z(), b.z(), t), this.q0.x, this.q0.y,
                    this.q0.z, this.q0.w, lerp(a.roll(), b.roll(), t), lerp(a.pitch(), b.pitch(), t), lerp(a.yaw(), b.yaw(), t),
                    lerp(a.throttle(), b.throttle(), t), lerp(a.motor(), b.motor(), t));
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private void floor(SceneBuilder.StepBuilder s) {
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            for (TrickFlight.Frame frame : this.frames) {
                if (frame.time() + 1.0E-9 < this.start) {
                    continue;
                }
                ScenePos p = this.position(frame);
                minX = Math.min(minX, p.x());
                maxX = Math.max(maxX, p.x());
                minZ = Math.min(minZ, p.z());
                maxZ = Math.max(maxZ, p.z());
            }
            int x0 = (int) Math.floor(minX) - 1;
            int x1 = (int) Math.ceil(maxX) + 1;
            int z0 = (int) Math.floor(minZ) - 1;
            int z1 = (int) Math.ceil(maxZ) + 1;
            if (x1 - x0 + 1 > MAX_FLOOR) {
                int mid = (x0 + x1) / 2;
                x0 = mid - MAX_FLOOR / 2;
                x1 = x0 + MAX_FLOOR - 1;
            }
            if (z1 - z0 + 1 > MAX_FLOOR) {
                int mid = (z0 + z1) / 2;
                z0 = mid - MAX_FLOOR / 2;
                z1 = z0 + MAX_FLOOR - 1;
            }
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    s.block(x, (int) FLOOR_TOP - 1, z, FLOOR);
                }
            }
        }

        private int obstacleSide() {
            double sum = 0.0;
            for (double[] box : this.flight.obstacles()) {
                double mid = (box[0] + box[3]) * 0.5;
                sum += Math.signum(mid - this.pathX((box[2] + box[5]) * 0.5));
            }
            return (int) Math.signum(sum);
        }

        private double pathX(double z) {
            TrickFlight.Frame best = this.frames.getFirst();
            for (TrickFlight.Frame frame : this.frames) {
                if (Math.abs(frame.z() - z) < Math.abs(best.z() - z)) {
                    best = frame;
                }
            }
            return best.x();
        }

        private double pathExtreme(double z0, double z1, double side) {
            double extreme = Double.NaN;
            for (TrickFlight.Frame frame : this.frames) {
                if (frame.time() + 1.0E-9 < this.start || frame.z() < z0 - 1.0 || frame.z() > z1 + 1.0) {
                    continue;
                }
                extreme = Double.isNaN(extreme) ? frame.x() : side > 0.0 ? Math.max(extreme, frame.x()) : Math.min(extreme, frame.x());
            }
            return Double.isNaN(extreme) ? this.pathX((z0 + z1) * 0.5) : extreme;
        }

        private void obstacles(SceneBuilder.StepBuilder s) {
            int index = 0;
            for (double[] box : this.flight.obstacles()) {
                double path = this.pathX((box[2] + box[5]) * 0.5);
                double side = Math.signum((box[0] + box[3]) * 0.5 - path);
                double inner = side > 0.0 ? box[0] : box[3];
                float face = (float) ((inner - this.centerX) * this.scale);
                float closest = (float) ((this.pathExtreme(box[2], box[5], side) - this.centerX) * this.scale);
                if ((face - closest) * side < DRONE_CLEARANCE) {
                    face = closest + (float) side * DRONE_CLEARANCE;
                }
                float z0 = (float) ((box[2] - this.centerZ) * this.scale);
                float z1 = (float) ((box[5] - this.centerZ) * this.scale);
                int length = Math.max(1, Math.round(z1 - z0));
                float zMid = (z0 + z1) * 0.5F;
                float top = Math.max(FLOOR_TOP + 2.0F, FLOOR_TOP + FLOOR_CLEARANCE + (float) ((box[4] - this.bottomY) * this.scale));
                int height = Math.min(4, Math.max(2, Math.round(top - FLOOR_TOP)));
                for (int iz = 0; iz < length; iz++) {
                    for (int iy = 0; iy < height; iy++) {
                        ScenePos at = new ScenePos(face + (float) side * 0.5F, FLOOR_TOP + iy, zMid + (iz - (length - 1) * 0.5F));
                        s.spawn("obstacle_" + index++, new ActorSpec.Block(OBSTACLE), at, 0.0F, Cue.Appear.INSTANT, 0.0F);
                    }
                }
            }
            this.obstacleBlocks = index;
        }
    }
}
