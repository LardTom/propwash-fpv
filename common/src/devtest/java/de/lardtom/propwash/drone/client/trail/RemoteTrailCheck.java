package de.lardtom.propwash.drone.client.trail;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class RemoteTrailCheck {
    public static final String HOST = "PROPWASH_LANHOST";
    public static final String DIR = "lan-trails";
    public static final String READY = "ready";
    public static final String HOST_STEP = "lan-trails: Gastgeber fliegt per Hand, ein zweiter Client im LAN sieht den Trail";
    public static final int HOST_TICKS = 20 * 60 * 50;
    private static final int OBSERVER_WAIT_TICKS = 20 * 60 * 45;
    private static final int FLIGHT_TICKS = 20 * 60 * 4;
    private static final int TAKEOFF_DELAY = 40;
    private static final int ARM_TICKS = 20;
    private static final int OBSERVER_FIND_TICKS = 20 * 60 * 3;
    private static final int LOG_EVERY = 40;
    private static final int MIN_SAMPLES = 4;
    private static final double CRUISE_HEIGHT = 7.0;
    private static final double CRUISE_FROM = 5.0;
    private static final int SWING_TICKS = 50;
    private static final float SWING_PITCH = 0.3F;
    private static final double AIRBORNE_HEIGHT = 1.5;
    private static final int PAINT = 0xE0A020;
    private static final Vec3 OBSERVER_OFFSET = new Vec3(-10.0, 0.0, 0.0);
    private static final int HIDE_HUD_TICKS = 10;
    private static final String FUTURE = "lan_future";
    private static final String PHASE = "lan_phase";
    private static final String MARK = "lan_mark";
    private static final String FLOWN = "lan_flown";
    private static final String WATCH_START = "watch_start";
    private static final String WATCH_ID = "watch_id";
    private static final String WATCH_FROM = "watch_from";
    private static final String WATCH_COUNT = "watch_count";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static volatile double groundY;
    private static volatile Vec3 start = Vec3.ZERO;
    private static int cruiseSince = -1;
    private static volatile boolean serverOnGround;
    private static volatile boolean serverArmed;
    private static volatile double serverY;
    private static boolean installed;

    private static final class Counters {
        int airborne;
        int drawn;
        int groundFlag;
        int samples;
        double speed;
    }

    private RemoteTrailCheck() {
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
                state.put(FUTURE, ctx.onServer(RemoteTrailCheck::prepare));
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
                ctx.log("zweiter Client ist da; Drohne laut Server am Boden: " + serverOnGround);
                ctx.onServer(RemoteTrailCheck::placeObserver);
                next(state, 3, ctx.tick());
            }
            case 3 -> {
                if (since < TAKEOFF_DELAY) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(RemoteTrailCheck::startPilot));
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
                    ctx.onServer(RemoteTrailCheck::finish);
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
                if (height > AIRBORNE_HEIGHT && drone.isArmed()) {
                    state.put(FLOWN, true);
                }
                DroneSim sim = session.sim();
                if (since % LOG_EVERY == 0 && drone != null && sim != null) {
                    ctx.log(String.format(Locale.ROOT, "Flug %d s: Höhe %.1f, Tempo %.1f m/s, %.1f m vom Start, scharf %b; Server: am Boden %b, scharf %b, Höhe %.1f",
                            since / 20, height, sim.telemetry().speed, Math.sqrt(drone.distanceToSqr(start.x, drone.getY(), start.z)), drone.isArmed(),
                            serverOnGround, serverArmed, serverY - groundY));
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
        double hover = Mth.clamp(telemetry.hoverThrottleStick, 0.15, 0.6);
        float throttle = (float) Mth.clamp(hover + 0.04 * (CRUISE_HEIGHT - height) - 0.06 * telemetry.verticalSpeed, 0.0, 1.0);
        if (cruiseSince < 0 && height > CRUISE_FROM) {
            cruiseSince = since;
        }
        float pitch = 0.0F;
        if (cruiseSince >= 0) {
            int swing = Math.floorDiv(since - cruiseSince + SWING_TICKS, 2 * SWING_TICKS);
            pitch = swing % 2 == 0 ? SWING_PITCH : -SWING_PITCH;
        }
        session.setTestInput(new StickPositions(0.0F, pitch, 0.0F, throttle), true, StabMode.ANGLE);
    }

    private static synchronized void install() {
        if (!installed) {
            installed = true;
            ServerBus.TICK_END.register(RemoteTrailCheck::onServerTick);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        int id = DRONE.get();
        if (id >= 0 && server.overworld().getEntity(id) instanceof QuadcopterEntity drone) {
            serverOnGround = drone.onGround();
            serverArmed = drone.isArmed();
            serverY = drone.getY();
        }
    }

    private static void prepare(MinecraftServer server) {
        install();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, 1);
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
        drone.setPaint(DronePaint.of(Map.of(PaintSlot.FRAME, PAINT)));
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getId());
    }

    private static void startPilot(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne fehlt");
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void placeObserver(MinecraftServer server) {
        ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != host) {
                player.teleportTo(host.level(), start.x + OBSERVER_OFFSET.x, groundY, start.z + OBSERVER_OFFSET.z, Set.of(), 0.0F, 0.0F, true);
            }
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
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return "remote drone missing (keine Welt)\n";
        }
        int start = (int) state.computeIfAbsent(WATCH_START, key -> tick);
        QuadcopterEntity drone = state.get(WATCH_ID) instanceof Integer id && level.getEntity(id) instanceof QuadcopterEntity found ? found : null;
        if (drone == null) {
            drone = remoteDrone(level, player.getUUID());
            if (drone == null) {
                return tick - start > OBSERVER_FIND_TICKS ? "remote drone missing\n" : null;
            }
            state.put(WATCH_ID, drone.getId());
            Constants.LOG.info("[LAN-Trails] Beobachter: fremd gesteuerte Drohne {} gefunden", drone.getId());
        }
        int id = drone.getId();
        look(player, drone);
        double height = drone.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(drone.getX()), Mth.floor(drone.getZ()));
        boolean airborne = drone.isArmed() && height > AIRBORNE_HEIGHT;
        if (!state.containsKey(WATCH_FROM)) {
            if (!airborne) {
                return tick - start > OBSERVER_FIND_TICKS
                        ? String.format(Locale.ROOT, "remote drone grounded (scharf %b, Höhe %.1f)%n", drone.isArmed(), height) : null;
            }
            state.put(WATCH_FROM, tick);
        }
        int from = (int) state.get(WATCH_FROM);
        Counters counters = (Counters) state.computeIfAbsent(WATCH_COUNT, key -> new Counters());
        double speed = drone.position().subtract(drone.xOld, drone.yOld, drone.zOld).length() * 20.0;
        if (airborne) {
            counters.airborne++;
            if (drone.onGround()) {
                counters.groundFlag++;
            }
            if (TrailRenderer.drawn(id)) {
                counters.drawn++;
            }
            counters.speed = Math.max(counters.speed, speed);
        }
        counters.samples = Math.max(counters.samples, TrailRenderer.samples(id));
        if ((tick - from) % LOG_EVERY == 0) {
            Constants.LOG.info(String.format(Locale.ROOT, "[LAN-Trails] Beobachter %d s: Höhe %.1f, Tempo %.1f m/s, scharf %b, Boden-Flag %b, Trail %s",
                    (tick - from) / 20, height, speed, drone.isArmed(), drone.onGround(), TrailRenderer.describe(id)));
        }
        if (tick - from >= seconds * 20 - HIDE_HUD_TICKS && !mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        if (tick - from < seconds * 20) {
            return null;
        }
        boolean drawn = counters.airborne > 0 && counters.drawn * 2 >= counters.airborne && counters.samples >= MIN_SAMPLES;
        return String.format(Locale.ROOT, "remote drone found%nremote trail %s (Proben bis %d, gezeichnet in %d von %d Ticks in der Luft)%n"
                        + "remote ground flag %s (%d von %d Ticks in der Luft)%nremote speed max %.1f m/s%n",
                drawn ? "drawn" : "none", counters.samples, counters.drawn, counters.airborne,
                counters.groundFlag == 0 ? "clear" : "stale", counters.groundFlag, counters.airborne, counters.speed);
    }

    private static @Nullable QuadcopterEntity remoteDrone(ClientLevel level, UUID self) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone) {
                Optional<UUID> pilot = drone.pilot();
                if (pilot.isPresent() && !pilot.get().equals(self)) {
                    return drone;
                }
            }
        }
        return null;
    }

    private static void look(LocalPlayer player, QuadcopterEntity drone) {
        Vec3 eye = player.getEyePosition();
        double dx = drone.getX() - eye.x;
        double dy = drone.getY() + drone.getBbHeight() * 0.5 - eye.y;
        double dz = drone.getZ() - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        player.xRotO = pitch;
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
