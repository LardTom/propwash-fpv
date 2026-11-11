package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.dispense.DispenseServerChecks;
import de.lardtom.propwash.apiimpl.Api17ServerChecks;
import de.lardtom.propwash.barrier.BarrierServerChecks;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DroneTagChecks;
import de.lardtom.propwash.interact.BlockBreakRights;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.AssistServerChecks;
import de.lardtom.propwash.rescue.CovSurfaceServerChecks;
import de.lardtom.propwash.rescue.CovVineServerChecks;
import de.lardtom.propwash.rescue.HomePointChecks;
import de.lardtom.propwash.rescue.HomeResetChecks;
import de.lardtom.propwash.rescue.SurfaceServerChecks;
import de.lardtom.propwash.rx.CreativeRxServerChecks;
import de.lardtom.propwash.rx.RxServerChecks;
import de.lardtom.propwash.server.AddonWorldRulesServerChecks;
import de.lardtom.propwash.server.ClearServerChecks;
import de.lardtom.propwash.server.WorldRulesRights;
import de.lardtom.propwash.zone.ZoneServerChecks;
import de.lardtom.propwash.share.ShareServerChecks;
import de.lardtom.propwash.share.CovPw3ServerChecks;
import de.lardtom.propwash.share.chat.ShareChatJoinCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class ServerSmoke {
    public static final String PROPERTY = "propwash.serversmoke";
    public static final int RUN_TICKS = 100;
    private static final int ASSIST_TICK = 40;
    private static final int HOME_CHECK_TICK = 20;
    private static final int ZONE_TICK = 60;
    private static final int SURFACE_TICK = 30;
    private static final int RX_TICK = 5;
    private static final int CLEAR_TICK = 62;
    private static final int SHARE_TICK = 70;
    private static final long STOP_GRACE_MILLIS = 10_000L;

    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static int ticksSinceStart = -1;
    private static @Nullable QuadcopterEntity drone;
    private static @Nullable String failure;
    private static boolean finished;
    private static boolean resetFinished;
    private static boolean rxFinished;
    private static boolean clearFinished;
    private static boolean covSurfaceFinished;
    private static boolean covWorldFinished;

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
        Api17ServerChecks.init();
        Addon17JoinCheck.init();
        MultiJoinCheck.init();
        CovAddonRulesJoinCheck.init();
        CovNsOpJoinCheck.init();
        CovNoclipJoinCheck.init();
        CovShareJoinCheck.init();
        CovChatShareJoinCheck.init();
        CovWebexportJoinCheck.init();
        RaceP2pJoinCheck.init();
        AutonomyOfflineJoinCheck.init();
        AutonomyFarJoinCheck.init();
        AutonomyHomeJoinCheck.init();
        CourseMenuJoinCheck.init();
        CreativeFcJoinCheck.init();
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
            String admin = WorldRulesRights.checkPermissionMod(server);
            if (admin != null) {
                fail("Welt-Regeln (Admin ohne Operator): " + admin);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Welt-Regeln: Admin ohne Operator mit /gamerule-Recht (simulierter Rechte-Mod) ändert per Tab, Voreinstellung und Befehl, ohne und nach dem Entzug nur lesbar");
            String breaks = BlockBreakRights.check(server);
            if (breaks != null) {
                fail("Blockbruch: " + breaks);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Blockbruch: Spawn-Schutz lehnt den Gast schon bei der Freigabe vorab ab, Operator und außerhalb zerbrechen, Abenteuermodus und Regel aus lehnen ab");
            String tags = DroneTagChecks.check(server);
            if (tags != null) {
                fail("Drohnen-Tags: " + tags);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Drohnen-Tags: {}", DroneTagChecks.summary());
            String dispense = DispenseServerChecks.check(server);
            if (dispense != null) {
                fail("Werfer: " + dispense);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Werfer: {}", DispenseServerChecks.summary());
            String barriers = BarrierServerChecks.check(server);
            if (barriers != null) {
                fail("Drohnen-Barrieren: " + barriers);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Drohnen-Barrieren: {}", BarrierServerChecks.summary());
            String creativeRx = CreativeRxServerChecks.check(server);
            if (creativeRx != null) {
                fail("Kreativ-RX-Block: " + creativeRx);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Kreativ-RX-Block: {}", CreativeRxServerChecks.summary());
            String vines = CovVineServerChecks.check(server);
            if (vines != null) {
                fail("Ranken-Balken und Drohne im Boden: " + vines);
                return;
            }
            Constants.LOG.info("[Server-Rauchtest] Ranken-Balken und Drohne im Boden: {}", CovVineServerChecks.summary());
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
        if (ticksSinceStart == 1) {
            AssistServerChecks.forceChunks(server, true);
        }
        if (ticksSinceStart == ASSIST_TICK && failure == null) {
            String assist = AssistServerChecks.check(server);
            if (assist != null) {
                fail("Flughilfe: " + assist);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Flughilfe je Pilot: {}", AssistServerChecks.summary());
            }
        }
        if (ticksSinceStart == ZONE_TICK && failure == null) {
            String zones = ZoneServerChecks.check(server);
            if (zones != null) {
                fail("Zonen: " + zones);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Zonen: {}", ZoneServerChecks.summary());
            }
        }
        if (ticksSinceStart == SURFACE_TICK && failure == null) {
            String surface = SurfaceServerChecks.check(server);
            if (surface != null) {
                fail("Slime und Wolle: " + surface);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Slime und Wolle: {}", SurfaceServerChecks.summary());
            }
        }
        if (ticksSinceStart == RX_TICK && failure == null) {
            String rx = RxServerChecks.check(server);
            if (rx != null) {
                rxFinished = true;
                fail("RX-Block: " + rx);
            }
        }
        if (!rxFinished && failure == null && ticksSinceStart > RX_TICK && (RxServerChecks.advance(server) || ticksSinceStart >= RUN_TICKS - 5)) {
            rxFinished = true;
            String rx = RxServerChecks.finish(server);
            if (rx != null) {
                fail("RX-Block: " + rx);
            } else {
                Constants.LOG.info("[Server-Rauchtest] RX-Block: {}", RxServerChecks.summary());
            }
        }
        if (!clearFinished && failure == null && ticksSinceStart >= CLEAR_TICK && ClearServerChecks.tick(server, ticksSinceStart - CLEAR_TICK)) {
            clearFinished = true;
            String clear = ClearServerChecks.failure();
            if (clear != null) {
                fail("/propwash clear: " + clear);
            } else {
                Constants.LOG.info("[Server-Rauchtest] /propwash clear: {}", ClearServerChecks.summary());
            }
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.rx()) {
            RxJoinCheck.tick(server, ticksSinceStart);
        }
        if (clearFinished && !covWorldFinished && failure == null && CovWorldServerChecks.enabled() && CovWorldServerChecks.tick(server, ticksSinceStart)) {
            covWorldFinished = true;
            String cov = CovWorldServerChecks.failure();
            if (cov != null) {
                fail("Abdeckung cov-world: " + cov);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Abdeckung cov-world: {}", CovWorldServerChecks.summary());
            }
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.zones()) {
            ZoneJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.pilotSeconds() > 0) {
            PilotJoinCheck.tick(server, ticksSinceStart);
            CameraAngleJoinCheck.tick(server);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.assist()) {
            AssistJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.plates()) {
            PlateJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.stackGate()) {
            StackGateJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.signal()) {
            SignalJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.gateHint()) {
            GateHintJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.multi() != null) {
            MultiJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.crossLoader()) {
            CrossLoaderJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.addon17()) {
            Addon17JoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.addonData()) {
            AddonDataJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.collision()) {
            CollisionJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.share()) {
            ShareJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.shareChat()) {
            ShareChatJoinCheck.tick(server, ticksSinceStart);
        }
        if (ticksSinceStart == SHARE_TICK && failure == null) {
            String share = ShareServerChecks.check(server);
            if (share != null) {
                fail("Sharecode: " + share);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Sharecode: {}", ShareServerChecks.summary());
            }
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.raceHome()) {
            RaceHomeJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.freecam()) {
            FreecamJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.raceLoop()) {
            RaceLoopJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covFreecam()) {
            CovFreecamJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.raceData()) {
            RaceDataJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.operator()) {
            OperatorJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.accFix()) {
            AccFixJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covSurface()) {
            CovSurfaceJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covLanding()) {
            CovLandingJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covHeat()) {
            CovHeatJoinCheck.tick(server, ticksSinceStart);
        }
        if (!covSurfaceFinished && failure == null && CovSurfaceServerChecks.tickWithPlate(server, ticksSinceStart)) {
            covSurfaceFinished = true;
            String covSurface = CovSurfaceServerChecks.failure();
            if (covSurface != null) {
                fail("Heimflug landet auf Wolle, Slime und Druckplatte: " + covSurface);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Heimflug landet auf Wolle, Slime und Druckplatte: {}", CovSurfaceServerChecks.summary());
            }
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covRx() > 0) {
            CovRxJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covVersions()) {
            CovVersionsJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covWatchRescue()) {
            CovWatchRescueJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.droneScale()) {
            DroneScaleJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.barriers()) {
            BarrierJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.zonesOpen()) {
            ZonesOpenJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covAddonRules()) {
            CovAddonRulesJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covNoAddon()) {
            CovNoAddonJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covNoclip()) {
            CovNoclipJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covRace() > 0) {
            CovRaceJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.autoArm()) {
            AutoArmJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.raceRules()) {
            RaceRulesJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.raceP2p()) {
            RaceP2pJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.autonomyOffline() > 0) {
            AutonomyOfflineJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.autonomyFar()) {
            AutonomyFarJoinCheck.tick(server, ticksSinceStart);
            String far = AutonomyFarJoinCheck.done() ? AutonomyFarJoinCheck.failure() : null;
            if (far != null) {
                fail("Weiter Flug ohne Pilot: " + far);
            }
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.droneRotation()) {
            DroneRotationJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.autonomyHome()) {
            AutonomyHomeJoinCheck.tick(server, ticksSinceStart);
            String homeFlight = AutonomyHomeJoinCheck.done() ? AutonomyHomeJoinCheck.failure() : null;
            if (homeFlight != null) {
                fail("Heimflug außer Reichweite: " + homeFlight);
            }
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.gatePush()) {
            GatePushJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.raceGhosts()) {
            RaceGhostsJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.stats()) {
            StatsJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.capture()) {
            CaptureJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.courseMenu()) {
            CourseMenuJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.specCount() > 0) {
            SpecCountJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.creativeFc()) {
            CreativeFcJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covCrashRules()) {
            CovCrashRulesJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covVine()) {
            CovVineJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.droneSound()) {
            DroneSoundJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.covCrashWall()) {
            CovCrashWallJoinCheck.tick(server, ticksSinceStart);
        }
        if (JoinTestEnv.holdSeconds() > 0 && JoinTestEnv.menus()) {
            MenusJoinCheck.tick(server, ticksSinceStart);
        }
        if (ticksSinceStart == HOME_CHECK_TICK && failure == null) {
            String home = HomePointChecks.check(server);
            if (home != null) {
                fail("Homepoint: " + home);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Homepoint: {}", HomePointChecks.summary());
            }
            String reset = failure == null ? HomeResetChecks.check(server) : null;
            if (reset != null) {
                fail("Reset: " + reset);
            } else if (failure == null) {
                Constants.LOG.info("[Server-Rauchtest] Reset: {}", HomeResetChecks.summary());
            }
        }
        if (!resetFinished && failure == null && ticksSinceStart >= HOME_CHECK_TICK + HomeResetChecks.FINISH_DELAY_TICKS
                && (HomeResetChecks.finishReady(server) || ticksSinceStart >= RUN_TICKS - 5)) {
            resetFinished = true;
            String reset = HomeResetChecks.finish(server);
            if (reset != null) {
                fail("Reset: " + reset);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Reset: {}", HomeResetChecks.finishSummary());
            }
        }
        if (CovWorldServerChecks.enabled() && failure == null && !covWorldFinished && ticksSinceStart < CovWorldServerChecks.LIMIT_TICKS + 20) {
            return;
        }
        if (failure == null && !covSurfaceFinished && ticksSinceStart <= CovSurfaceServerChecks.LIMIT_TICKS) {
            return;
        }
        if (JoinTestEnv.holdSeconds() > 0 || failure == null && ticksSinceStart < RUN_TICKS) {
            return;
        }
        finished = true;
        if (failure == null && !clearFinished) {
            fail("/propwash clear: Prüfung nach " + ticksSinceStart + " Ticks nicht beendet");
        }
        if (failure == null && !covSurfaceFinished) {
            fail("Heimflug landet auf Wolle und Slime: Prüfung nach " + ticksSinceStart + " Ticks nicht beendet");
        }
        if (failure == null) {
            String api17 = Api17ServerChecks.check();
            if (api17 != null) {
                fail("API 1.7: " + api17);
            } else {
                Constants.LOG.info("[Server-Rauchtest] API 1.7: {}", Api17ServerChecks.summary());
            }
        }
        if (failure == null) {
            String addonRules = AddonWorldRulesServerChecks.check(server);
            if (addonRules != null) {
                fail("Add-on-Welt-Regeln: " + addonRules);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Add-on-Welt-Regeln: {}", AddonWorldRulesServerChecks.summary());
            }
        }
        if (failure == null) {
            String covApi = CovApiServerChecks.check(server);
            if (covApi != null) {
                fail("Abdeckung cov-api: " + covApi);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Abdeckung cov-api: {}", CovApiServerChecks.summary());
            }
        }
        if (failure == null) {
            String covPw3 = CovPw3ServerChecks.check(server);
            if (covPw3 != null) {
                fail("Abdeckung cov-pw3: " + covPw3);
            } else {
                Constants.LOG.info("[Server-Rauchtest] Abdeckung cov-pw3: {}", CovPw3ServerChecks.summary());
            }
        }
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
            long stopAt = System.currentTimeMillis();
            while (System.currentTimeMillis() - stopAt < STOP_GRACE_MILLIS && pending(serverOnlyChecks())) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    return;
                }
            }
            long waited = System.currentTimeMillis() - stopAt;
            if (waited >= 50L) {
                Constants.LOG.info("[Server-Rauchtest] Stopp {} ms aufgeschoben, bis die Prüfungen nur am Server fertig waren", waited);
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
                String result = stopResult(failure, serverOnlyChecks());
                Constants.LOG.info("[Server-Rauchtest] {}", result);
                writeResult(result);
                server.halt(false);
            });
        }, "Propwash-Jointest-Stop");
        watcher.setDaemon(true);
        watcher.start();
    }

    record ServerOnlyCheck(String label, boolean enabled, Supplier<@Nullable String> failure, BooleanSupplier pending) {
        ServerOnlyCheck(String label, boolean enabled, Supplier<@Nullable String> failure) {
            this(label, enabled, failure, () -> false);
        }
    }

    static boolean pending(List<ServerOnlyCheck> checks) {
        for (ServerOnlyCheck check : checks) {
            if (check.enabled() && check.pending().getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    static List<ServerOnlyCheck> serverOnlyChecks() {
        return List.of(
                new ServerOnlyCheck("Autonomie ohne Besitzer", JoinTestEnv.autonomyOfflineSim(), AutonomyOfflineJoinCheck::failure),
                new ServerOnlyCheck("Weiter Flug ohne Pilot", JoinTestEnv.autonomyFar(), AutonomyFarJoinCheck::failure),
                new ServerOnlyCheck("Heimflug außer Reichweite", JoinTestEnv.autonomyHome(), AutonomyHomeJoinCheck::failure),
                new ServerOnlyCheck("Kamera-Chunks", JoinTestEnv.capture(), CaptureJoinCheck::failure, CaptureJoinCheck::pending),
                new ServerOnlyCheck("Zuschauerzahl", JoinTestEnv.specCount() > 0, SpecCountJoinCheck::failure));
    }

    static String stopResult(@Nullable String failure, List<ServerOnlyCheck> checks) {
        String reason = failure;
        for (ServerOnlyCheck check : checks) {
            if (reason != null) {
                break;
            }
            String open = check.enabled() ? check.failure().get() : null;
            if (open != null) {
                reason = check.label() + ": " + open;
            }
        }
        return reason == null ? "PASS dedizierter Server, Beitrittstest beendet" : "FAIL " + reason;
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
