package de.lardtom.propwash.rx;

import de.lardtom.propwash.server.WorldRulesRights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CreativeRxServerChecks {
    public static final int FAR_BLOCKS = 20_000;
    private static final RxSource CREATIVE = RxSource.axis(5);
    private static final RxSource NORMAL = RxSource.axis(6);
    private static final RxSource NETHER = RxSource.axis(7);
    private static String summary = "";

    private CreativeRxServerChecks() {
    }

    public static String summary() {
        return summary;
    }

    private static @Nullable String expect(RxServer.Outcome outcome, RxServer.Outcome wanted, String what) {
        return outcome == wanted ? null : what + ": " + outcome + " statt " + wanted;
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos pos = spawn.offset(-2, 3, -2);
        BlockPos normal = pos.west(2);
        ServerLevel nether = server.getLevel(Level.NETHER);
        BlockPos netherPos = new BlockPos(spawn.getX() >> 3, 100, spawn.getZ() >> 3);
        ServerPlayer guest = WorldRulesRights.guest(server);
        List<String> notes = new ArrayList<>();
        try {
            if (!RxServer.active(level, pos)) {
                return "Spawn-Chunk tickt keine Blöcke";
            }
            level.setBlock(pos, RxModule.CREATIVE_RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(normal, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(pos) instanceof RxBlockEntity creative) || !(level.getBlockEntity(normal) instanceof RxBlockEntity rx)) {
                return "keine Block-Entity am Kreativ-RX-Block oder am RX-Block";
            }
            if (RxIndex.of(level).at(pos) != creative) {
                return "Verzeichnis ohne den Kreativ-RX-Block";
            }
            guest.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 0.0F, 0.0F);
            String problem = expect(RxServer.config(guest, RxConfigC2S.bind(pos)), RxServer.Outcome.ACCEPTED, "Binden");
            if (problem == null) {
                problem = expect(RxServer.config(guest, RxConfigC2S.source(pos, CREATIVE)), RxServer.Outcome.ACCEPTED, "Kanal AUX 6");
            }
            if (problem == null) {
                problem = expect(RxServer.config(guest, RxConfigC2S.range(pos, RxLink.MIN_RANGE)), RxServer.Outcome.ACCEPTED, "Reichweite 1");
            }
            if (problem != null) {
                return problem;
            }
            if (!creative.settings().ownedBy(guest.getUUID()) || !CREATIVE.equals(creative.settings().source())) {
                return "Bindung oder Kanal nicht übernommen: " + creative.settings();
            }
            notes.add("Bindung und Kanalwahl wie beim RX-Block");
            rx.apply(new RxSettings(guest.getUUID(), NORMAL, -1.0F, 1.0F, RxLink.MAX_RANGE));
            RxMask near = RxServer.demandOf(guest);
            if (!near.contains(CREATIVE) || !near.contains(NORMAL)) {
                return "Bedarf nebenan: " + near.describe();
            }
            guest.snapTo(pos.getX() + FAR_BLOCKS + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            RxMask far = RxServer.demandOf(guest);
            if (!far.contains(CREATIVE) || far.contains(NORMAL)) {
                return "Bedarf " + FAR_BLOCKS + " Blöcke weg: " + far.describe() + " statt nur AUX 6 des Kreativ-RX-Blocks";
            }
            notes.add(FAR_BLOCKS + " Blöcke weg: Bedarf nur für den Kreativ-RX-Block mit Reichweite 1, nicht für den RX-Block mit 128");
            if (nether != null) {
                nether.setBlock(netherPos, RxModule.CREATIVE_RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                if (!(nether.getBlockEntity(netherPos) instanceof RxBlockEntity other)) {
                    return "keine Block-Entity am Kreativ-RX-Block im Nether";
                }
                other.apply(new RxSettings(guest.getUUID(), NETHER, -1.0F, 1.0F, RxLink.MIN_RANGE));
                RxMask overworld = RxServer.demandOf(guest);
                if (overworld.contains(NETHER)) {
                    return "Kreativ-RX-Block im Nether erzeugt Bedarf in der Oberwelt: " + overworld.describe();
                }
                notes.add("Kreativ-RX-Block im Nether ohne Bedarf für den Spieler in der Oberwelt");
            }
            summary = String.join(", ", notes);
            return null;
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(normal, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (nether != null && nether.getBlockState(netherPos).getBlock() instanceof RxBlock) {
                nether.setBlock(netherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            RxServer.forget(guest.getUUID());
        }
    }
}
