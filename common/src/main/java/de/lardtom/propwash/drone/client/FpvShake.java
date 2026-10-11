package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BreakReport;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.CameraShake;
import de.lardtom.propwash.video.ImpactJolt;
import de.lardtom.propwash.video.WearShake;
import de.lardtom.propwash.video.client.VideoSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public final class FpvShake {
    public static final FpvShake INSTANCE = new FpvShake();
    public static final double GROUND_RECIRCULATION_SPAN = 0.3;
    public static final double NEAR_WHEELBASES = 2.5;
    public static final double FAR_WHEELBASES = 5.0;
    public static final int PROBE_EVERY_FRAMES = 4;
    private static final int[][] DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};

    private final CameraShake shake = new CameraShake();
    private final ImpactJolt jolt = new ImpactJolt();
    private final double[] joltDegrees = new double[3];
    private int breaks;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final double[] degrees = new double[3];
    private int droneId = -1;
    private int frames;
    private double confinement;
    private float testIntensity = -1.0F;

    private FpvShake() {
    }

    public CameraShake model() {
        return this.shake;
    }

    public ImpactJolt jolt() {
        return this.jolt;
    }

    public double confinement() {
        return this.confinement;
    }

    public float intensity() {
        return this.testIntensity >= 0.0F ? this.testIntensity : VideoSettings.get().cameraShake;
    }

    void testIntensity(float intensity) {
        this.testIntensity = intensity;
    }

    void update(QuadcopterEntity drone, DroneSim sim, long nanos) {
        if (drone.getId() != this.droneId) {
            this.droneId = drone.getId();
            this.shake.reset();
            this.jolt.reset();
            this.breaks = sim.breakCount();
            this.confinement = 0.0;
            this.frames = 0;
        }
        int breaks = sim.breakCount();
        BreakReport report = sim.lastBreak();
        if (breaks > this.breaks && report != null) {
            this.jolt.hit(nanos, report.lost(), 31L * drone.getId() + breaks);
        }
        this.breaks = breaks;
        Telemetry t = sim.telemetry();
        AirframeSpec spec = sim.params();
        double hoverRpm = spec == null ? 0.0 : spec.hoverOmega * 60.0 / (2.0 * Math.PI);
        double rpm = 0.0;
        for (int i = 0; i < t.motorRpm.length; i++) {
            rpm += Math.abs(t.motorRpm[i]);
        }
        rpm /= t.motorRpm.length;
        boolean spinning = t.armed && hoverRpm > 1.0 && rpm > 1.0;
        double ratio = spinning ? rpm / hoverRpm : 0.0;
        if (this.frames++ % PROBE_EVERY_FRAMES == 0) {
            this.confinement = spinning ? this.probe(drone.level(), drone.getX(), drone.getY(), drone.getZ(), drone.build().frame().wheelbaseMm) : 0.0;
        }
        double ground = Mth.clamp((t.groundEffectFactor - 1.0) / GROUND_RECIRCULATION_SPAN, 0.0, 1.0);
        double induced = spec == null ? 0.0 : spec.hoverInducedVelocity;
        double recirculation = Math.max(ground, this.confinement) * CameraShake.recirculationFade(t.airspeed, induced, ratio);
        double wear = WearShake.atSpeed(WearShake.level(sim.wear(), spec == null ? 0.0 : spec.propBrittleness), ratio);
        this.shake.update(nanos, ratio, wear, Math.max(t.propWash, t.propWashVrs), recirculation, spinning && !t.grounded);
    }

    void apply(long displayedNanos, Quaternionf rotation) {
        float intensity = this.intensity();
        boolean shaking = this.shake.sample(displayedNanos, intensity, this.degrees);
        boolean jolting = this.jolt.sample(displayedNanos, intensity, this.joltDegrees);
        if (!shaking && !jolting) {
            return;
        }
        rotation.rotateX((float) Math.toRadians(this.degrees[0] + this.joltDegrees[0]))
                .rotateY((float) Math.toRadians(this.degrees[1] + this.joltDegrees[1]))
                .rotateZ((float) Math.toRadians(this.degrees[2] + this.joltDegrees[2]));
    }

    double probe(Level level, double x, double y, double z, int wheelbaseMm) {
        double wheelbase = Math.max(0.05, wheelbaseMm / 1000.0);
        double score = 0.0;
        for (int[] d : DIRECTIONS) {
            if (this.solid(level, x + d[0] * NEAR_WHEELBASES * wheelbase, y + d[1] * NEAR_WHEELBASES * wheelbase, z + d[2] * NEAR_WHEELBASES * wheelbase)) {
                score += 1.0;
            } else if (this.solid(level, x + d[0] * FAR_WHEELBASES * wheelbase, y + d[1] * FAR_WHEELBASES * wheelbase, z + d[2] * FAR_WHEELBASES * wheelbase)) {
                score += 0.5;
            }
        }
        return Math.min(1.0, score / 2.0);
    }

    private boolean solid(Level level, double x, double y, double z) {
        this.cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!level.isLoaded(this.cursor)) {
            return false;
        }
        BlockState state = level.getBlockState(this.cursor);
        return !state.isAir() && !state.getCollisionShape(level, this.cursor).isEmpty();
    }
}
