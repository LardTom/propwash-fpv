package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CovJmpFlightSelfTests {
    public static final String STEP = "drone: JMP-Drohne fliegt – Preset justmoreparts:xclass13_8s mit Gas-Rampe, Steigen und Landen";
    public static final Identifier PRESET = Identifier.fromNamespaceAndPath("justmoreparts", "xclass13_8s");
    private static final int ARM_TICKS = 20;
    private static final int RAMP_TICKS = 40;
    private static final int CLIMB_TICKS = 60;
    private static final int LAND_TICKS = 300;
    private static final double CLIMB_TO = 4.0;
    private static final double MIN_CLIMB = 2.0;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<@Nullable ItemStack> HAND = new AtomicReference<>();

    private CovJmpFlightSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 1200, CovJmpFlightSelfTests::flightStep);
    }

    static Optional<DroneBuild> preset() {
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            if (preset.id().equals(PRESET)) {
                return Conversions.build(preset.data().build());
            }
        }
        return Optional.empty();
    }

    private static void spawn(MinecraftServer server, DroneBuild build) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x + 5.0, feet.y + 0.1, feet.z + 5.0, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(build);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getId());
        HAND.compareAndSet(null, player.getMainHandItem().copy());
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Sichtflug verweigert: " + result);
        }
    }

    private static void release(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack hand = HAND.getAndSet(null);
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        int id = DRONE.getAndSet(-1);
        for (ServerLevel level : server.getAllLevels()) {
            if (id >= 0 && level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        if (ctx.tick() == 0) {
            Optional<DroneBuild> build = preset();
            if (build.isEmpty()) {
                ctx.log("übersprungen: Preset " + PRESET + " nicht geladen (Just More Parts " + Services.PLATFORM.modVersion("justmoreparts").orElse("fehlt") + ")");
                state.put("skip", true);
                return StepResult.pass();
            }
            state.put("build", build.get());
            state.put("future", ctx.onServer(server -> spawn(server, build.get())));
            state.put("phase", "start");
            state.put("mark", ctx.tick());
            return StepResult.pending();
        }
        String phase = (String) state.get("phase");
        int since = ctx.tick() - (int) state.get("mark");
        CompletableFuture<?> future = (CompletableFuture<?>) state.get("future");
        if (future != null && future.isCompletedExceptionally()) {
            return finish(ctx, "Server: " + future.exceptionNow());
        }
        if (phase.equals("cleanup")) {
            if (future != null && !future.isDone() || session.isActive()) {
                return since > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
            }
            String problem = (String) state.get("problem");
            if (problem != null) {
                return StepResult.fail(problem);
            }
            ctx.log((String) state.get("log"));
            return StepResult.pass();
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (future == null || !future.isDone() || drone == null || sim == null || drone.getId() != DRONE.get() || !session.isLineOfSight()) {
            return since > 300 ? finish(ctx, "keine Sichtflug-Sitzung mit der JMP-Drohne") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        try {
            switch (phase) {
                case "start" -> {
                    state.put("ground", drone.getY());
                    state.put("max", 0.0);
                    state.put("phase", "arm");
                    state.put("mark", ctx.tick());
                    return StepResult.pending();
                }
                case "arm" -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
                    if (since >= ARM_TICKS) {
                        if (!t.armed) {
                            return finish(ctx, "JMP-Drohne lässt sich nicht scharf schalten: " + t.armBlockers);
                        }
                        state.put("phase", "fly");
                        state.put("mark", ctx.tick());
                    }
                    return StepResult.pending();
                }
                case "fly" -> {
                    double hover = Math.clamp(t.hoverThrottleStick, 0.05, 0.9);
                    double height = drone.getY() - (double) state.get("ground");
                    state.put("max", Math.max((double) state.get("max"), height));
                    boolean climbing = !state.containsKey("descend_at");
                    if (climbing && (height >= CLIMB_TO || since >= RAMP_TICKS + CLIMB_TICKS)) {
                        state.put("descend_at", since);
                        climbing = false;
                    }
                    double throttle;
                    if (climbing) {
                        throttle = (hover + 0.12) * Math.min(1.0, (double) since / RAMP_TICKS);
                    } else {
                        double wanted = height > 1.0 ? -1.2 : -0.5;
                        throttle = down(t, height) ? 0.0 : Math.clamp(hover + 0.08 * (wanted - t.verticalSpeed), 0.0, 1.0);
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) throttle), true, StabMode.ANGLE);
                    boolean landed = !climbing && down(t, height) && since - (int) state.get("descend_at") > 10;
                    if (!landed && since < RAMP_TICKS + CLIMB_TICKS + LAND_TICKS) {
                        return StepResult.pending();
                    }
                    double max = (double) state.get("max");
                    String log = String.format(Locale.ROOT, "Just More Parts %s, Preset %s (%s, %.0f g): in %.1f s mit Gas-Rampe auf %.1f m gestiegen und gelandet "
                                    + "(am Ende %.2f m, am Boden %s), Abstürze %d", Services.PLATFORM.modVersion("justmoreparts").orElse("?"), PRESET,
                            ((DroneBuild) state.get("build")).frame().id(), sim.params().massKg * 1000.0, since / 20.0 + ARM_TICKS / 20.0, max, height, down(t, height),
                            session.crashCount());
                    state.put("log", log);
                    if (max < MIN_CLIMB) {
                        return finish(ctx, "JMP-Drohne steigt nicht: " + log);
                    }
                    if (!down(t, height)) {
                        return finish(ctx, "JMP-Drohne landet nicht: " + log);
                    }
                    return finish(ctx, null);
                }
                default -> {
                    return finish(ctx, "unbekannte Phase " + phase);
                }
            }
        } catch (RuntimeException e) {
            return finish(ctx, "Ausnahme im Flug: " + e);
        }
    }

    private static boolean down(Telemetry t, double height) {
        return t.grounded || t.sleeping || height < 0.15 && Math.abs(t.verticalSpeed) < 0.1;
    }

    private static StepResult finish(SelfTest.StepContext ctx, @Nullable String problem) {
        Map<String, Object> state = ctx.state();
        state.put("problem", problem);
        PilotSession.INSTANCE.clearTestInput();
        state.put("future", ctx.onServer(CovJmpFlightSelfTests::release));
        state.put("phase", "cleanup");
        state.put("mark", ctx.tick());
        return StepResult.pending();
    }
}
