package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.barrier.DroneBarrierBlock;
import de.lardtom.propwash.barrier.DroneBarriers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public final class BarrierMarkers {
    public static final int SAMPLES = 667;
    public static final int NEAR = 16;
    public static final int FAR = 32;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final long[] SPAWNED = new long[BarrierKind.values().length];

    private BarrierMarkers() {
    }

    public static long spawned(BarrierKind kind) {
        return SPAWNED[kind.ordinal()];
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused() || !DroneBarriers.reveals(player)) {
            return;
        }
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        for (int i = 0; i < SAMPLES; i++) {
            mark(level, x, y, z, NEAR);
            mark(level, x, y, z, FAR);
        }
    }

    private static void mark(ClientLevel level, int x, int y, int z, int range) {
        CURSOR.set(x + RANDOM.nextInt(range) - RANDOM.nextInt(range), y + RANDOM.nextInt(range) - RANDOM.nextInt(range),
                z + RANDOM.nextInt(range) - RANDOM.nextInt(range));
        BlockState state = level.getBlockState(CURSOR);
        if (state.getBlock() instanceof DroneBarrierBlock barrier) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), CURSOR.getX() + 0.5, CURSOR.getY() + 0.5,
                    CURSOR.getZ() + 0.5, 0.0, 0.0, 0.0);
            SPAWNED[barrier.kind().ordinal()]++;
        }
    }
}
