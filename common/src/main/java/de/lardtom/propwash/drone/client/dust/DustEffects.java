package de.lardtom.propwash.drone.client.dust;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.dust.DustEmitter;
import de.lardtom.propwash.drone.dust.DustModel;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

public final class DustEffects {
    private static final double TICK_SECONDS = 0.05;
    private static final long PRUNE_GRACE_TICKS = 2L;
    private static final Map<QuadcopterEntity, Tracked> DRONES = new WeakHashMap<>();
    private static final ArrayDeque<WashParticle> LIVE = new ArrayDeque<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Quaternionf ATTITUDE = new Quaternionf();
    private static final Vector3f UP = new Vector3f();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long tick;
    private static long spawned;
    private static long spawnedSpray;
    private static @Nullable ClientLevel trackedLevel;

    private DustEffects() {
    }

    private static final class Tracked {
        final DustEmitter emitter = new DustEmitter();
        DustModel.Wash wash = DustModel.Wash.NONE;
        DustSurface.Ground ground = DustSurface.Ground.NONE;
    }

    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.level != trackedLevel) {
            LIVE.clear();
            DRONES.clear();
            trackedLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        tick++;
        LIVE.removeIf(p -> !p.isAlive() || tick - p.spawnTick() > p.getLifetime() + PRUNE_GRACE_TICKS);
    }

    public static void onDroneTick(QuadcopterEntity drone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(drone.level() instanceof ClientLevel level) || minecraft.level != level) {
            return;
        }
        DustConfig config = DustConfig.get();
        DustModel.Detail detail = detail(minecraft.options.particles().get());
        if (!config.enabled || detail.cap <= 0 || !drone.isArmed() || drone.isInvisible() || drone.isRemoved()) {
            forget(drone);
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        if (drone.position().distanceToSqr(camera) > DustModel.CAMERA_RANGE * DustModel.CAMERA_RANGE) {
            forget(drone);
            return;
        }
        AirframeSpec spec = AirframeSpec.of(drone.build());
        double thrust = thrust(drone, spec);
        double jet = DustModel.jetDiameter(spec.discArea, AirframeSpec.MOTORS);
        double reach = DustModel.reach(DustModel.jetSpeed(thrust, jet), jet);
        drone.renderAttitude(1.0F, ATTITUDE);
        ATTITUDE.transform(UP.set(0.0F, 1.0F, 0.0F));
        double verticality = UP.y;
        double depth = DustModel.probeDepth(reach, verticality);
        if (!(depth > 0.0)) {
            forget(drone);
            return;
        }
        double rotorY = drone.getY() + rotorAboveFeet(spec);
        DustSurface.Ground ground = DustSurface.probe(level, drone.getX(), rotorY, drone.getZ(), depth, CURSOR);
        if (ground.surface() == DustModel.Surface.NONE) {
            forget(drone);
            return;
        }
        double height = rotorY - ground.top();
        DustModel.Wash wash = DustModel.evaluate(height, verticality, thrust, jet, ground.surface(), ground.material(), config.intensity, detail);
        Tracked tracked = DRONES.computeIfAbsent(drone, d -> new Tracked());
        tracked.wash = wash;
        tracked.ground = ground;
        int count = DustModel.allowed(tracked.emitter.take(wash.ratePerSecond(), TICK_SECONDS), LIVE.size(), detail);
        if (count <= 0) {
            return;
        }
        double along = Math.max(0.0, height) / verticality;
        double offsetX = -UP.x * along;
        double offsetZ = -UP.z * along;
        double offset = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        if (offset > wash.reach() && offset > 0.0) {
            offsetX *= wash.reach() / offset;
            offsetZ *= wash.reach() / offset;
        }
        double centerX = drone.getX() + offsetX;
        double centerZ = drone.getZ() + offsetZ;
        double radius = DustModel.spawnRadius(jet, wash.distance());
        for (int i = 0; i < count; i++) {
            spawn(minecraft, level, centerX, centerZ, rotorY, depth, radius, wash, jet, DustModel.visualScale(config.intensity));
        }
    }

    private static void spawn(Minecraft minecraft, ClientLevel level, double centerX, double centerZ, double rotorY, double depth,
                              double radius, DustModel.Wash wash, double jet, float visual) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double r = radius * (0.35 + 0.65 * RANDOM.nextDouble());
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double x = centerX + dirX * r;
        double z = centerZ + dirZ * r;
        DustSurface.Ground ground = DustSurface.probe(level, x, rotorY, z, depth + 0.5, CURSOR);
        if (ground.surface() == DustModel.Surface.NONE || ground.top() > rotorY) {
            return;
        }
        double speed = DustModel.radialSpeed(wash.groundSpeed()) * (0.6 + 0.6 * RANDOM.nextDouble());
        double y = ground.top() + 0.02 + 0.08 * RANDOM.nextDouble();
        int sprite = 4 + RANDOM.nextInt(WashParticle.SPRITE_COUNT - 4);
        WashParticle particle;
        if (ground.surface() == DustModel.Surface.WATER) {
            speed *= 0.8;
            particle = new WashParticle(level, x, y, z, dirX * speed, 0.03 + 0.04 * RANDOM.nextDouble(), dirZ * speed,
                    WashParticle.Style.SPRAY, DustModel.SPRAY_COLOR, Math.min(0.6F, (0.22F + 0.08F * RANDOM.nextFloat()) * visual),
                    (0.03F + 0.03F * RANDOM.nextFloat()) * visual, 8 + RANDOM.nextInt(7), sprite, tick);
        } else {
            float scale = (float) Math.sqrt(DustModel.sizeFactor(jet));
            particle = new WashParticle(level, x, y, z, dirX * speed, 0.006 + 0.018 * RANDOM.nextDouble(), dirZ * speed,
                    WashParticle.Style.DUST, jitter(ground.color()), Math.min(0.75F, (0.4F + 0.14F * RANDOM.nextFloat()) * visual),
                    (0.05F + 0.05F * RANDOM.nextFloat()) * scale * visual, 12 + RANDOM.nextInt(11), sprite, tick);
        }
        minecraft.particleEngine.add(particle);
        LIVE.add(particle);
        spawned++;
        if (particle.style() == WashParticle.Style.SPRAY) {
            spawnedSpray++;
        }
    }

    private static int jitter(int rgb) {
        double f = 0.94 + 0.12 * RANDOM.nextDouble();
        int r = (int) Math.min(255.0, (rgb >> 16 & 0xFF) * f);
        int g = (int) Math.min(255.0, (rgb >> 8 & 0xFF) * f);
        int b = (int) Math.min(255.0, (rgb & 0xFF) * f);
        return r << 16 | g << 8 | b;
    }

    static double thrust(QuadcopterEntity drone, AirframeSpec spec) {
        double k = spec.kThrust * spec.ductGain;
        double omega = spec.omegaMax;
        double total = 0.0;
        double sum = 0.0;
        for (int i = 0; i < QuadcopterEntity.MOTORS; i++) {
            double out = drone.motorOutput(i);
            sum += out;
            total += k * (out * omega) * (out * omega);
        }
        if (sum <= 0.0) {
            double average = drone.motorOutput();
            total = QuadcopterEntity.MOTORS * k * (average * omega) * (average * omega);
        }
        return Double.isFinite(total) ? total : 0.0;
    }

    static double rotorAboveFeet(AirframeSpec spec) {
        return Math.max(0.0, spec.rotorPlaneOffset - spec.hitboxFootOffset);
    }

    public static DustModel.Detail detail(ParticleStatus status) {
        return switch (status) {
            case ALL -> DustModel.Detail.ALL;
            case DECREASED -> DustModel.Detail.DECREASED;
            case MINIMAL -> DustModel.Detail.MINIMAL;
        };
    }

    private static void forget(QuadcopterEntity drone) {
        if (!DRONES.isEmpty()) {
            DRONES.remove(drone);
        }
    }

    public static void reset() {
        LIVE.clear();
        DRONES.clear();
        trackedLevel = null;
    }

    public static int live() {
        return LIVE.size();
    }

    public static long spawnedTotal() {
        return spawned;
    }

    public static long spawnedSpray() {
        return spawnedSpray;
    }

    public static DustModel.Wash lastWash(QuadcopterEntity drone) {
        Tracked tracked = DRONES.get(drone);
        return tracked == null ? DustModel.Wash.NONE : tracked.wash;
    }

    public static DustSurface.Ground lastGround(QuadcopterEntity drone) {
        Tracked tracked = DRONES.get(drone);
        return tracked == null ? DustSurface.Ground.NONE : tracked.ground;
    }

    public static int liveOf(WashParticle.Style style) {
        int count = 0;
        for (WashParticle particle : LIVE) {
            if (particle.style() == style && particle.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public static int untextured() {
        int count = 0;
        for (WashParticle particle : LIVE) {
            if (!particle.textured()) {
                count++;
            }
        }
        return count;
    }
}
