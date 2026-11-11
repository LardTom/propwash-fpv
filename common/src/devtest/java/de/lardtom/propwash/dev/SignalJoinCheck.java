package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SignalJoinCheck {
    public static final int WALL_DX = 40;
    public static final int WALL_THICKNESS = 5;
    public static final int WALL_HALF_WIDTH = 15;
    public static final int WALL_HEIGHT = 20;
    public static final int PATH_MARGIN = 20;
    public static final int AWAY = 250;
    public static final int BUILD_AFTER_TICKS = 60;
    public static final int AWAY_AFTER_TICKS = 20 * 25;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();
    private static final Map<UUID, BlockPos> ORIGINS = new HashMap<>();

    private SignalJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int since = tick - JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (since == BUILD_AFTER_TICKS) {
                BlockPos origin = player.blockPosition();
                ORIGINS.put(player.getUUID(), origin);
                int placed = wall(player.level(), origin, Blocks.STONE.defaultBlockState());
                Constants.LOG.info("[Beitrittstest] Signal: Steinwand {} × {} × {} ({} Blöcke) {} Blöcke östlich von {} bei {}",
                        WALL_THICKNESS, 2 * WALL_HALF_WIDTH, WALL_HEIGHT, placed, WALL_DX, player.getPlainTextName(), origin.toShortString());
            } else if (since == AWAY_AFTER_TICKS) {
                BlockPos origin = ORIGINS.getOrDefault(player.getUUID(), player.blockPosition());
                int away = away(server);
                player.teleportTo(player.level(), origin.getX() - away + 0.5, origin.getY(), origin.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
                Constants.LOG.info("[Beitrittstest] Signal: {} {} Blöcke nach Westen geschickt (Sichtweite {} Chunks), die Chunks der Wand verlassen seine Sichtweite",
                        player.getPlainTextName(), away, server.getPlayerList().getViewDistance());
            }
        }
    }

    public static int away(MinecraftServer server) {
        int storage = Math.max(2, server.getPlayerList().getViewDistance()) + 3;
        return Math.max(AWAY, 16 * (storage + 2) + WALL_DX + WALL_THICKNESS + PATH_MARGIN);
    }

    public static int wall(ServerLevel level, BlockPos origin, BlockState state) {
        int placed = 0;
        for (int dx = 0; dx < WALL_THICKNESS; dx++) {
            for (int dz = -WALL_HALF_WIDTH; dz < WALL_HALF_WIDTH; dz++) {
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    if (level.setBlock(origin.offset(WALL_DX + dx, dy, dz), state, Block.UPDATE_CLIENTS)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }
}
