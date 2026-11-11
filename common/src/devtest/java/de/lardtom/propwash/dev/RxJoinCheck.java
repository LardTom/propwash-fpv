package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxIndex;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxServer;
import de.lardtom.propwash.rx.RxServerChecks;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.RxUnloadProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RxJoinCheck {
    public static final int SETUP_AFTER_TICKS = 40;
    public static final int OWN_EAST = 2;
    public static final int FOREIGN_SOUTH = 2;
    public static final int SIGNAL_WEST = 2;
    public static final int FAR_CHUNKS = 30;
    public static final int TIMEOUT_TICKS = 20 * 120;
    private static final Map<UUID, Probe> PROBES = new HashMap<>();

    private RxJoinCheck() {
    }

    private static final class Probe {
        final int joined;
        int stage;
        BlockPos anchor = BlockPos.ZERO;
        @Nullable RxUnloadProbe unload;
        @Nullable LevelSite site;

        Probe(int joined) {
            this.joined = joined;
        }
    }

    private static final class LevelSite implements RxUnloadProbe.Site {
        private final ServerLevel level;
        private final UUID owner;
        private final BlockPos own;
        private @Nullable RxBlockEntity placed;

        LevelSite(ServerLevel level, UUID owner, BlockPos own) {
            this.level = level;
            this.owner = owner;
            this.own = own;
        }

        @Override
        public boolean ticking(BlockPos pos) {
            return RxServer.active(this.level, pos);
        }

        @Override
        public void place(BlockPos pos) {
            this.placed = RxJoinCheck.place(this.level, pos, this.owner);
        }

        @Override
        public void release(BlockPos pos) {
            this.level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        }

        @Override
        public boolean chunkPresent(BlockPos pos) {
            return this.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }

        @Override
        public boolean removed(BlockPos pos) {
            RxBlockEntity entity = this.placed;
            return entity == null || entity.isRemoved();
        }

        @Override
        public int loadedChunks() {
            return this.level.getChunkSource().getLoadedChunksCount();
        }

        @Override
        public boolean held() {
            BlockState state = this.level.getBlockState(this.own);
            return state.getBlock() instanceof RxBlock && state.getValue(RxBlock.POWER) == 15 && state.getValue(RxBlock.LINK) == RxLinkState.LOST;
        }

        @Override
        public boolean indexed(BlockPos pos) {
            RxIndex index = RxIndex.existing(this.level);
            return index != null && index.contains(pos);
        }
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Probe probe = PROBES.computeIfAbsent(player.getUUID(), id -> new Probe(tick));
            try {
                step(server, player, probe, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] RX: Schritt {} fehlgeschlagen", probe.stage, e);
                probe.stage = 9;
            }
        }
    }

    private static void step(MinecraftServer server, ServerPlayer player, Probe probe, int tick) {
        ServerLevel level = player.level();
        switch (probe.stage) {
            case 0 -> {
                if (tick - probe.joined < SETUP_AFTER_TICKS) {
                    return;
                }
                probe.anchor = player.blockPosition();
                BlockPos own = probe.anchor.east(OWN_EAST);
                BlockPos foreign = probe.anchor.south(FOREIGN_SOUTH);
                place(level, own, player.getUUID());
                place(level, foreign, RxServerChecks.STRANGER);
                level.setBlock(probe.anchor.west(SIGNAL_WEST), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                BlockPos far = probe.anchor.offset(FAR_CHUNKS * 16, 0, 0);
                level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                probe.unload = new RxUnloadProbe(far, tick, TIMEOUT_TICKS);
                probe.site = new LevelSite(level, player.getUUID(), own);
                RxServer.refreshDemand(player);
                Constants.LOG.info("[Beitrittstest] RX: Block für {} bei {} (Gas), fremder Block bei {}, zweiter Block in Chunk {},{} wird angelegt",
                        player.getPlainTextName(), own.toShortString(), foreign.toShortString(), far.getX() >> 4, far.getZ() >> 4);
                probe.stage = 1;
            }
            case 1 -> {
                RxUnloadProbe unload = probe.unload;
                LevelSite site = probe.site;
                if (unload != null && site != null && unload.step(site, tick)) {
                    finish(level, probe, unload.problem());
                }
            }
            default -> {
            }
        }
    }

    private static void finish(ServerLevel level, Probe probe, @Nullable String problem) {
        boolean ok = problem == null;
        level.setBlock(probe.anchor.west(SIGNAL_WEST), (ok ? Blocks.EMERALD_BLOCK : Blocks.REDSTONE_BLOCK).defaultBlockState(), Block.UPDATE_ALL);
        Constants.LOG.info("[Beitrittstest] RX: nicht geladener Block {} ({} Chunks geladen, vorher {})", ok ? "unberührt" : "FEHLER: " + problem,
                level.getChunkSource().getLoadedChunksCount(), probe.unload == null ? 0 : probe.unload.loadedBefore());
        probe.stage = 9;
    }

    private static @Nullable RxBlockEntity place(ServerLevel level, BlockPos pos, UUID owner) {
        level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof RxBlockEntity entity) {
            entity.apply(entity.settings().withOwner(owner).withSource(RxSource.GAS).withRange(64));
            return entity;
        }
        return null;
    }
}
