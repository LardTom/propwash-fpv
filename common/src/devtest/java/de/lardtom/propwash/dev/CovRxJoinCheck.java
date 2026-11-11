package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxServer;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
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

public final class CovRxJoinCheck {
    public static final int SETUP_AFTER_TICKS = 40;
    public static final int OWN_EAST = 2;
    public static final int SIGNAL_WEST = 2;
    public static final int HELD_POWER = 10;
    public static final int MIN_SAMPLES = 5;
    private static final int SAMPLE_TICKS = 20;
    private static final Map<UUID, Probe> PROBES = new HashMap<>();

    private CovRxJoinCheck() {
    }

    private static final class Probe {
        int stage;
        int mark;
        BlockPos anchor = BlockPos.ZERO;
        int samples;
        int heldSamples;
        @Nullable String deviation;
        boolean online;
        String name = "";
    }

    static void tick(MinecraftServer server, int tick) {
        Map<UUID, ServerPlayer> online = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.put(player.getUUID(), player);
            PROBES.computeIfAbsent(player.getUUID(), id -> {
                Probe fresh = new Probe();
                fresh.mark = tick;
                return fresh;
            });
        }
        for (Map.Entry<UUID, Probe> entry : PROBES.entrySet()) {
            Probe probe = entry.getValue();
            ServerPlayer player = online.get(entry.getKey());
            try {
                step(server, player, probe, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] RX-Abdeckung: Schritt {} fehlgeschlagen", probe.stage, e);
                probe.stage = 9;
            }
        }
    }

    private static void step(MinecraftServer server, @Nullable ServerPlayer player, Probe probe, int tick) {
        ServerLevel level = server.overworld();
        switch (probe.stage) {
            case 0 -> {
                if (player == null || tick - probe.mark < SETUP_AFTER_TICKS) {
                    return;
                }
                probe.name = player.getPlainTextName();
                probe.anchor = player.blockPosition();
                BlockPos own = probe.anchor.east(OWN_EAST);
                level.setChunkForced(own.getX() >> 4, own.getZ() >> 4, true);
                level.setBlock(probe.anchor.west(SIGNAL_WEST), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(own, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(own) instanceof RxBlockEntity entity) {
                    entity.apply(new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, 64));
                }
                RxServer.refreshDemand(player);
                Constants.LOG.info("[Beitrittstest] RX-Abdeckung: Block für {} bei {} (Gas, Reichweite 64), Chunk festgehalten", probe.name, own.toShortString());
                probe.stage = 1;
                probe.online = true;
            }
            case 1 -> {
                BlockPos own = probe.anchor.east(OWN_EAST);
                if (player == null) {
                    BlockState state = level.getBlockState(own);
                    int power = state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
                    Constants.LOG.info("[Beitrittstest] RX-Abdeckung: {} getrennt, Stärke beim Trennen {} ({})", probe.name, power,
                            state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.LINK) : "-");
                    if (power != HELD_POWER) {
                        probe.deviation = "beim Trennen Stärke " + power + " statt " + HELD_POWER;
                    }
                    probe.stage = 2;
                    probe.mark = tick;
                }
            }
            case 2 -> {
                BlockPos own = probe.anchor.east(OWN_EAST);
                if (player == null) {
                    if ((tick - probe.mark) % SAMPLE_TICKS == 0) {
                        probe.samples++;
                        BlockState state = level.getBlockState(own);
                        int power = state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
                        if (power == HELD_POWER) {
                            probe.heldSamples++;
                        } else if (probe.deviation == null) {
                            probe.deviation = "während der Abwesenheit Stärke " + power + " nach " + (tick - probe.mark) + " Ticks";
                        }
                        if (probe.samples % 10 == 1) {
                            Constants.LOG.info("[Beitrittstest] RX-Abdeckung: {} abwesend, Probe {}: Stärke {} ({})", probe.name, probe.samples, power,
                                    state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.LINK) : "-");
                        }
                    }
                    return;
                }
                boolean held = probe.deviation == null && probe.samples >= MIN_SAMPLES && probe.heldSamples == probe.samples;
                level.setBlock(probe.anchor.west(SIGNAL_WEST), (held ? Blocks.EMERALD_BLOCK : Blocks.REDSTONE_BLOCK).defaultBlockState(), Block.UPDATE_ALL);
                BlockState state = level.getBlockState(own);
                Constants.LOG.info("[Beitrittstest] RX-Abdeckung: {} wieder da; während der Abwesenheit {} von {} Proben Stärke {}{}; jetzt {} ({})",
                        probe.name, probe.heldSamples, probe.samples, HELD_POWER, probe.deviation == null ? "" : " – FEHLER: " + probe.deviation,
                        state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1,
                        state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.LINK) : RxLinkState.NONE);
                RxServer.refreshDemand(player);
                probe.stage = 3;
                probe.mark = tick;
            }
            case 3 -> {
                if (player == null) {
                    level.setChunkForced(probe.anchor.east(OWN_EAST).getX() >> 4, probe.anchor.east(OWN_EAST).getZ() >> 4, false);
                    Constants.LOG.info("[Beitrittstest] RX-Abdeckung: {} nach dem Wiederbeitritt getrennt, Chunk freigegeben", probe.name);
                    probe.stage = 9;
                }
            }
            default -> {
            }
        }
    }
}
