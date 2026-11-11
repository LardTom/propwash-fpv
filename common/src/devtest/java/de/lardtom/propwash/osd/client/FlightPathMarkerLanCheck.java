package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.FlightPathMarker;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class FlightPathMarkerLanCheck {
    public static final String HOST = "PROPWASH_LANHOST_FPM";
    public static final String DIR = "lan-fpm";
    public static final String READY = "ready";
    public static final String HOST_STEP = "lan-fpm: Gastgeber fliegt im FPV, ein zweiter Client im LAN schaut per F7 zu und sieht den Flugbahn-Marker";
    public static final int HOST_TICKS = 20 * 60 * 50;
    public static final int OBSERVER_EXTRA_TICKS = 20 * 60 * 5;
    private static final int OBSERVER_WAIT_TICKS = 20 * 60 * 45;
    private static final int FLIGHT_TICKS = 20 * 60 * 5;
    private static final int TAKEOFF_DELAY = 40;
    private static final int ARM_TICKS = 20;
    private static final int LOG_EVERY = 40;
    private static final double CRUISE_HEIGHT = 7.0;
    private static final double CRUISE_FROM = 5.0;
    private static final float CRUISE_PITCH = 0.2F;
    private static final float CRUISE_YAW = 0.12F;
    private static final double MOVING_SPEED = FlightPathMarker.FULL_FROM;
    private static final int MIN_MOVING_TICKS = 100;
    private static final int PHOTO_WAIT_TICKS = 200;
    private static final int FIND_TICKS = 20 * 60 * 4;
    private static final int REQUEST_RETRY_TICKS = 100;
    private static final Vec3 OBSERVER_OFFSET = new Vec3(-8.0, 0.0, 0.0);
    private static final String FUTURE = "lan_fpm_future";
    private static final String PHASE = "lan_fpm_phase";
    private static final String MARK = "lan_fpm_mark";
    private static final String FLOWN = "lan_fpm_flown";
    private static final String WATCH_START = "fpm_watch_start";
    private static final String WATCH_FROM = "fpm_watch_from";
    private static final String REQUESTED = "fpm_requested";
    private static final String COUNTERS = "fpm_counters";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static volatile double groundY;
    private static volatile Vec3 start = Vec3.ZERO;
    private static int cruiseSince = -1;

    private static final class Counters {
        int watched;
        int moving;
        int drawn;
        int drawnMoving;
        int inView;
        double maxSpeed;
    }

    private FlightPathMarkerLanCheck() {
    }

    public static boolean hostEnabled() {
        String value = System.getenv(HOST);
        return value != null && !value.isBlank() && !value.trim().equals("0");
    }

    public static Path dir() {
        return Services.PLATFORM.getGameDir().resolve(DIR);
    }

    public static StepResult hostStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return StepResult.fail("keine Einzelspielerwelt");
        }
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = ctx.tick() - (int) state.getOrDefault(MARK, 0);
        PilotSession session = PilotSession.INSTANCE;
        switch (phase) {
            case 0 -> {
                cruiseSince = -1;
                state.put(FUTURE, ctx.onServer(FlightPathMarkerLanCheck::prepare));
                next(state, 1, ctx.tick());
            }
            case 1 -> {
                StepResult waiting = await(ctx, "Vorbereitung");
                if (waiting != null) {
                    return waiting;
                }
                int port = HttpUtil.getAvailablePort();
                server.setUsesAuthentication(false);
                if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, port)) {
                    return StepResult.fail("LAN-Freigabe auf Port " + port + " fehlgeschlagen");
                }
                String written = write(READY, Integer.toString(port));
                if (written != null) {
                    return StepResult.fail(written);
                }
                ctx.log("LAN-Welt auf Port " + port + " freigegeben, Drohne " + DRONE.get() + " am Boden abgestellt");
                next(state, 2, ctx.tick());
            }
            case 2 -> {
                if (server.getPlayerCount() < 2) {
                    if (since > 0 && since % 1200 == 0) {
                        ctx.log("warte auf den zweiten Client (" + since / 20 + " s)");
                    }
                    return since > OBSERVER_WAIT_TICKS ? StepResult.fail("kein zweiter Client kam in die LAN-Welt") : StepResult.pending();
                }
                ctx.log("zweiter Client ist da, bekommt eine Kreativ-Brille");
                state.put(FUTURE, ctx.onServer(FlightPathMarkerLanCheck::equipObserver));
                next(state, 3, ctx.tick());
            }
            case 3 -> {
                StepResult waiting = await(ctx, "Beobachter");
                if (waiting != null) {
                    return waiting;
                }
                if (since < TAKEOFF_DELAY) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(FlightPathMarkerLanCheck::startPilot));
                next(state, 4, ctx.tick());
            }
            case 4 -> {
                StepResult waiting = await(ctx, "Start");
                if (waiting != null) {
                    return waiting;
                }
                if (!flying(session)) {
                    return since > 300 ? StepResult.fail("keine FPV-Sitzung mit der Drohne") : StepResult.pending();
                }
                fly(session, 0);
                next(state, 5, ctx.tick());
            }
            default -> {
                if (server.getPlayerCount() < 2) {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    ctx.onServer(FlightPathMarkerLanCheck::finish);
                    if (!state.containsKey(FLOWN)) {
                        return StepResult.fail("zweiter Client ging, bevor die Drohne flog");
                    }
                    ctx.log("zweiter Client ist gegangen; Flug beendet nach " + since / 20 + " s");
                    return StepResult.pass();
                }
                if (!flying(session)) {
                    return StepResult.fail("Flugsitzung endete während des Flugs");
                }
                fly(session, since);
                QuadcopterEntity drone = session.drone();
                double height = drone == null ? 0.0 : drone.getY() - groundY;
                if (drone != null && height > CRUISE_FROM && drone.isArmed()) {
                    state.put(FLOWN, true);
                }
                DroneSim sim = session.sim();
                if (since % LOG_EVERY == 0 && drone != null && sim != null) {
                    ctx.log(String.format(Locale.ROOT, "[LAN-Marker] Flug %d s: Höhe %.1f, Tempo %.1f m/s, %.1f m vom Start, scharf %b", since / 20, height,
                            sim.telemetry().speed, Math.sqrt(drone.distanceToSqr(start.x, drone.getY(), start.z)), drone.isArmed()));
                }
                if (since > FLIGHT_TICKS) {
                    return StepResult.fail("zweiter Client blieb länger als " + FLIGHT_TICKS / 1200 + " min");
                }
            }
        }
        return StepResult.pending();
    }

    private static boolean flying(PilotSession session) {
        QuadcopterEntity drone = session.drone();
        return session.isActive() && session.sim() != null && drone != null && drone.getId() == DRONE.get();
    }

    private static void fly(PilotSession session, int since) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (sim == null || drone == null) {
            return;
        }
        if (since < ARM_TICKS) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
            return;
        }
        Telemetry telemetry = sim.telemetry();
        double height = drone.getY() - groundY;
        double hover = Math.clamp(telemetry.hoverThrottleStick, 0.15, 0.6);
        float throttle = (float) Math.clamp(hover + 0.04 * (CRUISE_HEIGHT - height) - 0.06 * telemetry.verticalSpeed + 0.02, 0.0, 1.0);
        if (cruiseSince < 0 && height > CRUISE_FROM) {
            cruiseSince = since;
        }
        boolean cruise = cruiseSince >= 0;
        session.setTestInput(new StickPositions(0.0F, cruise ? CRUISE_PITCH : 0.0F, cruise ? CRUISE_YAW : 0.0F, throttle), true, StabMode.ANGLE);
    }

    private static void prepare(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        groundY = feet.y;
        start = new Vec3(feet.x + 1.5, feet.y + 0.5, feet.z + 3.0);
        drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getId());
    }

    private static void equipObserver(MinecraftServer server) {
        ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != host) {
                player.teleportTo(host.level(), start.x + OBSERVER_OFFSET.x, groundY, start.z + OBSERVER_OFFSET.z, Set.of(), 0.0F, 0.0F, true);
                player.setItemSlot(EquipmentSlot.HEAD, FlightPathMarkerSelfTests.boundGoggles(null));
            }
        }
    }

    private static void startPilot(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne fehlt");
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        player.setItemSlot(EquipmentSlot.HEAD, FlightPathMarkerSelfTests.boundGoggles(drone));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void finish(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, int seconds) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return "spectate_fpm=missing (keine Welt)\n";
        }
        SpectateSession session = SpectateSession.INSTANCE;
        int begin = (int) state.computeIfAbsent(WATCH_START, key -> tick);
        if (!session.watching() || session.drone() == null) {
            if (state.containsKey(WATCH_FROM)) {
                return report(state, "spectate_fpm=lost (Zuschauen endete: " + session.phase() + ", " + session.lastResult() + ")");
            }
            if (tick - begin > FIND_TICKS) {
                return String.format(Locale.ROOT, "spectate_fpm=not_watching (Brille %s, Kanäle %d, Phase %s, Ergebnis %s)%n",
                        SpectateSession.goggles(mc), session.watchable(mc).size(), session.phase(), session.lastResult());
            }
            int requested = (int) state.getOrDefault(REQUESTED, Integer.MIN_VALUE / 2);
            if (!session.active() && tick - requested > REQUEST_RETRY_TICKS && SpectateSession.goggles(mc) != null) {
                List<ChannelListS2C.Entry> watchable = session.watchable(mc);
                if (!watchable.isEmpty() && session.request(mc, watchable.getFirst())) {
                    state.put(REQUESTED, tick);
                    Constants.LOG.info("[LAN-Marker] Beobachter fragt Kanal von {} an", watchable.getFirst().pilotName());
                }
            }
            return null;
        }
        if (!state.containsKey(WATCH_FROM)) {
            FlightPathMarkerSelfTests.showMarker();
            state.put(WATCH_FROM, tick);
            Constants.LOG.info("[LAN-Marker] Beobachter schaut per F7 zu, Flugbahn-Marker eingeschaltet");
        }
        int from = (int) state.get(WATCH_FROM);
        Counters counters = (Counters) state.computeIfAbsent(COUNTERS, key -> new Counters());
        OsdData data = OsdHud.sampler().data();
        double speed = data.hasVelocity() ? Math.sqrt(data.velocityX * data.velocityX + data.velocityY * data.velocityY
                + data.velocityZ * data.velocityZ) : 0.0;
        boolean drawn = FlightPathMarkerSelfTests.drawn() && data.spectating;
        FlightPathMarker.State shown = FlightPathMarkerSelfTests.shown();
        counters.watched++;
        counters.maxSpeed = Math.max(counters.maxSpeed, speed);
        if (drawn) {
            counters.drawn++;
            if (shown.inView) {
                counters.inView++;
            }
        }
        if (speed >= MOVING_SPEED) {
            counters.moving++;
            if (drawn) {
                counters.drawnMoving++;
            }
        }
        if ((tick - from) % LOG_EVERY == 0) {
            Constants.LOG.info(String.format(Locale.ROOT, "[LAN-Marker] Beobachter %d s: OSD-Tempo %.1f m/s, Marker %s%s", (tick - from) / 20, speed,
                    drawn ? "gezeichnet" : "nicht gezeichnet", drawn ? String.format(Locale.ROOT, " bei %.0f/%.0f (%s)", shown.x, shown.y,
                            shown.inView ? "im Bild" : "am Rand") : ""));
        }
        if (tick - from < seconds * 20 || counters.moving < MIN_MOVING_TICKS) {
            return tick - from > seconds * 20 + OBSERVER_EXTRA_TICKS ? report(state, "spectate_fpm=none") : null;
        }
        boolean photo = drawn && shown.inView && shown.alpha >= 1.0F;
        if (!photo && tick - from < seconds * 20 + PHOTO_WAIT_TICKS) {
            return null;
        }
        boolean ok = counters.drawnMoving * 10 >= counters.moving * 8 && counters.inView > 0;
        return report(state, ok ? "spectate_fpm=drawn" : "spectate_fpm=none");
    }

    private static String report(Map<String, Object> state, String verdict) {
        Counters c = (Counters) state.getOrDefault(COUNTERS, new Counters());
        return String.format(Locale.ROOT, "%s%nspectate_fpm_ticks watched=%d moving=%d drawn=%d drawn_moving=%d in_view=%d max_speed=%.1f%n", verdict,
                c.watched, c.moving, c.drawn, c.drawnMoving, c.inView, c.maxSpeed);
    }

    private static @Nullable String write(String name, String text) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(name), text, StandardCharsets.UTF_8);
            return null;
        } catch (IOException e) {
            return name + " nicht geschrieben: " + e.getMessage();
        }
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx, String what) {
        Object value = ctx.state().get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static void next(Map<String, Object> state, int phase, int tick) {
        state.put(PHASE, phase);
        state.put(MARK, tick);
    }
}
