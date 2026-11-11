package de.lardtom.propwash.rx;

import de.lardtom.propwash.server.WorldRulesRights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RxServerChecks {
    public static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-00000000f00d");
    public static final int FAR_CHUNKS = 18;
    private static volatile String summary = "";
    private static String summaryOfInputs = "";
    private static @Nullable Pending pending;

    private RxServerChecks() {
    }

    public static String summary() {
        return summary;
    }

    private static final class Pending {
        final BlockPos far;
        final List<String> notes;
        int stage;
        int ticks;
        @Nullable String failure;

        Pending(BlockPos far, List<String> notes) {
            this.far = far;
            this.notes = notes;
        }
    }

    private static RxServer.Outcome config(ServerPlayer player, RxConfigC2S payload) {
        return RxServer.config(player, payload);
    }

    private static @Nullable String expect(RxServer.Outcome outcome, RxServer.Outcome wanted, String what) {
        return outcome == wanted ? null : what + ": " + outcome + " statt " + wanted;
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos pos = spawn.offset(2, 3, 2);
        BlockPos unloaded = spawn.offset(FAR_CHUNKS * 16 + 8, 0, -FAR_CHUNKS * 16 - 8);
        ServerPlayer guest = WorldRulesRights.guest(server);
        List<String> notes = new ArrayList<>();
        try {
            if (!RxServer.active(level, pos)) {
                return "Spawn-Chunk tickt keine Blöcke";
            }
            level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity placed = level.getBlockEntity(pos);
            if (!(placed instanceof RxBlockEntity entity)) {
                return "keine Block-Entity am RX-Block";
            }
            RxIndex index = RxIndex.of(level);
            if (!index.contains(pos) || index.at(pos) != entity) {
                return "Verzeichnis ohne den gesetzten Block";
            }
            guest.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 0.0F, 0.0F);
            String problem = expect(config(guest, RxConfigC2S.source(pos, RxSource.GAS)), RxServer.Outcome.INVALID, "Kanal ohne Bindung");
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.bind(pos)), RxServer.Outcome.ACCEPTED, "Binden");
            }
            if (problem == null && !entity.settings().ownedBy(guest.getUUID())) {
                problem = "Bindung nicht übernommen";
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.source(pos, RxSource.GAS)), RxServer.Outcome.ACCEPTED, "Kanal Gas");
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.low(pos, Float.NaN)), RxServer.Outcome.INVALID, "0 % = NaN");
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.high(pos, Float.POSITIVE_INFINITY)), RxServer.Outcome.INVALID, "100 % = unendlich");
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.high(pos, 1.5F)), RxServer.Outcome.INVALID, "100 % = 1,5");
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.range(pos, RxLink.MAX_RANGE + 1)), RxServer.Outcome.INVALID, "Reichweite 129");
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.range(pos, 0)), RxServer.Outcome.INVALID, "Reichweite 0");
            }
            if (problem == null) {
                problem = expect(config(guest, new RxConfigC2S(pos, RxConfigC2S.Action.RANGE, null, 64.5F)), RxServer.Outcome.INVALID,
                        "Reichweite 64,5");
            }
            if (problem == null) {
                problem = expect(config(guest, RxConfigC2S.range(pos, 100)), RxServer.Outcome.ACCEPTED, "Reichweite 100");
            }
            if (problem == null && (entity.settings().range() != 100 || !RxSource.GAS.equals(entity.settings().source())
                    || entity.settings().low() != -1.0F || entity.settings().high() != 1.0F)) {
                problem = "Einstellungen nach den abgelehnten Werten verändert: " + entity.settings();
            }
            if (problem != null) {
                return problem;
            }
            notes.add("NaN, unendlich, 1,5 und Reichweiten 0/129/64,5 abgelehnt");
            entity.apply(entity.settings().withOwner(STRANGER));
            for (RxConfigC2S payload : List.of(RxConfigC2S.source(pos, RxSource.axis(6)), RxConfigC2S.unbind(pos), RxConfigC2S.bind(pos),
                    RxConfigC2S.range(pos, 5), RxConfigC2S.low(pos, 0.0F))) {
                problem = expect(config(guest, payload), RxServer.Outcome.FOREIGN, "fremder Block " + payload.action());
                if (problem != null) {
                    return problem;
                }
            }
            if (!entity.settings().ownedBy(STRANGER) || entity.settings().range() != 100) {
                return "fremder Block verändert: " + entity.settings();
            }
            notes.add("fremder Block abgelehnt (5 Aktionen)");
            server.getPlayerList().op(guest.nameAndId());
            try {
                problem = expect(config(guest, RxConfigC2S.range(pos, 32)), RxServer.Outcome.ACCEPTED, "Operator ändert fremden Block");
                if (problem != null || entity.settings().range() != 32 || !entity.settings().ownedBy(STRANGER)) {
                    return problem != null ? problem : "Operator-Änderung nicht übernommen";
                }
            } finally {
                server.getPlayerList().deop(guest.nameAndId());
            }
            notes.add("Operator darf");
            guest.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 40.5, 0.0F, 0.0F);
            problem = expect(config(guest, RxConfigC2S.source(pos, RxSource.GAS)), RxServer.Outcome.TOO_FAR, "zu weit weg");
            if (problem != null) {
                return problem;
            }
            notes.add("zu weit weg abgelehnt");
            int cx = unloaded.getX() >> 4;
            int cz = unloaded.getZ() >> 4;
            if (level.getChunkSource().hasChunk(cx, cz)) {
                return "Prüf-Chunk " + cx + "," + cz + " ist schon geladen";
            }
            guest.snapTo(unloaded.getX() + 0.5, unloaded.getY(), unloaded.getZ() + 1.5, 0.0F, 0.0F);
            problem = expect(config(guest, RxConfigC2S.bind(unloaded)), RxServer.Outcome.NOT_LOADED, "Block in nicht geladenem Chunk");
            if (problem != null) {
                return problem;
            }
            if (level.getChunkSource().hasChunk(cx, cz) || level.getChunkSource().getChunkNow(cx, cz) != null) {
                return "Einstellung an nicht geladenem Chunk hat ihn geladen";
            }
            notes.add("nicht geladener Chunk abgelehnt und nicht geladen");
            problem = inputs(server, level, guest, entity, pos);
            if (problem != null) {
                return problem;
            }
            notes.add(summaryOfInputs);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (index.contains(pos)) {
                return "Verzeichnis nach dem Abbau nicht leer";
            }
            BlockPos far = spawn.offset(-FAR_CHUNKS * 16 - 7, 3, FAR_CHUNKS * 16 + 3);
            if (level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4)) {
                return "Entlade-Chunk ist schon geladen";
            }
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
            pending = new Pending(far, notes);
            return null;
        } finally {
            RxServer.forget(guest.getUUID());
            if (server.getPlayerList().isOp(guest.nameAndId())) {
                server.getPlayerList().deop(guest.nameAndId());
            }
        }
    }

    private static @Nullable String inputs(MinecraftServer server, ServerLevel level, ServerPlayer guest, RxBlockEntity entity, BlockPos pos) {
        guest.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 0.0F, 0.0F);
        float[] sticks = {0.0F, 0.0F, 0.0F, 0.5F};
        float[] axes = new float[RxSource.AXES];
        RxServer.forget(guest.getUUID());
        String problem = expect(RxServer.input(guest, RxInputC2S.of(1, RxMask.of(RxSource.GAS), sticks, axes, 0)), RxServer.Outcome.NO_DEMAND,
                "Werte ohne Bedarf");
        if (problem != null) {
            return problem;
        }
        entity.apply(entity.settings().withOwner(guest.getUUID()).withSource(RxSource.GAS));
        RxServer.refreshDemand(guest);
        if (!RxServer.sentDemand(guest.getUUID()).equals(RxMask.of(RxSource.GAS))) {
            return "Bedarf nicht Gas: " + RxServer.sentDemand(guest.getUUID()).describe();
        }
        problem = expect(RxServer.input(guest, RxInputC2S.of(2, RxMask.of(RxSource.axis(6)), sticks, axes, 0)), RxServer.Outcome.OUTSIDE_DEMAND,
                "Werte außerhalb des Bedarfs");
        if (problem == null) {
            problem = expect(RxServer.input(guest, RxInputC2S.of(3, RxMask.of(RxSource.GAS), sticks, axes, 0)), RxServer.Outcome.ACCEPTED, "Gas 50 %");
        }
        if (problem == null) {
            problem = expect(RxServer.input(guest, RxInputC2S.of(3, RxMask.of(RxSource.GAS), sticks, axes, 0)), RxServer.Outcome.OLD_SEQUENCE,
                    "alte Folgenummer");
        }
        if (problem != null) {
            return problem;
        }
        if (Math.abs(RxServer.lastValue(guest.getUUID(), RxSource.GAS) - 0.5F) > 1.0E-3F) {
            return "Gaswert nicht übernommen: " + RxServer.lastValue(guest.getUUID(), RxSource.GAS);
        }
        int accepted = 0;
        int limited = 0;
        for (int i = 0; i < 40; i++) {
            RxServer.Outcome outcome = RxServer.input(guest, RxInputC2S.of(10 + i, RxMask.of(RxSource.GAS), sticks, axes, 0));
            if (outcome == RxServer.Outcome.ACCEPTED) {
                accepted++;
            } else if (outcome == RxServer.Outcome.RATE_LIMITED) {
                limited++;
            }
        }
        if (accepted > RxServer.MAX_INPUTS_PER_SECOND - 2 || limited == 0) {
            return "Ratenbegrenzung greift nicht: " + accepted + " angenommen, " + limited + " verworfen";
        }
        summaryOfInputs = "Werte ohne Bedarf, außerhalb des Bedarfs und mit alter Folgenummer abgelehnt, höchstens "
                + RxServer.MAX_INPUTS_PER_SECOND + " Pakete je Sekunde (" + limited + " von 40 verworfen)";
        RxServer.forget(guest.getUUID());
        return null;
    }

    public static boolean advance(MinecraftServer server) {
        Pending state = pending;
        if (state == null) {
            return true;
        }
        ServerLevel level = server.overworld();
        state.ticks++;
        int cx = state.far.getX() >> 4;
        int cz = state.far.getZ() >> 4;
        switch (state.stage) {
            case 0 -> {
                if (!RxServer.active(level, state.far)) {
                    return false;
                }
                level.setBlock(state.far, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                if (!(level.getBlockEntity(state.far) instanceof RxBlockEntity entity)) {
                    state.failure = "Block im Entlade-Chunk ohne Block-Entity";
                    return true;
                }
                entity.apply(entity.settings().withOwner(STRANGER).withSource(RxSource.GAS));
                if (!RxIndex.of(level).contains(state.far)) {
                    state.failure = "Verzeichnis ohne den Block im Entlade-Chunk";
                    return true;
                }
                level.setChunkForced(cx, cz, false);
                state.stage = 1;
                return false;
            }
            case 1 -> {
                RxIndex index = RxIndex.existing(level);
                return !level.getChunkSource().hasChunk(cx, cz) && (index == null || !index.contains(state.far));
            }
            default -> {
                return true;
            }
        }
    }

    public static @Nullable String finish(MinecraftServer server) {
        Pending state = pending;
        pending = null;
        if (state == null) {
            return null;
        }
        ServerLevel level = server.overworld();
        if (state.failure != null) {
            return state.failure;
        }
        int cx = state.far.getX() >> 4;
        int cz = state.far.getZ() >> 4;
        if (state.stage == 0) {
            level.setChunkForced(cx, cz, false);
            return "Entlade-Chunk nach " + state.ticks + " Ticks nicht geladen";
        }
        if (level.getChunkSource().hasChunk(cx, cz)) {
            return "Entlade-Chunk nach " + state.ticks + " Ticks nicht entladen";
        }
        RxIndex index = RxIndex.existing(level);
        if (index != null && index.contains(state.far)) {
            return "Verzeichnis enthält den Block nach dem Entladen noch";
        }
        int left = index == null ? 0 : index.size();
        state.notes.add("Verzeichnis nach dem Entladen ohne den Block (" + left + " Einträge übrig, " + state.ticks + " Ticks)");
        summary = String.join(", ", state.notes);
        return null;
    }
}
