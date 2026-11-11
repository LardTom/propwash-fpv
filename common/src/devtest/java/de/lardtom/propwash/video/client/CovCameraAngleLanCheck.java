package de.lardtom.propwash.video.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
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

public final class CovCameraAngleLanCheck {
    public static final String HOST = "PROPWASH_LANHOST_COV_CAMANGLE";
    public static final String DIR = "lan-cov-camangle";
    public static final String READY = "ready";
    public static final String HOST_STEP = "lan-camangle: Gastgeber ändert im FPV den Kamerawinkel von 40° auf 20°, ein zweiter Client schaut per F7 zu";
    public static final int HOST_TICKS = 20 * 60 * 50;
    public static final int OBSERVER_TICKS = 20 * 60 * 6;
    public static final float BEFORE = 40.0F;
    public static final float AFTER = 20.0F;
    private static final int OBSERVER_WAIT_TICKS = 20 * 60 * 45;
    private static final int CHANGE_AFTER = 20 * 15;
    private static final int END_AFTER = 20 * 30;
    private static final int FIND_TICKS = 20 * 60 * 4;
    private static final int REQUEST_RETRY_TICKS = 100;
    private static final double HEIGHT = 4.0;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static volatile double groundY;

    private CovCameraAngleLanCheck() {
    }

    public static boolean hostEnabled() {
        String value = System.getenv(HOST);
        return value != null && !value.isBlank() && !value.trim().equals("0");
    }

    private static @Nullable String write(String name, String text) {
        try {
            Path dir = Services.PLATFORM.getGameDir().resolve(DIR);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
            return null;
        } catch (IOException e) {
            return "Datei " + name + " nicht geschrieben: " + e;
        }
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
        drone.snapTo(feet.x + 1.5, feet.y + 0.5, feet.z + 3.0, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setTune(new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, BEFORE)));
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getId());
    }

    private static ItemStack goggles(@Nullable QuadcopterEntity drone) {
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (drone != null) {
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        }
        return goggles;
    }

    private static void equip(MinecraftServer server) {
        ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != host) {
                player.teleportTo(host.level(), host.getX() - 8.0, groundY, host.getZ(), Set.of(), 0.0F, 0.0F, true);
                player.setItemSlot(EquipmentSlot.HEAD, goggles(null));
            }
        }
        if (!(host.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne fehlt");
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        host.setItemSlot(EquipmentSlot.MAINHAND, remote);
        host.setItemSlot(EquipmentSlot.HEAD, goggles(drone));
        DronePiloting.StartResult result = DronePiloting.tryStart(host, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static float serverAngle(MinecraftServer server) {
        ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
        return host.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone ? de.lardtom.propwash.drone.CameraMount.uptilt(drone.build(),
                drone.tune().values()) : Float.NaN;
    }

    private static void hover(PilotSession session, int since) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (sim == null || drone == null) {
            return;
        }
        Telemetry t = sim.telemetry();
        double hover = Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        float throttle = (float) Math.clamp(hover + 0.06 * (Math.clamp(1.2 * (groundY + HEIGHT - drone.getY()), -3.0, 3.0) - t.verticalSpeed), 0.0, 1.0);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, since < 10 ? 0.0F : throttle), since >= 5, StabMode.ANGLE);
    }

    public static StepResult hostStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return StepResult.fail("keine Einzelspielerwelt");
        }
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault("phase", 0);
        int since = ctx.tick() - (int) state.getOrDefault("mark", 0);
        PilotSession session = PilotSession.INSTANCE;
        CompletableFuture<?> future = (CompletableFuture<?>) state.get("future");
        if (future != null) {
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Server: " + future.exceptionNow().getMessage());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            state.remove("future");
        }
        switch (phase) {
            case 0 -> {
                state.put("future", ctx.onServer(CovCameraAngleLanCheck::prepare));
                return next(state, 1, ctx.tick());
            }
            case 1 -> {
                int port = HttpUtil.getAvailablePort();
                server.setUsesAuthentication(false);
                if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, port)) {
                    return StepResult.fail("LAN-Freigabe fehlgeschlagen");
                }
                String written = write(READY, Integer.toString(port));
                if (written != null) {
                    return StepResult.fail(written);
                }
                ctx.log("LAN-Welt auf Port " + port + " freigegeben, Drohne mit " + BEFORE + "° abgestellt");
                return next(state, 2, ctx.tick());
            }
            case 2 -> {
                if (server.getPlayerCount() < 2) {
                    return since > OBSERVER_WAIT_TICKS ? StepResult.fail("kein zweiter Client kam in die LAN-Welt") : StepResult.pending();
                }
                state.put("future", ctx.onServer(CovCameraAngleLanCheck::equip));
                return next(state, 3, ctx.tick());
            }
            case 3 -> {
                if (!session.isFpv() || session.drone() == null || session.drone().getId() != DRONE.get()) {
                    return since > 300 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                hover(session, since);
                if (since >= CHANGE_AFTER) {
                    ctx.log(String.format(Locale.ROOT, "[LAN-Kamerawinkel] Gastgeber stellt %.0f° → %.0f° ein (FPV-Kamera vorher %.0f°)", BEFORE, AFTER,
                            session.cameraUptiltDeg()));
                    TuneAccess.INSTANCE.apply(new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, AFTER)));
                    return next(state, 4, ctx.tick());
                }
                return StepResult.pending();
            }
            case 4 -> {
                hover(session, CHANGE_AFTER + since);
                if (server.getPlayerCount() < 2 || since > END_AFTER) {
                    float angle = serverAngle(server);
                    ctx.log(String.format(Locale.ROOT, "[LAN-Kamerawinkel] Gastgeber: FPV-Kamera %.0f°, Drohne am Server %.0f°, zweiter Client %s",
                            session.cameraUptiltDeg(), angle, server.getPlayerCount() < 2 ? "gegangen" : "noch da"));
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    if (session.cameraUptiltDeg() != AFTER || angle != AFTER) {
                        return StepResult.fail("neuer Winkel kommt beim Gastgeber nicht an");
                    }
                    if (server.getPlayerCount() >= 2) {
                        return since > END_AFTER + 20 * 120 ? StepResult.fail("zweiter Client bleibt zu lange") : StepResult.pending();
                    }
                    return StepResult.pass();
                }
                return StepResult.pending();
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult next(Map<String, Object> state, int phase, int tick) {
        state.put("phase", phase);
        state.put("mark", tick);
        return StepResult.pending();
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return "camangle_spectate=missing\n";
        }
        SpectateSession session = SpectateSession.INSTANCE;
        int begin = (int) state.computeIfAbsent("cov_camangle_start", key -> tick);
        QuadcopterEntity drone = session.drone();
        if (!session.watching() || drone == null) {
            if (state.containsKey("cov_camangle_before")) {
                return report(state, "lost: " + session.phase());
            }
            if (tick - begin > FIND_TICKS) {
                return "camangle_spectate=not_watching (" + session.phase() + ", " + session.lastResult() + ")\n";
            }
            int requested = (int) state.getOrDefault("cov_camangle_requested", Integer.MIN_VALUE / 2);
            if (!session.active() && tick - requested > REQUEST_RETRY_TICKS && SpectateSession.goggles(mc) != null) {
                List<ChannelListS2C.Entry> watchable = session.watchable(mc);
                if (!watchable.isEmpty() && session.request(mc, watchable.getFirst())) {
                    state.put("cov_camangle_requested", tick);
                    Constants.LOG.info("[LAN-Kamerawinkel] Beobachter fragt Kanal von {} an", watchable.getFirst().pilotName());
                }
            }
            return null;
        }
        float angle = session.cameraUptiltDeg(drone);
        float pitch = mc.gameRenderer.mainCamera().xRot();
        if (!state.containsKey("cov_camangle_before")) {
            state.put("cov_camangle_before", angle);
            state.put("cov_camangle_pitch_before", pitch);
            Constants.LOG.info("[LAN-Kamerawinkel] Beobachter schaut zu: Kamerawinkel {}°, Kamera-Nick {}°", angle, pitch);
        }
        if (angle != (float) state.get("cov_camangle_before") && !state.containsKey("cov_camangle_after")) {
            state.put("cov_camangle_after", angle);
            state.put("cov_camangle_pitch_after", pitch);
            state.put("cov_camangle_after_at", tick);
            Constants.LOG.info("[LAN-Kamerawinkel] Beobachter sieht neuen Kamerawinkel {}°, Kamera-Nick {}°", angle, pitch);
        }
        if (state.containsKey("cov_camangle_after") && tick - (int) state.get("cov_camangle_after_at") > 40) {
            state.put("cov_camangle_pitch_after", pitch);
            return report(state, "done");
        }
        return tick - begin > OBSERVER_TICKS ? report(state, "timeout") : null;
    }

    private static String report(Map<String, Object> state, String end) {
        Float before = (Float) state.get("cov_camangle_before");
        Float after = (Float) state.get("cov_camangle_after");
        boolean follows = before != null && after != null && before == BEFORE && after == AFTER;
        return "camangle_spectate=" + (follows ? "follows" : "stale") + String.format(Locale.ROOT, "%ncamangle_detail before %s after %s, camera pitch %s -> %s, %s%n",
                before, after, state.get("cov_camangle_pitch_before"), state.get("cov_camangle_pitch_after"), end);
    }
}
