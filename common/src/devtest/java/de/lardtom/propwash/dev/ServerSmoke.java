package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.interact.BlockBreakRights;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.WorldRulesRights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ServerSmoke {
    public static final String PROPERTY = "propwash.serversmoke";
    public static final int RUN_TICKS = 100;

    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static int ticksSinceStart = -1;
    private static @Nullable QuadcopterEntity drone;
    private static @Nullable String failure;
    private static boolean finished;

    private ServerSmoke() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void init() {
        if (!ENABLED) {
            return;
        }
        ServerBus.STARTED.register(ServerSmoke::onStarted);
        ServerBus.TICK_END.register(ServerSmoke::onTickEnd);
    }

    private static void onStarted(MinecraftServer server) {
        ticksSinceStart = 0;
        if (JoinTestEnv.holdSeconds() > 0) {
            server.setUsingWhitelist(false);
            try {
                Files.deleteIfExists(Services.PLATFORM.getGameDir().resolve(JoinTestEnv.STOP_FILE));
            } catch (IOException e) {
                fail("alte " + JoinTestEnv.STOP_FILE + " nicht gelöscht: " + e);
            }
            watchStopFile(server);
        }
        if (!server.isDedicatedServer()) {
            fail("kein dedizierter Server");
            return;
        }
        try {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getRespawnData().pos();
            QuadcopterEntity created = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (created == null) {
                fail("Drohne nicht erzeugt");
                return;
            }
            level.setChunkForced(spawn.getX() >> 4, spawn.getZ() >> 4, true);
            created.snapTo(spawn.getX() + 0.5, spawn.getY() + 4.0, spawn.getZ() + 0.5, 0.0F, 0.0F);
            if (!level.addFreshEntity(created)) {
                fail("Drohne nicht gespawnt");
                return;
            }
            drone = created;
            String rights = WorldRulesRights.check(server, true);
            if (rights != null) {
                fail("Welt-Regeln: " + rights);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Welt-Regeln: Gast ohne Recht abgelehnt, als Operator übernommen");
            String breaks = BlockBreakRights.check(server);
            if (breaks != null) {
                fail("Blockbruch: " + breaks);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Blockbruch: Spawn-Schutz lehnt den Gast schon bei der Freigabe vorab ab, Operator und außerhalb zerbrechen, Abenteuermodus und Regel aus lehnen ab");
            Constants.LOG.info("[Server-Rauchtest] gestartet, läuft {} Ticks{}", runTicks(),
                    JoinTestEnv.holdSeconds() > 0 ? " oder bis " + JoinTestEnv.STOP_FILE + " (Beitrittstest)" : "");
        } catch (RuntimeException e) {
            Constants.LOG.error("[Server-Rauchtest] Start fehlgeschlagen", e);
            fail("Ausnahme beim Start: " + e);
        }
    }

    private static void onTickEnd(MinecraftServer server) {
        if (ticksSinceStart < 0 || finished) {
            return;
        }
        ticksSinceStart++;
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.pilotSeconds() > 0) {
            PilotJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 || failure == null && ticksSinceStart < RUN_TICKS) {
            return;
        }
        finished = true;
        QuadcopterEntity spawned = drone;
        if (failure == null && (spawned == null || spawned.isRemoved())) {
            fail("Drohne nach " + ticksSinceStart + " Ticks nicht mehr vorhanden"
                    + (spawned == null ? "" : " (" + spawned.getRemovalReason() + ")"));
        }
        if (spawned != null && !spawned.isRemoved()) {
            spawned.discard();
        }
        String result = failure == null ? "PASS dedizierter Server, " + ticksSinceStart + " Ticks" : "FAIL " + failure;
        Constants.LOG.info("[Server-Rauchtest] {}", result);
        writeResult(result);
        server.halt(false);
    }

    private static int runTicks() {
        int hold = JoinTestEnv.holdSeconds();
        return hold > 0 ? hold * 20 : RUN_TICKS;
    }

    private static void watchStopFile(MinecraftServer server) {
        long deadline = System.currentTimeMillis() + JoinTestEnv.holdSeconds() * 1000L;
        Thread watcher = new Thread(() -> {
            while (System.currentTimeMillis() < deadline && !Files.exists(Services.PLATFORM.getGameDir().resolve(JoinTestEnv.STOP_FILE))) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    return;
                }
            }
            server.execute(() -> {
                if (finished) {
                    return;
                }
                finished = true;
                QuadcopterEntity spawned = drone;
                if (spawned != null && !spawned.isRemoved()) {
                    spawned.discard();
                }
                String result = failure == null ? "PASS dedizierter Server, Beitrittstest beendet" : "FAIL " + failure;
                Constants.LOG.info("[Server-Rauchtest] {}", result);
                writeResult(result);
                server.halt(false);
            });
        }, "Propwash-Jointest-Stop");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void fail(String reason) {
        if (failure == null) {
            failure = reason;
        }
    }

    private static void writeResult(String result) {
        try {
            Files.writeString(Services.PLATFORM.getGameDir().resolve(SelfTest.RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Server-Rauchtest] Ergebnisdatei nicht geschrieben", e);
        }
    }
}
