package de.lardtom.propwash.server.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovLanGuestHost {
    public static final String HOST = "PROPWASH_LANHOST_COV_GUEST";
    public static final String DIR = "lan-cov-guest";
    public static final String READY = "ready";
    public static final String STEP = "server: Abdeckung LAN-Gast – Gastgeber gibt die Welt ohne Cheats für Gäste frei, der Gast darf weder Welt-Regeln noch "
            + "/propwash clear";
    public static final int HOST_TICKS = 20 * 60 * 30;
    private static final int GUEST_WAIT = 20 * 60 * 25;
    private static final int GUEST_STAY = 20 * 60 * 4;
    private static final String PHASE = "cov_lan_phase";
    private static final String MARK = "cov_lan_mark";
    private static final String FUTURE = "cov_lan_future";
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();

    private CovLanGuestHost() {
    }

    public static boolean hostEnabled() {
        String value = System.getenv(HOST);
        return value != null && !value.isBlank() && !value.trim().equals("0");
    }

    public static void register() {
        if (hostEnabled()) {
            SelfTest.clientStep(STEP, HOST_TICKS, CovLanGuestHost::step);
        }
    }

    public static Path dir() {
        return Services.PLATFORM.getGameDir().resolve(DIR);
    }

    private static void next(Map<String, Object> state, int phase, int tick) {
        state.put(PHASE, phase);
        state.put(MARK, tick);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return StepResult.fail("keine Einzelspielerwelt");
        }
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = ctx.tick() - (int) state.getOrDefault(MARK, 0);
        switch (phase) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(CovLanGuestHost::prepare));
                next(state, 1, ctx.tick());
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Vorbereitung: " + future.exceptionNow());
                }
                int port = HttpUtil.getAvailablePort();
                server.setUsesAuthentication(false);
                if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port)) {
                    return StepResult.fail("LAN-Freigabe auf Port " + port + " fehlgeschlagen");
                }
                try {
                    Files.createDirectories(dir());
                    Files.writeString(dir().resolve(READY), Integer.toString(port), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return StepResult.fail("Port nicht geschrieben: " + e);
                }
                ctx.log("LAN-Welt auf Port " + port + " ohne Cheats für Gäste freigegeben (Gast-Befehle " + server.getGuestCommandAccess() + ")");
                next(state, 2, ctx.tick());
            }
            case 2 -> {
                if (server.getPlayerCount() < 2) {
                    return since > GUEST_WAIT ? StepResult.fail("kein Gast kam in die LAN-Welt") : StepResult.pending();
                }
                ctx.log("Gast ist da");
                next(state, 3, ctx.tick());
            }
            case 3 -> {
                if (server.getPlayerCount() >= 2) {
                    return since > GUEST_STAY ? StepResult.fail("Gast blieb länger als " + GUEST_STAY / 1200 + " min") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(s -> {
                    ServerLevel level = s.overworld();
                    UUID id = DRONE.get();
                    if (id == null || !(level.getEntity(id) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
                        throw new IllegalStateException("Drohne des Gastgebers nach dem Besuch des Gastes weg");
                    }
                    drone.discard();
                }));
                next(state, 4, ctx.tick());
            }
            default -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail(String.valueOf(future.exceptionNow().getMessage()));
                }
                ctx.log("Gast ist gegangen; Drohne des Gastgebers blieb stehen");
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static void prepare(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        int x = player.getBlockX() + 4;
        int z = player.getBlockZ() + 2;
        DRONE.set(CovClearSelfTests.spawn(level, new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5)).getUUID());
    }
}
