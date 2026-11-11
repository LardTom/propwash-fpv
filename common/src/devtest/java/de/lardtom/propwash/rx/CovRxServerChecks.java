package de.lardtom.propwash.rx;

import de.lardtom.propwash.server.CovPlayerListAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CovRxServerChecks {
    public static final String PILOT_A = "CovRxPilotA";
    public static final String PILOT_B = "CovRxPilotB";
    private static final UUID A_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0b01");
    private static final UUID B_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0b02");
    private static final int BASE_CHUNKS = -6;
    private static final int READY_TICKS = 200;
    private static final float[][] STEPS = {{1.0F, Float.NaN}, {Float.NaN, 0.0F}, {-1.0F, Float.NaN}, {Float.NaN, 1.0F}};

    private static int stage;
    private static int since;
    private static int step;
    private static int sequenceA;
    private static int sequenceB;
    private static @Nullable String failure;
    private static String summary = "";
    private static @Nullable BlockPos blockA;
    private static @Nullable BlockPos blockB;
    private static @Nullable ServerPlayer pilotA;
    private static @Nullable ServerPlayer pilotB;
    private static final List<String> NOTES = new ArrayList<>();
    private static int beforeA;
    private static int beforeB;

    private CovRxServerChecks() {
    }

    public static @Nullable String failure() {
        return failure;
    }

    public static String summary() {
        return summary;
    }

    public static boolean tick(MinecraftServer server) {
        if (stage >= 3) {
            return true;
        }
        since++;
        try {
            if (stage == 0) {
                setup(server);
                stage = 1;
                since = 0;
                return false;
            }
            ServerLevel level = server.overworld();
            BlockPos a = blockA;
            BlockPos b = blockB;
            if (a == null || b == null) {
                return done(server, "Blöcke fehlen");
            }
            if (stage == 1) {
                RxServer.refreshDemand(pilotA);
                RxServer.refreshDemand(pilotB);
                boolean ready = RxServer.sentDemand(A_ID).equals(RxMask.of(RxSource.GAS)) && RxServer.sentDemand(B_ID).equals(RxMask.of(RxSource.GAS))
                        && RxServer.active(level, a) && RxServer.active(level, b);
                if (!ready) {
                    return since > READY_TICKS && done(server, "Bedarf Gas nicht für beide Spieler: " + RxServer.sentDemand(A_ID).describe() + " / "
                            + RxServer.sentDemand(B_ID).describe());
                }
                stage = 2;
                since = 0;
                step = 0;
                return false;
            }
            beforeA = power(level, a);
            beforeB = power(level, b);
            float[] step0 = STEPS[step];
            String sent;
            online(server, pilotA, true);
            online(server, pilotB, true);
            try {
                sent = send(step0[0], step0[1]);
                if (sent == null) {
                    evaluate(server);
                }
            } finally {
                online(server, pilotA, false);
                online(server, pilotB, false);
            }
            if (sent != null) {
                return done(server, sent);
            }
            float[] values = STEPS[step];
            int nowA = power(level, a);
            int nowB = power(level, b);
            String problem = expect("A", values[0], beforeA, nowA);
            if (problem == null) {
                problem = expect("B", values[1], beforeB, nowB);
            }
            if (problem != null) {
                return done(server, "Schritt " + (step + 1) + ": " + problem + " (A " + beforeA + " → " + nowA + ", B " + beforeB + " → " + nowB + ")");
            }
            NOTES.add((Float.isNaN(values[0]) ? "B" : "A") + " Gas " + (Float.isNaN(values[0]) ? values[1] : values[0]) + ": A " + nowA + ", B "
                    + nowB);
            step++;
            since = 0;
            if (step >= STEPS.length) {
                summary = "zwei simulierte Spieler binden je einen Block auf Gas, abwechselnde Eingaben – jeder Block folgt nur seinem Besitzer ("
                        + String.join("; ", NOTES) + ")";
                return done(server, null);
            }
            return false;
        } catch (RuntimeException e) {
            return done(server, "Ausnahme: " + e);
        }
    }

    private static void evaluate(MinecraftServer server) {
        try {
            Method method = RxServer.class.getDeclaredMethod("evaluate", MinecraftServer.class);
            method.setAccessible(true);
            method.invoke(null, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RxServer.evaluate nicht aufrufbar: " + e, e);
        }
    }

    private static @Nullable String send(float a, float b) {
        float[] axes = new float[RxSource.AXES];
        if (!Float.isNaN(a)) {
            RxServer.Outcome outcome = RxServer.input(pilotA, RxInputC2S.of(++sequenceA, RxMask.of(RxSource.GAS), new float[]{0.0F, 0.0F, 0.0F, a},
                    axes, 0));
            if (!outcome.accepted() && outcome != RxServer.Outcome.RATE_LIMITED) {
                return "Eingabe von A abgelehnt: " + outcome;
            }
        }
        if (!Float.isNaN(b)) {
            RxServer.Outcome outcome = RxServer.input(pilotB, RxInputC2S.of(++sequenceB, RxMask.of(RxSource.GAS), new float[]{0.0F, 0.0F, 0.0F, b},
                    axes, 0));
            if (!outcome.accepted() && outcome != RxServer.Outcome.RATE_LIMITED) {
                return "Eingabe von B abgelehnt: " + outcome;
            }
        }
        return null;
    }

    private static @Nullable String expect(String who, float sent, int before, int now) {
        if (Float.isNaN(sent)) {
            return now == before ? null : who + " änderte sich ohne eigene Eingabe";
        }
        int wanted = sent >= 1.0F ? 15 : sent <= -1.0F ? 0 : -1;
        if (wanted < 0) {
            return now == RxMapping.CENTRE ? null : who + " folgt nicht (Stärke " + now + " statt " + RxMapping.CENTRE + ")";
        }
        return now == wanted ? null : who + " folgt nicht (Stärke " + now + " statt " + wanted + ")";
    }

    private static int power(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
    }

    private static void setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int cx = (spawn.getX() >> 4) + BASE_CHUNKS;
        int cz = (spawn.getZ() >> 4) - BASE_CHUNKS;
        level.setChunkForced(cx, cz, true);
        level.getChunk(cx, cz);
        int x = cx * 16 + 4;
        int z = cz * 16 + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        blockA = new BlockPos(x, y, z);
        blockB = new BlockPos(x + 8, y, z);
        pilotA = player(server, A_ID, PILOT_A);
        pilotB = player(server, B_ID, PILOT_B);
        for (BlockPos pos : List.of(blockA, blockB)) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
        }
        bind(level, blockA, A_ID);
        bind(level, blockB, B_ID);
        pilotA.snapTo(blockA.getX() + 0.5, blockA.getY(), blockA.getZ() + 3.5, 0.0F, 0.0F);
        pilotB.snapTo(blockB.getX() + 0.5, blockB.getY(), blockB.getZ() + 3.5, 0.0F, 0.0F);
    }

    private static void bind(ServerLevel level, BlockPos pos, UUID owner) {
        if (!(level.getBlockEntity(pos) instanceof RxBlockEntity entity)) {
            throw new IllegalStateException("RX-Block ohne Block-Entity bei " + pos);
        }
        entity.apply(new RxSettings(owner, RxSource.GAS, -1.0F, 1.0F, 32));
    }

    private static void online(MinecraftServer server, @Nullable ServerPlayer player, boolean add) {
        if (player != null) {
            CovPlayerListAccess.online(server, player, add, true);
        }
    }

    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static boolean done(MinecraftServer server, @Nullable String problem) {
        failure = problem;
        stage = 3;
        try {
            online(server, pilotA, false);
            online(server, pilotB, false);
        } catch (RuntimeException e) {
            failure = failure == null ? "Aufräumen: " + e : failure;
        }
        RxServer.forget(A_ID);
        RxServer.forget(B_ID);
        ServerLevel level = server.overworld();
        for (BlockPos pos : new BlockPos[]{blockA, blockB}) {
            if (pos != null && level.getBlockState(pos).getBlock() instanceof RxBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (blockA != null) {
            level.setChunkForced(blockA.getX() >> 4, blockA.getZ() >> 4, false);
        }
        return true;
    }
}
