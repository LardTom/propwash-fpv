package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.SnagBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SnagEffects {
    public static final double RANGE = 48.0;
    public static final double REACH = 0.08;
    public static final float MIN_MOTOR = 0.05F;
    public static final int SOUND_TICKS = 6;
    private static final double TICK = 0.05;

    private static int particles;
    private static int sounds;

    private SnagEffects() {
    }

    static void onDroneTick(QuadcopterEntity drone) {
        if (!(drone.level() instanceof ClientLevel level) || !drone.isArmed() || drone.motorOutput() < MIN_MOTOR) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.mainCamera().position().distanceToSqr(drone.position()) > RANGE * RANGE) {
            return;
        }
        AABB box = drone.getBoundingBox().inflate(REACH);
        Contact contact = contact(level, box);
        if (contact == null) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 motion = drone.getKnownMovement();
        int count = 1 + (int) Math.min(3.0, drone.motorOutput() * 3.0F);
        for (int i = 0; i < count; i++) {
            double x = box.minX + random.nextDouble() * box.getXsize();
            double y = box.minY + random.nextDouble() * box.getYsize();
            double z = box.minZ + random.nextDouble() * box.getZsize();
            double out = 0.15 + 0.25 * drone.motorOutput();
            double vx = motion.x * 0.3 + (random.nextDouble() - 0.5) * out;
            double vy = motion.y * 0.3 + (random.nextDouble() - 0.3) * out;
            double vz = motion.z * 0.3 + (random.nextDouble() - 0.5) * out;
            TerrainParticle particle = new TerrainParticle(level, x, y, z, vx, vy, vz, contact.state(), contact.pos());
            particle.scale(0.35F + random.nextFloat() * 0.3F);
            particle.setLifetime(8 + random.nextInt(12));
            mc.particleEngine.add(particle);
            particles++;
        }
        if (drone.tickCount % SOUND_TICKS == 0) {
            SoundType type = contact.state().getSoundType();
            float volume = Math.min(0.6F, (type.getVolume() + 1.0F) / 8.0F + 0.2F * drone.motorOutput());
            level.playLocalSound(drone, type.getHitSound(), SoundSource.BLOCKS, volume, type.getPitch() * (0.9F + 0.4F * drone.motorOutput()));
            sounds++;
        }
    }

    static @Nullable Contact contact(ClientLevel level, AABB box) {
        int x0 = (int) Math.floor(box.minX);
        int y0 = (int) Math.floor(box.minY);
        int z0 = (int) Math.floor(box.minZ);
        int x1 = (int) Math.floor(box.maxX);
        int y1 = (int) Math.floor(box.maxY);
        int z1 = (int) Math.floor(box.maxZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    BlockState state = SnagBlocks.snagAt(level, cursor);
                    if (state == null) {
                        continue;
                    }
                    for (AABB part : state.getShape(level, cursor).toAabbs()) {
                        if (part.move(cursor).intersects(box)) {
                            return new Contact(cursor.immutable(), state);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static int particles() {
        return particles;
    }

    public static int sounds() {
        return sounds;
    }

    record Contact(BlockPos pos, BlockState state) {
    }
}
