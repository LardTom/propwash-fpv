package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.BreakReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class BreakEffects {
    public static final int SHARDS_PER_BLOCK = 48;
    public static final double REACH = 0.25;
    public static final double SLOWEST = 0.55;
    public static final double FASTEST = 1.45;
    public static final double BURST_MIN = 0.08;
    public static final double BURST_MAX = 0.3;
    public static final double SCATTER = 0.05;
    public static final float SIZE_MIN = 0.25F;
    public static final float SIZE_MAX = 0.5F;
    public static final float MIN_VOLUME = 0.45F;
    public static final float MAX_VOLUME = 1.6F;
    public static final float VOLUME_PER_MPS = 0.14F;
    public static final float PITCH = 0.62F;
    private static final double TICK = 0.05;
    private static int shards;
    private static int sounds;
    private static float lastVolume;

    private BreakEffects() {
    }

    public static int shards() {
        return shards;
    }

    public static int sounds() {
        return sounds;
    }

    public static float lastVolume() {
        return lastVolume;
    }

    public static int count(ParticleStatus status, double share) {
        double detail = switch (status) {
            case ALL -> 1.0;
            case DECREASED -> 0.5;
            case MINIMAL -> 0.15;
        };
        double hit = Double.isFinite(share) && share > 0.0 ? Mth.clamp(1.5 * share, 0.3, 1.0) : 1.0;
        return (int) Math.round(SHARDS_PER_BLOCK * detail * hit);
    }

    public static float volume(double speedLost) {
        if (!Double.isFinite(speedLost) || speedLost <= 0.0) {
            return MIN_VOLUME;
        }
        return Mth.clamp(MIN_VOLUME + VOLUME_PER_MPS * (float) speedLost, MIN_VOLUME, MAX_VOLUME);
    }

    static void spray(ClientLevel level, QuadcopterEntity drone, BlockPos pos, BlockState state, AABB glass, Direction.Axis axis, boolean positive,
                      double vx, double vy, double vz, BreakReport report) {
        if (state.isAir() || state.is(Blocks.MOVING_PISTON) || !state.shouldSpawnTerrainParticles()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int count = count(mc.options.particles().get(), report.share());
        if (count <= 0) {
            return;
        }
        AABB box = drone.getBoundingBox().inflate(REACH);
        double x0 = Math.max(glass.minX, box.minX);
        double x1 = Math.min(glass.maxX, box.maxX);
        double y0 = Math.max(glass.minY, box.minY);
        double y1 = Math.min(glass.maxY, box.maxY);
        double z0 = Math.max(glass.minZ, box.minZ);
        double z1 = Math.min(glass.maxZ, box.maxZ);
        double face = positive ? glass.max(axis) : glass.min(axis);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (!(speed > 0.0) || !Double.isFinite(speed)) {
            return;
        }
        RandomSource random = level.getRandom();
        double cx = (box.minX + box.maxX) / 2.0;
        double cy = (box.minY + box.maxY) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double ahead = 2.0 - Mth.clamp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            double x = axis == Direction.Axis.X ? face : pick(random, x0, x1, glass.minX, glass.maxX);
            double y = axis == Direction.Axis.Y ? face : pick(random, y0, y1, glass.minY, glass.maxY);
            double z = axis == Direction.Axis.Z ? face : pick(random, z0, z1, glass.minZ, glass.maxZ);
            double ox = axis == Direction.Axis.X ? 0.0 : x - cx;
            double oy = axis == Direction.Axis.Y ? 0.0 : y - cy;
            double oz = axis == Direction.Axis.Z ? 0.0 : z - cz;
            double off = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (off < 1.0E-3) {
                ox = axis == Direction.Axis.X ? 0.0 : random.nextDouble() - 0.5;
                oy = axis == Direction.Axis.Y ? 0.0 : random.nextDouble() - 0.5;
                oz = axis == Direction.Axis.Z ? 0.0 : random.nextDouble() - 0.5;
                off = Math.max(1.0E-3, Math.sqrt(ox * ox + oy * oy + oz * oz));
            }
            double along = (SLOWEST + (FASTEST - SLOWEST) * random.nextDouble()) * TICK;
            double burst = (BURST_MIN + (BURST_MAX - BURST_MIN) * random.nextDouble()) * speed * TICK / off;
            double scatter = SCATTER * speed * TICK;
            ShardParticle particle = new ShardParticle(level, x, y, z, state, pos, random);
            particle.scale(SIZE_MIN + (SIZE_MAX - SIZE_MIN) * random.nextFloat());
            particle.setLifetime(14 + random.nextInt(22));
            particle.launch(vx * along + ox * burst + scatter * (random.nextDouble() * 2.0 - 1.0),
                    vy * along + oy * burst + scatter * (random.nextDouble() * 2.0 - 1.0),
                    vz * along + oz * burst + scatter * (random.nextDouble() * 2.0 - 1.0), ahead);
            mc.particleEngine.add(particle);
            shards++;
        }
    }

    static void sound(ClientLevel level, QuadcopterEntity drone, BlockState state, BreakReport report) {
        SoundType type = state.getSoundType();
        float volume = volume(report.lost());
        level.playLocalSound(drone, type.getBreakSound(), SoundSource.BLOCKS, volume, type.getPitch() * PITCH);
        sounds++;
        lastVolume = volume;
    }

    private static double pick(RandomSource random, double from, double to, double fallbackFrom, double fallbackTo) {
        double a = from;
        double b = to;
        if (!(b > a)) {
            a = fallbackFrom;
            b = fallbackTo;
        }
        return a + (b - a) * random.nextDouble();
    }
}
