package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GateHintJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int AWAY = 40;
    public static final int LAG_TICKS = 240;
    public static final long LAG_MILLIS = 250L;
    public static final double NEAR = 12.0;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int ready = -1;
        long readyAt;
        int gates;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private GateHintJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        boolean lag = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.ready < 0) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    prepare(player, track, tick);
                }
                continue;
            }
            int gates = player.level().getEntitiesOfClass(GateEntity.class, player.getBoundingBox().inflate(NEAR),
                    gate -> gate.kind() == CourseElement.GATE).size();
            if (gates > track.gates) {
                Constants.LOG.info("[Beitrittstest] Gate-Hinweis: Gate {} von {} gesetzt, {} ms nach dem Ausrüsten (Server bremst {} ms je Tick)", gates,
                        player.getPlainTextName(), System.currentTimeMillis() - track.readyAt, LAG_MILLIS);
                track.gates = gates;
            }
            lag |= track.gates < 2 && tick - track.ready < LAG_TICKS;
        }
        if (lag) {
            try {
                Thread.sleep(LAG_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void prepare(ServerPlayer player, Track track, int tick) {
        track.ready = tick;
        track.readyAt = System.currentTimeMillis();
        ServerLevel level = player.level();
        int x = player.getBlockX() + AWAY;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(RaceModule.GATE_MEDIUM.get(), 8));
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 180.0F, 30.0F, true);
        player.resetFallDistance();
        Constants.LOG.info("[Beitrittstest] Gate-Hinweis: {} steht {} Blöcke vom Spawn mit Gate in der Hand, Server bremst {} Ticks lang um {} ms je Tick",
                player.getPlainTextName(), AWAY, LAG_TICKS, LAG_MILLIS);
    }
}
