package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleFollow;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.AutonomyBudget;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class AutonomySelfTests {
    public static final String GROUP = "addon-api-17: B7";
    private static final int BACK = 32;
    private static final int FRONT = 40;
    private static final int SIDE = 12;
    private static final int HEIGHT = 20;
    private static final double A_X = 20.5;
    private static final double B_X = 26.5;
    private static final double B_Z = 6.5;
    private static final double VIEW_DX = -4.0;
    private static final double HOVER = 4.0;
    private static final double HOLD_LIMIT = 0.5;
    private static final double FOLLOW_LIMIT = 5.0;
    private static final double ZONE_FOLLOW_LIMIT = 7.0;
    private static final double HEIGHT_LOSS = 0.3;
    private static final double WALK_STEP = 4.3 / 20.0;
    private static final double WALK = 20.0;
    private static final double ZONE_WALK = 24.0;
    private static final int WIND_FROM = -12;
    private static final int WIND_TO = -6;
    private static final int MOON_FROM = -22;
    private static final int MOON_TO = -16;
    private static final int LOW_GRAVITY = 30;
    private static final int STRONG_WIND = 3;
    private static final float LOW_CHARGE = 0.16F;
    private static final String WIND_ZONE = "B7 Wind";
    private static final String MOON_ZONE = "B7 Schwerkraft";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final AtomicReference<int[]> ARENA = new AtomicReference<>();
    private static final AtomicReference<UUID> DRONE_A = new AtomicReference<>();
    private static final AtomicReference<UUID> DRONE_B = new AtomicReference<>();
    private static final AtomicReference<UUID> DRONE_PAD = new AtomicReference<>();
    private static final double PAD_OFFSET = 0.15;
    private static final double PAD_LIMIT = 0.3;
    private static final double PAD_HEIGHT = 5.0;
    private static final int PAD_Z = -6;
    private static final int[] PAD_X = {32, 35, 38};
    private static final String[] PAD_NAMES = {"Teppich im Boden eingelassen", "Teppich auf dem Boden", "Stufe auf dem Boden"};
    private static final double HOME_PAD_OFFSET = 0.22;
    private static final double HOME_PAD_RUN = 4.0;
    private static final int HOME_PAD_SECONDS = 8;
    private static final AtomicReference<double[]> PLAYER_HOME = new AtomicReference<>();
    private static final AtomicReference<Snap> SNAP = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new HashMap<>();
    private static final double TAKEOVER_HEIGHT = 4.0;
    private static final double TAKEOVER_LOSS = 0.5;
    private static final int TAKEOVER_TICKS = 60;
    private static final int TAKEOVER_THROTTLE_TICK = 10;
    private static final double THROTTLE_TOLERANCE = 0.03;
    private static final String TAKEOVER_ASSIST = ExampleAddon.NAMESPACE + ":takeover_altitude";
    private static final AtomicBoolean ASSIST_ON = new AtomicBoolean();
    private static final AtomicReference<boolean[]> SAVED_INPUT = new AtomicReference<>();
    private static boolean assistRegistered;
    private static final float TILT_LIMIT = 30.0F;
    private static final double TILT_TOLERANCE = 4.0;
    private static final Identifier TILT = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "tilt_probe");
    private static final AtomicReference<TiltProbe> PROBE = new AtomicReference<>();
    private static final String NO_BUDGET_PLAYER = "propwash.drone.autonomy.no_budget.player";
    private static final String NO_BUDGET_SERVER = "propwash.drone.autonomy.no_budget.server";
    private static final String NO_BUDGET_OFF = "propwash.drone.autonomy.no_budget.off";

    private record Snap(UUID id, long time, boolean present, double x, double y, double z, boolean armed, boolean piloted, String active, String phase,
                        boolean planned, boolean homeFlying, double gravity, double windScale, double px, double py, double pz) {
        double distance(Vec3 point) {
            return Math.sqrt(sq(this.x - point.x) + sq(this.y - point.y) + sq(this.z - point.z));
        }

        double toPlayer() {
            return Math.hypot(this.x - this.px, this.z - this.pz);
        }
    }

    private AutonomySelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – Testfeld, zwei Drohnen, Funke und zwei Zonen aufbauen", 300, AutonomySelfTests::setupStep);
        SelfTest.clientStep(GROUP + " – Pilot verlässt das FPV: Beispiel startet Halten, 10 s innerhalb 0,5 m", 1200, AutonomySelfTests::holdStep);
        SelfTest.clientStep(GROUP + " – Spieler läuft 20 Blöcke: Folgen hält höchstens 5 m Abstand", 900,
                ctx -> walkStep(ctx, WALK, true, false));
        SelfTest.clientStep(GROUP + " – Budget voll: zweiter Add-on-Flug desselben Spielers ergibt NO_BUDGET", 200, AutonomySelfTests::budgetStep);
        SelfTest.clientStep(GROUP + " – Welt-Regeln „Autonome Flüge je Spieler gleichzeitig“ und „gesamt“: 2 lässt den zweiten Flug zu, 1 lehnt ihn ab, "
                + "0 und gesamt 1 ebenso, die Meldung nennt den Wert", 900, AutonomySelfTests::budgetRuleStep);
        SelfTest.clientStep(GROUP + " – Folgen durch eine Zone mit Wind und eine mit geringer Schwerkraft: Zonen wirken wie im FPV-Flug", 900,
                ctx -> walkStep(ctx, ZONE_WALK, false, true));
        SelfTest.clientStep(GROUP + " – land() nach dem Folgen: am Boden entschärft, AUTONOMY_ENDED mit LANDED", 900, ctx -> landStep(ctx, true));
        SelfTest.clientStep(GROUP + " – Übergabe: Pilot verlässt das FPV mit Folgen, Flug läuft ohne Höhenverlust weiter", 1200,
                AutonomySelfTests::handoverStep);
        SelfTest.clientStep(GROUP + " – stop(): AUTONOMY_ENDED mit STOPPED, Propwash landet an Ort und Stelle", 900, AutonomySelfTests::stopStep);
        SelfTest.clientStep(GROUP + " – Start vom Boden auf 5 m, danach land(): am Boden entschärft, AUTONOMY_ENDED mit LANDED", 1200,
                AutonomySelfTests::takeoffStep);
        SelfTest.clientStep(GROUP + " – land() über Teilhöhen-Blöcken (Teppich im Boden, Teppich, Stufe): setzt höchstens 0,3 m neben dem Punkt auf",
                3600, AutonomySelfTests::partialBlockStep);
        SelfTest.clientStep(GROUP + " – Heimflug nach TIMEOUT zum Homepoint auf Teilhöhen-Blöcken (Teppich im Boden, Teppich, Stufe): "
                + "setzt höchstens 0,3 m neben dem Homepoint auf", 4800, AutonomySelfTests::homePadStep);
        SelfTest.clientStep(GROUP + " – Pilot übernimmt: AUTONOMY_ENDED mit PILOT", 900, AutonomySelfTests::pilotStep);
        SelfTest.clientStep(GROUP + " – Pilot übernimmt in 4 m mit Tastatur: scharf auf Schwebedrehzahl, Gas auf Schwebegas, höchstens 0,5 m Höhenverlust",
                1500, ctx -> takeoverStep(ctx, false));
        SelfTest.clientStep(GROUP + " – Pilot übernimmt in 4 m mit Flughilfe (Gas-Mitte = Schweben): scharf, Gas in der Mitte, höchstens 0,5 m Höhenverlust",
                1500, ctx -> takeoverStep(ctx, true));
        SelfTest.clientStep(GROUP + " – Fast leerer Akku: Ende mit BATTERY, danach Heimflug zum Homepoint", 2400, AutonomySelfTests::batteryStep);
        SelfTest.clientStep(GROUP + " – Drohne ohne Akku: NOT_ARMABLE, ohne takeoff am Boden NOT_READY", 200, AutonomySelfTests::armStep);
        SelfTest.clientStep(GROUP + " – Neigungsgrenze 30° aus dem PID-Tab: DroneView am Server und am Client, FlightState im Add-on-Flug "
                + "und voller Knüppel neigen 30° statt 60°", 1500, AutonomySelfTests::angleLimitStep);
        SelfTest.clientStep(GROUP + " – Testfeld abbauen", 200, AutonomySelfTests::cleanupStep);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server, AtomicReference<UUID> which) {
        UUID id = which.get();
        if (id == null) {
            return null;
        }
        return serverPlayer(server).level().getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static QuadcopterEntity require(MinecraftServer server, AtomicReference<UUID> which) {
        QuadcopterEntity drone = drone(server, which);
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt");
        }
        return drone;
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, String what, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(action));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(what + ": " + result);
    }

    private static Vec3 at(int[] arena, double dx, double dz) {
        return new Vec3(arena[0] + dx, arena[1], arena[2] + dz);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state)) {
            return;
        }
        ORIGINAL.putIfAbsent(pos.immutable(), old);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void poll(SelfTest.StepContext ctx, AtomicReference<UUID> which) {
        ctx.onServer(server -> {
            QuadcopterEntity drone = drone(server, which);
            ServerPlayer player = serverPlayer(server);
            long time = server.overworld().getGameTime();
            if (drone == null) {
                SNAP.set(new Snap(which.get(), time, false, 0, 0, 0, false, false, "", "", false, false, Double.NaN, Double.NaN, player.getX(), player.getY(),
                        player.getZ()));
                return;
            }
            DroneSim sim = AutonomousReturns.sim(drone);
            ReturnPlan plan = drone.returnPlan();
            SNAP.set(new Snap(drone.getUUID(), time, true, drone.getX(), drone.getY(), drone.getZ(), drone.isArmed(), drone.pilot().isPresent(),
                    AddonAutonomy.active(drone).map(Object::toString).orElse(""), AddonAutonomy.phase(drone).orElse(""), plan != null,
                    plan != null && AutonomousReturns.flying(drone), sim == null ? Double.NaN : sim.gravity(),
                    sim == null ? Double.NaN : sim.tuning().windScale, player.getX(), player.getY(), player.getZ()));
        });
    }

    private static @Nullable Snap snap(AtomicReference<UUID> which) {
        Snap snap = SNAP.get();
        return snap != null && snap.id() != null && snap.id().equals(which.get()) ? snap : null;
    }

    private static String describe(@Nullable Snap snap) {
        if (snap == null) {
            return "kein Serverstand";
        }
        if (!snap.present()) {
            return "Drohne nicht auf dem Server";
        }
        return String.format(Locale.ROOT, "Pos (%.2f, %.2f, %.2f), scharf %s, Pilot %s, Flug '%s' Phase %s, Heimflug %s/%s, g %.2f, Wind %.1f, Spieler %.2f m",
                snap.x(), snap.y(), snap.z(), snap.armed(), snap.piloted(), snap.active(), snap.phase(), snap.planned(), snap.homeFlying(),
                snap.gravity(), snap.windScale(), snap.toPlayer());
    }

    private static String ended(UUID drone) {
        return ExampleFollow.lastEnded(drone).map(e -> e.id().getPath() + "/" + e.reason()).orElse("-");
    }

    private static boolean endedWith(@Nullable UUID drone, AutonomyEnd reason, String path) {
        if (drone == null) {
            return false;
        }
        Optional<ExampleFollow.Ended> last = ExampleFollow.lastEnded(drone);
        return last.isPresent() && last.get().reason() == reason && last.get().id().getPath().equals(path);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0 && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        StepResult wait = server(ctx, "Testfeld", server -> {
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            PLAYER_HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
            int[] arena = {player.getBlockX() + 12, player.getBlockY(), player.getBlockZ()};
            ARENA.set(arena);
            for (int dx = -BACK; dx <= FRONT; dx++) {
                for (int dz = -SIDE; dz <= SIDE; dz++) {
                    level.getChunk((arena[0] + dx) >> 4, (arena[2] + dz) >> 4);
                    set(level, new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 0; dy < HEIGHT; dy++) {
                        set(level, new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            CommandSourceStack quiet = server.createCommandSourceStack().withSource(CommandSource.NULL).withLevel(level);
            server.getCommands().performPrefixedCommand(quiet, "weather clear");
            WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_RESCUE, 1);
            removeZones(server);
            String dimension = ZoneServer.dimension(level);
            save(server, Zone.create(dimension, WIND_ZONE, ZoneShape.BOX, ZoneBox.blocks(arena[0] + WIND_FROM, arena[1] - 1, arena[2] - SIDE,
                    arena[0] + WIND_TO, arena[1] + HEIGHT, arena[2] + SIDE)).withOverride(WorldRuleSpecs.WIND, STRONG_WIND));
            save(server, Zone.create(dimension, MOON_ZONE, ZoneShape.BOX, ZoneBox.blocks(arena[0] + MOON_FROM, arena[1] - 1, arena[2] - SIDE,
                    arena[0] + MOON_TO, arena[1] + HEIGHT, arena[2] + SIDE)).withOverride(WorldRuleSpecs.GRAVITY_SCALE, LOW_GRAVITY));
            DRONE_A.set(spawn(level, at(arena, A_X, 0.5), ExampleFollow.HOLD_TAG).getUUID());
            DRONE_B.set(spawn(level, at(arena, B_X, B_Z), null).getUUID());
            ExampleFollow.ENDED.clear();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        if (wait != null && wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        UUID a = DRONE_A.get();
        UUID b = DRONE_B.get();
        boolean seen = mc.level != null && a != null && b != null && mc.level.getEntity(a) instanceof QuadcopterEntity
                && mc.level.getEntity(b) instanceof QuadcopterEntity;
        return seen ? StepResult.pass() : ctx.tick() > 200 ? StepResult.fail("Drohnen kommen nicht beim Client an") : StepResult.pending();
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, @Nullable String tag) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, 90.0F, 0.0F);
        if (tag != null) {
            drone.addTag(tag);
        }
        level.addFreshEntity(drone);
        return drone;
    }

    private static void save(MinecraftServer server, Zone zone) {
        ZoneServer.Outcome outcome = ZoneServer.save(server, zone, Component.literal("Selbsttest"), "Selbsttest");
        if (!outcome.ok()) {
            throw new IllegalStateException("Zone " + zone.name() + " abgelehnt: " + outcome.problem());
        }
    }

    private static void removeZones(MinecraftServer server) {
        for (Zone zone : ZoneData.get(server).all()) {
            if (zone.name().equals(WIND_ZONE) || zone.name().equals(MOON_ZONE)) {
                ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest");
            }
        }
    }

    private static void giveRemote(ServerPlayer player, QuadcopterEntity drone) {
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
    }

    private static void teleport(ServerPlayer player, Vec3 pos, float yaw) {
        player.teleportTo(player.level(), pos.x, pos.y, pos.z, Set.of(), yaw, 0.0F, true);
        player.setOnGround(true);
    }

    private static @Nullable StepResult pilotUp(SelfTest.StepContext ctx, AtomicReference<UUID> which, int first) {
        Minecraft mc = Minecraft.getInstance();
        int[] arena = ARENA.get();
        PilotSession session = PilotSession.INSTANCE;
        int phase = phase(ctx);
        if (phase == first) {
            StepResult wait = server(ctx, "Pilot startet", server -> {
                QuadcopterEntity drone = require(server, which);
                ServerPlayer player = serverPlayer(server);
                teleport(player, drone.position().add(VIEW_DX, 0.0, 0.0), -90.0F);
                giveRemote(player, drone);
                DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                if (result != DronePiloting.StartResult.STARTED) {
                    RESULT.set("Start verweigert: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            QuadcopterEntity drone = session.drone();
            if (!session.isActive() || drone == null || !drone.getUUID().equals(which.get())) {
                return since(ctx) > 200 ? StepResult.fail("keine Sitzung am Client") : StepResult.pending();
            }
            next(ctx, first + 1);
            return StepResult.pending();
        }
        DroneSim sim = session.sim();
        if (sim == null || arena == null) {
            return StepResult.fail("Sitzung beim Abheben verloren");
        }
        Telemetry t = sim.telemetry();
        int s = since(ctx);
        if (s < 10) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
            return StepResult.pending();
        }
        if (s < 30) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
            if (s == 29 && !t.armed) {
                return StepResult.fail("nicht scharf: Sperren " + t.armBlockers);
            }
            return StepResult.pending();
        }
        if (!t.armed) {
            return StepResult.fail("beim Abheben entschärft: " + t.lastDisarmReason);
        }
        double height = session.comPosition(new Vector3d()).y - arena[1];
        double hover = Math.clamp(t.hoverThrottleStick, 0.2, 0.6);
        float throttle = (float) Math.clamp(hover + 0.08 * (HOVER + 0.2 - height) - 0.08 * t.verticalSpeed, 0.05, 0.8);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        if (mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        boolean steady = height >= HOVER - 0.2 && Math.abs(t.verticalSpeed) < 0.25;
        if (!steady) {
            ctx.state().remove("steady");
            return s > 500 ? StepResult.fail("hebt nicht ab, Höhe " + height) : StepResult.pending();
        }
        int steadyTicks = (int) ctx.state().merge("steady", 1, (x, y) -> (int) x + (int) y);
        return steadyTicks >= 20 ? null : StepResult.pending();
    }

    private static StepResult holdStep(SelfTest.StepContext ctx) {
        if (ARENA.get() == null || DRONE_A.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_A);
        int phase = phase(ctx);
        if (phase <= 1) {
            StepResult up = pilotUp(ctx, DRONE_A, 0);
            if (up != null) {
                return up;
            }
            next(ctx, 2);
            return StepResult.pending();
        }
        if (phase == 2) {
            StepResult wait = server(ctx, "Aussteigen", server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = require(server, DRONE_A);
                ctx.state().put("exit", drone.position());
                DronePiloting.useRemote(player, player.getMainHandItem());
                if (!AddonAutonomy.active(drone).map(ExampleFollow.HOLD::equals).orElse(false)) {
                    RESULT.set("Halten startet nicht: " + AddonAutonomy.active(drone) + ", Pilot " + drone.pilot().isPresent());
                }
            });
            if (wait != null) {
                return wait;
            }
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put("worst", 0.0);
            next(ctx, 3);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_A);
        Vec3 exit = (Vec3) ctx.state().get("exit");
        if (snap == null || !snap.present() || exit == null) {
            return StepResult.pending();
        }
        if (!snap.active().equals(ExampleFollow.HOLD.toString()) || snap.piloted()) {
            return StepResult.fail("Halten läuft nicht: " + describe(snap) + ", Ende " + ended(DRONE_A.get()));
        }
        double worst = Math.max((double) ctx.state().get("worst"), snap.distance(exit));
        ctx.state().put("worst", worst);
        if (since(ctx) < 200) {
            return StepResult.pending();
        }
        ctx.log(String.format(Locale.ROOT, "Halten 10 s: größte Abweichung %.3f m, %s", worst, describe(snap)));
        return StepResult.check(worst <= HOLD_LIMIT, String.format(Locale.ROOT, "Halten weicht %.2f m ab: %s", worst, describe(snap)));
    }

    private static StepResult walkStep(SelfTest.StepContext ctx, double distance, boolean startFollow, boolean zones) {
        int[] arena = ARENA.get();
        if (arena == null || DRONE_A.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_A);
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Folgen starten", server -> {
                QuadcopterEntity drone = require(server, DRONE_A);
                ServerPlayer player = serverPlayer(server);
                if (startFollow) {
                    teleport(player, new Vec3(drone.getX() - 2.5, arena[1], drone.getZ()), 90.0F);
                    AutonomyResult result = ExampleFollow.follow(drone, player);
                    if (result != AutonomyResult.STARTED) {
                        RESULT.set("Folgen startet nicht: " + result);
                    } else if (!endedWith(drone.getUUID(), AutonomyEnd.REPLACED, "hold")) {
                        RESULT.set("Halten endet nicht mit REPLACED: " + ended(drone.getUUID()));
                    }
                } else if (!AddonAutonomy.active(drone).map(ExampleFollow.FOLLOW::equals).orElse(false)) {
                    RESULT.set("Folgen läuft nicht mehr: " + AddonAutonomy.active(drone));
                }
            });
            if (wait != null) {
                return wait;
            }
            ctx.state().put("walked", 0.0);
            ctx.state().put("worst", 0.0);
            ctx.state().put("wind", 0.0);
            ctx.state().put("gravity", Double.POSITIVE_INFINITY);
            next(ctx, 1);
            return StepResult.pending();
        }
        double walked = (double) ctx.state().get("walked");
        if (walked < distance) {
            double step = Math.min(WALK_STEP, distance - walked);
            ctx.state().put("walked", walked + step);
            ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                teleport(player, new Vec3(player.getX() - step, arena[1], player.getZ()), 90.0F);
            });
        } else if (!ctx.state().containsKey("done")) {
            ctx.state().put("done", ctx.tick());
        }
        Snap snap = snap(DRONE_A);
        if (snap == null || !snap.present()) {
            return StepResult.pending();
        }
        if (!snap.active().equals(ExampleFollow.FOLLOW.toString())) {
            return StepResult.fail("Folgen läuft nicht: " + describe(snap) + ", Ende " + ended(DRONE_A.get()));
        }
        if (since(ctx) > 10 && snap.toPlayer() > (double) ctx.state().get("worst")) {
            ctx.state().put("worst", snap.toPlayer());
            ctx.state().put("worstAt", snap.x() - arena[0]);
        }
        double rel = snap.x() - arena[0];
        if (rel > WIND_FROM + 1 && rel < WIND_TO - 1 && Double.isFinite(snap.windScale())) {
            ctx.state().put("wind", Math.max((double) ctx.state().get("wind"), snap.windScale()));
        }
        if (rel > MOON_FROM + 1 && rel < MOON_TO - 1 && Double.isFinite(snap.gravity())) {
            ctx.state().put("gravity", Math.min((double) ctx.state().get("gravity"), snap.gravity()));
        }
        Integer done = (Integer) ctx.state().get("done");
        if (done == null || ctx.tick() - done < 40) {
            return StepResult.pending();
        }
        double worst = (double) ctx.state().get("worst");
        double wind = (double) ctx.state().get("wind");
        double gravity = (double) ctx.state().get("gravity");
        ctx.log(String.format(Locale.ROOT, "Folgen über %.0f Blöcke: größter Abstand %.2f m (bei x %+.1f), Wind-Faktor in der Zone %.1f, g in der Zone %.2f, %s",
                distance, worst, (double) ctx.state().getOrDefault("worstAt", Double.NaN), wind, gravity, describe(snap)));
        if (worst > (zones ? ZONE_FOLLOW_LIMIT : FOLLOW_LIMIT)) {
            return StepResult.fail(String.format(Locale.ROOT, "Folgen zu weit weg: %.2f m, %s", worst, describe(snap)));
        }
        if (zones && wind < STRONG_WIND - 0.5) {
            return StepResult.fail("Wind-Zone wirkt nicht: Faktor " + wind);
        }
        if (zones && !(gravity < 0.5 * 9.81)) {
            return StepResult.fail("Schwerkraft-Zone wirkt nicht: g " + gravity);
        }
        return StepResult.pass();
    }

    private static StepResult budgetStep(SelfTest.StepContext ctx) {
        StepResult wait = server(ctx, "Budget", server -> {
            QuadcopterEntity b = require(server, DRONE_B);
            ServerPlayer player = serverPlayer(server);
            int before = AutonomyBudget.SERVER.size(AutonomyBudget.Kind.ADDON);
            AutonomyResult result = ExampleFollow.takeoff(b, player, 5.0);
            if (result != AutonomyResult.NO_BUDGET) {
                RESULT.set("erwartet NO_BUDGET, war " + result + " (Add-on-Flüge vorher " + before + ")");
                ExampleFollow.stop(b);
            } else if (before != 1 || AddonAutonomy.active(b).isPresent()) {
                RESULT.set("Zähler falsch: " + before + ", aktiv " + AddonAutonomy.active(b));
            }
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("zweiter Add-on-Flug desselben Spielers: NO_BUDGET");
        return StepResult.pass();
    }

    private record RuleCase(String name, int perPlayer, int total, boolean takeoff, AutonomyResult expected, @Nullable String key, int value) {
    }

    private static final RuleCase[] RULE_CASES = {
            new RuleCase("je Spieler 2", 2, 6, false, AutonomyResult.NOT_READY, null, 0),
            new RuleCase("je Spieler 1", 1, 6, true, AutonomyResult.NO_BUDGET, NO_BUDGET_PLAYER, 1),
            new RuleCase("je Spieler 0", 0, 6, true, AutonomyResult.NO_BUDGET, NO_BUDGET_OFF, 0),
            new RuleCase("gesamt 1", 2, 1, false, AutonomyResult.NO_BUDGET, NO_BUDGET_SERVER, 1)
    };

    private static StepResult budgetRuleStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        int index = phase / 2;
        if (index < RULE_CASES.length) {
            RuleCase rule = RULE_CASES[index];
            if (phase % 2 == 0) {
                if (ctx.state().get(FUTURE) == null) {
                    Minecraft.getInstance().gui.hud.setOverlayMessage(Component.empty(), false);
                }
                StepResult wait = server(ctx, rule.name(), server -> {
                    QuadcopterEntity b = require(server, DRONE_B);
                    ServerPlayer player = serverPlayer(server);
                    WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER, rule.perPlayer());
                    WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL, rule.total());
                    int before = AutonomyBudget.SERVER.size(AutonomyBudget.Kind.ADDON);
                    AutonomyResult result = rule.takeoff() ? ExampleFollow.takeoff(b, player, 5.0) : ExampleFollow.hold(b, player);
                    if (result != rule.expected()) {
                        RESULT.set("erwartet " + rule.expected() + ", war " + result + " (Add-on-Flüge vorher " + before + ")");
                        ExampleFollow.stop(b);
                    } else if (before != 1 || AddonAutonomy.active(b).isPresent()) {
                        RESULT.set("Zähler falsch: " + before + ", aktiv " + AddonAutonomy.active(b));
                    }
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, phase + 1);
                return StepResult.pending();
            }
            if (rule.key() == null) {
                ctx.log(rule.name() + ": " + rule.expected() + " statt NO_BUDGET, der zweite Flug passt ins Budget");
                next(ctx, phase + 1);
                return StepResult.pending();
            }
            Component shown = overlayMessage();
            if (shows(shown, rule.key(), rule.value())) {
                ctx.log(rule.name() + ": NO_BUDGET, Meldung „" + shown.getString() + "“");
                next(ctx, phase + 1);
                return StepResult.pending();
            }
            if (since(ctx) > 100) {
                return StepResult.fail(rule.name() + ": Meldung " + rule.key() + " mit Wert " + rule.value() + " fehlt, angezeigt: "
                        + (shown == null ? "nichts" : "„" + shown.getString() + "“ " + shown.getContents()));
            }
            return StepResult.pending();
        }
        StepResult wait = server(ctx, "Regeln zurücksetzen", AutonomySelfTests::resetBudgetRules);
        if (wait != null) {
            return wait;
        }
        return StepResult.pass();
    }

    private static void resetBudgetRules(MinecraftServer server) {
        WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL.defaultValue());
    }

    private static @Nullable Component overlayMessage() {
        try {
            Field field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            return (Component) field.get(Minecraft.getInstance().gui.hud);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean shows(@Nullable Component shown, String key, int value) {
        if (shown == null || !(shown.getContents() instanceof TranslatableContents text) || !key.equals(text.getKey())) {
            return false;
        }
        Object[] args = text.getArgs();
        if (NO_BUDGET_OFF.equals(key)) {
            return args.length == 2;
        }
        String shownValue = args.length == 3 && args[1] instanceof Component part ? part.getString() : args.length == 3 ? String.valueOf(args[1]) : "";
        return String.valueOf(value).equals(shownValue) && shown.getString().contains(" " + value + " ");
    }

    private static StepResult landStep(SelfTest.StepContext ctx, boolean expectLanded) {
        poll(ctx, DRONE_A);
        if (phase(ctx) == 0) {
            StepResult wait = server(ctx, "land()", server -> {
                QuadcopterEntity drone = require(server, DRONE_A);
                if (!ExampleFollow.land(drone)) {
                    RESULT.set("kein Auftrag zum Landen");
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_A);
        if (snap == null || !snap.present()) {
            return StepResult.pending();
        }
        if (snap.armed() || !snap.active().isEmpty() || !snap.phase().isEmpty()) {
            return StepResult.pending();
        }
        if (!expectLanded || endedWith(DRONE_A.get(), AutonomyEnd.LANDED, "follow")) {
            ctx.log("gelandet: " + describe(snap) + ", Ende " + ended(DRONE_A.get()));
            return StepResult.check(snap.y() - ARENA.get()[1] < 0.2, "nicht am Boden: " + describe(snap));
        }
        return StepResult.fail("Ende nicht LANDED: " + ended(DRONE_A.get()));
    }

    private static StepResult handoverStep(SelfTest.StepContext ctx) {
        if (ARENA.get() == null || DRONE_A.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_A);
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Folgen-Markierung", server -> {
                QuadcopterEntity drone = require(server, DRONE_A);
                drone.removeTag(ExampleFollow.HOLD_TAG);
                drone.addTag(ExampleFollow.FOLLOW_TAG);
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        if (phase <= 2) {
            StepResult up = pilotUp(ctx, DRONE_A, 1);
            if (up != null) {
                return up;
            }
            next(ctx, 3);
            return StepResult.pending();
        }
        if (phase == 3) {
            StepResult wait = server(ctx, "Aussteigen", server -> {
                ServerPlayer player = serverPlayer(server);
                QuadcopterEntity drone = require(server, DRONE_A);
                ctx.state().put("exitY", drone.getY());
                DronePiloting.useRemote(player, player.getMainHandItem());
                if (!AddonAutonomy.active(drone).map(ExampleFollow.FOLLOW::equals).orElse(false)) {
                    RESULT.set("Folgen startet bei der Übergabe nicht: " + AddonAutonomy.active(drone));
                }
            });
            if (wait != null) {
                return wait;
            }
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put("lowest", Double.POSITIVE_INFINITY);
            next(ctx, 4);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_A);
        Double exitY = (Double) ctx.state().get("exitY");
        if (snap == null || !snap.present() || exitY == null) {
            return StepResult.pending();
        }
        if (!snap.active().equals(ExampleFollow.FOLLOW.toString())) {
            return StepResult.fail("Folgen läuft nach der Übergabe nicht: " + describe(snap) + ", Ende " + ended(DRONE_A.get()));
        }
        double lowest = Math.min((double) ctx.state().get("lowest"), snap.y());
        ctx.state().put("lowest", lowest);
        if (since(ctx) < 60) {
            return StepResult.pending();
        }
        double loss = exitY - lowest;
        ctx.log(String.format(Locale.ROOT, "Übergabe mit Folgen: Höhenverlust %.3f m in 3 s, %s", loss, describe(snap)));
        return StepResult.check(loss <= HEIGHT_LOSS, String.format(Locale.ROOT, "Höhenverlust %.2f m: %s", loss, describe(snap)));
    }

    private static StepResult stopStep(SelfTest.StepContext ctx) {
        poll(ctx, DRONE_A);
        if (phase(ctx) == 0) {
            StepResult wait = server(ctx, "stop()", server -> {
                QuadcopterEntity drone = require(server, DRONE_A);
                drone.removeTag(ExampleFollow.FOLLOW_TAG);
                if (!ExampleFollow.stop(drone)) {
                    RESULT.set("stop() ohne Flug");
                } else if (!endedWith(drone.getUUID(), AutonomyEnd.STOPPED, "follow") || AddonAutonomy.active(drone).isPresent()) {
                    RESULT.set("Ende nicht STOPPED: " + ended(drone.getUUID()) + ", aktiv " + AddonAutonomy.active(drone));
                } else if (!AutonomousReturns.flying(drone)) {
                    RESULT.set("Propwash landet nicht selbst");
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_A);
        if (snap == null || !snap.present() || snap.armed() || !snap.phase().isEmpty()) {
            return since(ctx) > 800 ? StepResult.fail("landet nach stop() nicht: " + describe(snap)) : StepResult.pending();
        }
        ctx.log("nach stop() gelandet: " + describe(snap));
        return StepResult.check(snap.y() - ARENA.get()[1] < 0.2, "nicht am Boden: " + describe(snap));
    }

    private static StepResult takeoffStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null || DRONE_B.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_B);
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Start vom Boden", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                ServerPlayer player = serverPlayer(server);
                teleport(player, drone.position().add(VIEW_DX, 0.0, 0.0), -90.0F);
                AutonomyResult result = ExampleFollow.takeoff(drone, player, 5.0);
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start vom Boden: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            ctx.state().put("top", 0.0);
            next(ctx, 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_B);
        if (snap == null || !snap.present()) {
            return StepResult.pending();
        }
        if (phase == 1) {
            if (!"ASSIST".equals(snap.phase())) {
                return since(ctx) > 400 ? StepResult.fail("Steigen endet nicht: " + describe(snap)) : StepResult.pending();
            }
            double height = snap.y() - arena[1];
            if (since(ctx) < 20 + (int) ctx.state().getOrDefault("assistAt", since(ctx))) {
                ctx.state().putIfAbsent("assistAt", since(ctx));
                ctx.state().put("top", Math.max((double) ctx.state().get("top"), height));
                return StepResult.pending();
            }
            double top = Math.max((double) ctx.state().get("top"), height);
            ctx.log(String.format(Locale.ROOT, "Start vom Boden: Höhe %.2f m, %s", top, describe(snap)));
            if (Math.abs(top - 5.0) > 0.6) {
                return StepResult.fail(String.format(Locale.ROOT, "Starthöhe %.2f statt 5 m: %s", top, describe(snap)));
            }
            ctx.onServer(server -> ExampleFollow.land(require(server, DRONE_B)));
            next(ctx, 2);
            return StepResult.pending();
        }
        if (snap.armed() || !snap.phase().isEmpty()) {
            return since(ctx) > 600 ? StepResult.fail("landet nach land() nicht: " + describe(snap)) : StepResult.pending();
        }
        if (!endedWith(DRONE_B.get(), AutonomyEnd.LANDED, "hold")) {
            return StepResult.fail("Ende nicht LANDED: " + ended(DRONE_B.get()));
        }
        ctx.log("nach land() entschärft: " + describe(snap));
        return StepResult.check(snap.y() - arena[1] < 0.2, "nicht am Boden: " + describe(snap));
    }

    private static BlockPos padBlock(int[] arena, int index) {
        return new BlockPos(arena[0] + PAD_X[index], index == 0 ? arena[1] - 1 : arena[1], arena[2] + PAD_Z);
    }

    private static double padTop(int[] arena, int index) {
        return padBlock(arena, index).getY() + (index == 2 ? 0.5 : 1.0 / 16.0);
    }

    private static BlockState padState(int index) {
        return index == 2 ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : Blocks.CARPET.white().defaultBlockState();
    }

    private static StepResult partialBlockStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("kein Testfeld");
        }
        int phase = phase(ctx);
        int index = phase / 5;
        if (index >= PAD_NAMES.length) {
            StepResult wait = server(ctx, "zurück zu Drohne B", server -> {
                QuadcopterEntity drone = drone(server, DRONE_B);
                if (drone != null) {
                    teleport(serverPlayer(server), drone.position().add(VIEW_DX, 0.0, 0.0), -90.0F);
                }
            });
            if (wait != null) {
                return wait;
            }
            @SuppressWarnings("unchecked")
            List<String> misses = (List<String>) ctx.state().getOrDefault("padMisses", List.of());
            return misses.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", misses));
        }
        String name = PAD_NAMES[index];
        BlockPos block = padBlock(arena, index);
        double top = padTop(arena, index);
        double cx = block.getX() + 0.5;
        double cz = block.getZ() + 0.5;
        if (DRONE_PAD.get() != null) {
            poll(ctx, DRONE_PAD);
        }
        int step = phase % 5;
        if (step == 0) {
            StepResult wait = server(ctx, name + ": Block und Drohne", server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                set(level, block.below(), Blocks.SMOOTH_STONE.defaultBlockState());
                set(level, block, padState(index));
                QuadcopterEntity drone = spawn(level, new Vec3(cx + PAD_OFFSET, top, cz), null);
                DRONE_PAD.set(drone.getUUID());
                teleport(player, new Vec3(cx + VIEW_DX, arena[1], cz), -90.0F);
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_PAD);
        if (step == 1) {
            if (since(ctx) < 20) {
                return StepResult.pending();
            }
            StepResult wait = server(ctx, name + ": Start vom Boden", server -> {
                AutonomyResult result = ExampleFollow.takeoff(require(server, DRONE_PAD), serverPlayer(server), PAD_HEIGHT);
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start vom Boden: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (step < 4 && (snap == null || !snap.present())) {
            return since(ctx) > 200 ? StepResult.fail(name + ": " + describe(snap)) : StepResult.pending();
        }
        if (step == 2) {
            if (!"ASSIST".equals(snap.phase())) {
                ctx.state().remove("padAssist");
                return since(ctx) > 400 ? StepResult.fail(name + ": Steigen endet nicht: " + describe(snap)) : StepResult.pending();
            }
            ctx.state().putIfAbsent("padAssist", ctx.tick());
            if (ctx.tick() - (int) ctx.state().get("padAssist") < 40) {
                return StepResult.pending();
            }
            StepResult wait = server(ctx, name + ": land()", server -> {
                QuadcopterEntity drone = require(server, DRONE_PAD);
                ctx.state().put("padAt", new double[]{drone.getX(), drone.getZ()});
                if (!ExampleFollow.land(drone)) {
                    RESULT.set("kein Auftrag zum Landen");
                }
            });
            if (wait != null) {
                return wait;
            }
            ctx.state().remove("padAssist");
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (step == 4) {
            StepResult wait = server(ctx, name + ": Drohne entfernen", server -> {
                QuadcopterEntity drone = drone(server, DRONE_PAD);
                if (drone != null) {
                    drone.discard();
                }
            });
            if (wait != null) {
                return wait;
            }
            DRONE_PAD.set(null);
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (snap.armed() || !snap.phase().isEmpty()) {
            return since(ctx) > 900 ? StepResult.fail(name + ": landet nach land() nicht: " + describe(snap)) : StepResult.pending();
        }
        double[] at = (double[]) ctx.state().get("padAt");
        double off = Math.hypot(snap.x() - at[0], snap.z() - at[1]);
        double centre = Math.hypot(snap.x() - cx, snap.z() - cz);
        boolean onBlock = Math.abs(snap.x() - cx) < 0.5 && Math.abs(snap.z() - cz) < 0.5 && Math.abs(snap.y() - top) < 0.15;
        String line = String.format(Locale.ROOT, "%s: land() %.2f m neben der Mitte, setzt %.2f m neben diesem Punkt und %.2f m neben der Mitte auf, "
                        + "%.2f m über der Oberseite%s, Ende %s", name, Math.hypot(at[0] - cx, at[1] - cz), off, centre, snap.y() - top,
                onBlock ? "" : " (nicht auf dem Block)", ended(DRONE_PAD.get()));
        ctx.log(line);
        if (off > PAD_LIMIT || !onBlock || !endedWith(DRONE_PAD.get(), AutonomyEnd.LANDED, "hold")) {
            @SuppressWarnings("unchecked")
            List<String> misses = (List<String>) ctx.state().computeIfAbsent("padMisses", k -> new ArrayList<String>());
            misses.add(line);
        }
        next(ctx, phase + 1);
        return StepResult.pending();
    }

    private static StepResult homePadStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("kein Testfeld");
        }
        int phase = phase(ctx);
        int index = phase / 5;
        if (index >= PAD_NAMES.length) {
            StepResult wait = server(ctx, "zurück zu Drohne B", server -> {
                QuadcopterEntity drone = drone(server, DRONE_B);
                if (drone != null) {
                    teleport(serverPlayer(server), drone.position().add(VIEW_DX, 0.0, 0.0), -90.0F);
                }
            });
            if (wait != null) {
                return wait;
            }
            @SuppressWarnings("unchecked")
            List<String> misses = (List<String>) ctx.state().getOrDefault("homePadMisses", List.of());
            return misses.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", misses));
        }
        String name = PAD_NAMES[index];
        BlockPos block = padBlock(arena, index);
        double top = padTop(arena, index);
        double cx = block.getX() + 0.5;
        double cz = block.getZ() + 0.5;
        double homeX = cx + HOME_PAD_OFFSET;
        if (DRONE_PAD.get() != null) {
            poll(ctx, DRONE_PAD);
        }
        int step = phase % 5;
        if (step == 0) {
            StepResult wait = server(ctx, name + ": Block, Homepoint und Drohne", server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                set(level, block.below(), Blocks.SMOOTH_STONE.defaultBlockState());
                set(level, block, padState(index));
                QuadcopterEntity drone = spawn(level, new Vec3(homeX, arena[1], cz + HOME_PAD_RUN), null);
                drone.setHomePoint(new HomePoint(homeX, top, cz, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.COMMAND));
                DRONE_PAD.set(drone.getUUID());
                teleport(player, new Vec3(cx + VIEW_DX, arena[1], cz + 0.5 * HOME_PAD_RUN), -90.0F);
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (step == 1) {
            if (since(ctx) < 20) {
                return StepResult.pending();
            }
            StepResult wait = server(ctx, name + ": Start vom Boden mit Höchstdauer", server -> {
                AutonomyResult result = ExampleFollow.start(require(server, DRONE_PAD), serverPlayer(server), new ExampleFollow.Hold(),
                        AutonomyOptions.of(ExampleFollow.HOLD).label("HOLD").takeoff(PAD_HEIGHT).maxDuration(Duration.ofSeconds(HOME_PAD_SECONDS)));
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start vom Boden: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (step == 4) {
            StepResult wait = server(ctx, name + ": Drohne entfernen", server -> {
                QuadcopterEntity drone = drone(server, DRONE_PAD);
                if (drone != null) {
                    drone.discard();
                }
            });
            if (wait != null) {
                return wait;
            }
            DRONE_PAD.set(null);
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_PAD);
        if (snap == null || !snap.present()) {
            return since(ctx) > 200 ? StepResult.fail(name + ": " + describe(snap)) : StepResult.pending();
        }
        if (step == 2) {
            if (!endedWith(DRONE_PAD.get(), AutonomyEnd.TIMEOUT, "hold") || !snap.planned() || !snap.homeFlying()) {
                return since(ctx) > 600 ? StepResult.fail(name + ": kein Heimflug nach TIMEOUT: " + describe(snap) + ", Ende " + ended(DRONE_PAD.get()))
                        : StepResult.pending();
            }
            ctx.log(name + ": Ende mit TIMEOUT, Heimflug läuft: " + describe(snap));
            next(ctx, phase + 1);
            return StepResult.pending();
        }
        if (snap.planned() || snap.armed()) {
            return since(ctx) > 1700 ? StepResult.fail(name + ": Heimflug landet nicht: " + describe(snap)) : StepResult.pending();
        }
        double off = Math.hypot(snap.x() - homeX, snap.z() - cz);
        boolean onBlock = Math.abs(snap.x() - cx) < 0.5 && Math.abs(snap.z() - cz) < 0.5 && Math.abs(snap.y() - top) < 0.15;
        String line = String.format(Locale.ROOT, "%s: Heimflug zum Homepoint %.2f m neben der Mitte setzt %.2f m neben dem Homepoint auf, "
                + "%.2f m über der Oberseite%s", name, HOME_PAD_OFFSET, off, snap.y() - top, onBlock ? "" : " (nicht auf dem Block)");
        ctx.log(line);
        if (off > PAD_LIMIT || !onBlock) {
            @SuppressWarnings("unchecked")
            List<String> misses = (List<String>) ctx.state().computeIfAbsent("homePadMisses", k -> new ArrayList<String>());
            misses.add(line);
        }
        next(ctx, phase + 1);
        return StepResult.pending();
    }

    private static StepResult pilotStep(SelfTest.StepContext ctx) {
        poll(ctx, DRONE_B);
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Start vom Boden", server -> {
                AutonomyResult result = ExampleFollow.takeoff(require(server, DRONE_B), serverPlayer(server), 2.0);
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start vom Boden: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_B);
        if (phase == 1) {
            if (snap == null || !snap.present() || !"ASSIST".equals(snap.phase())) {
                return since(ctx) > 300 ? StepResult.fail("Steigen endet nicht: " + describe(snap)) : StepResult.pending();
            }
            next(ctx, 2);
            return StepResult.pending();
        }
        if (phase == 2) {
            StepResult wait = server(ctx, "Pilot übernimmt", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                ServerPlayer player = serverPlayer(server);
                giveRemote(player, drone);
                DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                if (result != DronePiloting.StartResult.STARTED) {
                    RESULT.set("Übernahme verweigert: " + result);
                } else if (!endedWith(drone.getUUID(), AutonomyEnd.PILOT, "hold") || AddonAutonomy.active(drone).isPresent()
                        || AddonAutonomy.flying(drone)) {
                    RESULT.set("Ende nicht PILOT: " + ended(drone.getUUID()) + ", aktiv " + AddonAutonomy.active(drone));
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 3);
            return StepResult.pending();
        }
        if (phase == 3) {
            if (since(ctx) < 10) {
                return StepResult.pending();
            }
            StepResult wait = server(ctx, "Pilot aussteigen", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                if (AddonAutonomy.flying(drone)) {
                    RESULT.set("ohne Markierung startet ein Add-on-Flug");
                }
            });
            if (wait != null) {
                return wait;
            }
            PilotSession.INSTANCE.clearTestInput();
            next(ctx, 4);
            return StepResult.pending();
        }
        if (snap == null || !snap.present() || snap.armed() || snap.y() - ARENA.get()[1] > 0.2) {
            return since(ctx) > 300 ? StepResult.fail("Drohne kommt nicht zur Ruhe: " + describe(snap)) : StepResult.pending();
        }
        ctx.log("Pilot übernimmt: Ende " + ended(DRONE_B.get()));
        return StepResult.pass();
    }

    private static StepResult takeoverStep(SelfTest.StepContext ctx, boolean assisted) {
        StepResult result = takeover(ctx, assisted);
        if (result.status() != StepResult.Status.PENDING) {
            restoreInput();
            if (result.status() == StepResult.Status.FAIL && phase(ctx) < 5) {
                ctx.onServer(server -> {
                    QuadcopterEntity drone = drone(server, DRONE_B);
                    if (drone != null) {
                        ExampleFollow.stop(drone);
                        if (drone.pilot().isPresent()) {
                            DronePiloting.release(drone);
                        }
                    }
                });
            }
        }
        return result;
    }

    private static StepResult takeover(SelfTest.StepContext ctx, boolean assisted) {
        int[] arena = ARENA.get();
        if (arena == null || DRONE_B.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_B);
        PilotSession session = PilotSession.INSTANCE;
        int phase = phase(ctx);
        if (phase == 0) {
            if (ctx.state().putIfAbsent("keyboard", true) == null) {
                useKeyboard(assisted);
            }
            StepResult wait = server(ctx, "Start vom Boden", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                ServerPlayer player = serverPlayer(server);
                teleport(player, drone.position().add(VIEW_DX, 0.0, 0.0), -90.0F);
                AutonomyResult result = ExampleFollow.takeoff(drone, player, TAKEOVER_HEIGHT);
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start vom Boden: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_B);
        if (phase == 1) {
            if (snap == null || !snap.present() || !"ASSIST".equals(snap.phase())) {
                return since(ctx) > 400 ? StepResult.fail("Steigen endet nicht: " + describe(snap)) : StepResult.pending();
            }
            int at = (int) ctx.state().computeIfAbsent("assistAt", key -> since(ctx));
            if (since(ctx) - at < 40) {
                return StepResult.pending();
            }
            next(ctx, 2);
            return StepResult.pending();
        }
        if (phase == 2) {
            StepResult wait = server(ctx, "Pilot übernimmt", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                ServerPlayer player = serverPlayer(server);
                giveRemote(player, drone);
                ctx.state().put("before", drone.getY());
                DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                if (result != DronePiloting.StartResult.STARTED) {
                    RESULT.set("Übernahme verweigert: " + result);
                } else if (!endedWith(drone.getUUID(), AutonomyEnd.PILOT, "hold") || AddonAutonomy.flying(drone)) {
                    RESULT.set("Ende nicht PILOT: " + ended(drone.getUUID()) + ", aktiv " + AddonAutonomy.active(drone));
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 3);
            return StepResult.pending();
        }
        if (phase == 3) {
            QuadcopterEntity flown = session.drone();
            DroneSim sim = session.sim();
            if (!session.isActive() || flown == null || sim == null || !flown.getUUID().equals(DRONE_B.get())) {
                return since(ctx) > 200 ? StepResult.fail("keine Sitzung am Client") : StepResult.pending();
            }
            int tick = (int) ctx.state().merge("ticks", 1, (a, b) -> (int) a + (int) b) - 1;
            if (tick == 0) {
                ctx.state().put("armedAtStart", session.armed());
            }
            if (!session.armed()) {
                ctx.state().putIfAbsent("disarmedAt", tick);
            }
            double lowest = Math.min((double) ctx.state().getOrDefault("lowest", Double.POSITIVE_INFINITY), flown.getY());
            ctx.state().put("lowest", lowest);
            if (tick == TAKEOVER_THROTTLE_TICK) {
                Telemetry t = sim.telemetry();
                ctx.state().put("throttle", (double) RadioState.INSTANCE.sticks().throttle());
                ctx.state().put("expected", assisted ? 0.5 : t.hoverThrottleStick);
                ctx.state().put("source", String.valueOf(RadioState.INSTANCE.source()));
                ctx.state().put("assist", sim.assistOwnsHold());
            }
            if (tick < TAKEOVER_TICKS) {
                return StepResult.pending();
            }
            double loss = (double) ctx.state().get("before") - lowest;
            boolean armedAtStart = (boolean) ctx.state().get("armedAtStart");
            Object disarmedAt = ctx.state().get("disarmedAt");
            double throttle = (double) ctx.state().get("throttle");
            double expected = (double) ctx.state().get("expected");
            String detail = String.format(Locale.ROOT, "scharf beim Start %s, entschärft ab Tick %s, Höhenverlust %.3f m in %.1f s, Gas %.3f (erwartet %.3f, %s), "
                            + "Flughilfe hält %s, Motoren %.0f U/min", armedAtStart, disarmedAt == null ? "-" : disarmedAt, loss, TAKEOVER_TICKS / 20.0,
                    throttle, expected, ctx.state().get("source"), ctx.state().get("assist"), averageRpm(sim.telemetry()));
            ctx.log("Übernahme im Flug" + (assisted ? " mit Flughilfe" : "") + ": " + detail);
            String verdict = null;
            if (!armedAtStart || disarmedAt != null) {
                verdict = "nicht scharf übernommen: " + detail;
            } else if (loss > TAKEOVER_LOSS) {
                verdict = String.format(Locale.ROOT, "sackt %.2f m ab (Grenze %.2f m): %s", loss, TAKEOVER_LOSS, detail);
            } else if (Math.abs(throttle - expected) > THROTTLE_TOLERANCE) {
                verdict = "Gas nicht passend zur Flughilfe: " + detail;
            } else if (assisted != (boolean) ctx.state().get("assist")) {
                verdict = "Flughilfe hält die Höhe " + (assisted ? "nicht" : "unerwartet") + ": " + detail;
            }
            ctx.state().put("verdict", verdict == null ? "" : verdict);
            next(ctx, 4);
            return StepResult.pending();
        }
        if (phase == 4) {
            StepResult wait = server(ctx, "Pilot steigt aus, Beispiel landet", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                drone.addTag(ExampleFollow.HOLD_TAG);
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.removeTag(ExampleFollow.HOLD_TAG);
                ExampleFollow.land(drone);
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 5);
            return StepResult.pending();
        }
        if (snap == null || !snap.present() || snap.armed() || snap.piloted() || !snap.phase().isEmpty() || snap.y() - arena[1] > 0.2) {
            return since(ctx) > 800 ? StepResult.fail("Drohne kommt nach der Übernahme nicht zur Ruhe: " + describe(snap)) : StepResult.pending();
        }
        String verdict = (String) ctx.state().get("verdict");
        return verdict.isEmpty() ? StepResult.pass() : StepResult.fail(verdict);
    }

    private static double averageRpm(Telemetry t) {
        double sum = 0.0;
        for (double rpm : t.motorRpm) {
            sum += Math.abs(rpm);
        }
        return t.motorRpm.length == 0 ? 0.0 : sum / t.motorRpm.length;
    }

    private static void useKeyboard(boolean assisted) {
        if (!assistRegistered) {
            assistRegistered = true;
            PropwashApi.flight().registerAssist(TAKEOVER_ASSIST, profile -> ASSIST_ON.get() ? new CenterAltitude() : null);
        }
        ASSIST_ON.set(assisted);
        SAVED_INPUT.compareAndSet(null, new boolean[]{InputConfig.get().keyboardFallback, TransmitterInput.INSTANCE.suppressDevice()});
        PilotSession.INSTANCE.clearTestInput();
        TransmitterInput.INSTANCE.simulate(null);
        TransmitterInput.INSTANCE.setSuppressDevice(true);
        InputConfig.get().keyboardFallback = true;
    }

    private static void restoreInput() {
        ASSIST_ON.set(false);
        boolean[] saved = SAVED_INPUT.getAndSet(null);
        if (saved != null) {
            InputConfig.get().keyboardFallback = saved[0];
            TransmitterInput.INSTANCE.setSuppressDevice(saved[1]);
        }
    }

    private static final class CenterAltitude implements FlightAssist {
        @Override
        public void update(FlightState state, AssistOutput output) {
            if (!state.armed()) {
                return;
            }
            output.climb((state.stickThrottle() - 0.5) * 4.0);
            output.label("ALT");
        }
    }

    private static StepResult batteryStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null || DRONE_A.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_A);
        int phase = phase(ctx);
        Vec3 home = at(arena, A_X, 0.5);
        if (phase == 0) {
            StepResult wait = server(ctx, "Akku fast leer", server -> {
                QuadcopterEntity drone = require(server, DRONE_A);
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                teleport(player, drone.position().add(-VIEW_DX, 0.0, 0.0), 90.0F);
                drone.setHomePoint(new HomePoint(home.x, home.y, home.z, HomePointCodecs.dimension(level), level.getGameTime(),
                        HomePoint.Source.COMMAND));
                drone.setBatteryCharge(LOW_CHARGE);
                AutonomyResult result = ExampleFollow.takeoff(drone, player, 4.0);
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_A);
        if (snap == null || !snap.present()) {
            return StepResult.pending();
        }
        if (phase == 1) {
            if (!endedWith(DRONE_A.get(), AutonomyEnd.BATTERY, "hold")) {
                return since(ctx) > 600 ? StepResult.fail("kein Ende mit BATTERY: " + describe(snap) + ", Ende " + ended(DRONE_A.get()))
                        : StepResult.pending();
            }
            if (!snap.planned() || !snap.homeFlying()) {
                return since(ctx) > 640 ? StepResult.fail("kein Heimflug nach BATTERY: " + describe(snap)) : StepResult.pending();
            }
            ctx.log("Ende mit BATTERY, Heimflug läuft: " + describe(snap));
            ctx.state().put("far", Math.hypot(snap.x() - home.x, snap.z() - home.z));
            next(ctx, 2);
            return StepResult.pending();
        }
        if (snap.planned() || snap.armed()) {
            return since(ctx) > 1700 ? StepResult.fail("Heimflug landet nicht: " + describe(snap)) : StepResult.pending();
        }
        double distance = Math.hypot(snap.x() - home.x, snap.z() - home.z);
        ctx.log(String.format(Locale.ROOT, "Heimflug nach BATTERY über %.1f m, gelandet %.2f m vom Homepoint", (double) ctx.state().get("far"),
                distance));
        return StepResult.check(distance <= 2.0 && snap.y() - arena[1] < 0.2, "nicht am Homepoint gelandet: " + describe(snap));
    }

    private static StepResult armStep(SelfTest.StepContext ctx) {
        StepResult wait = server(ctx, "Schärfen", server -> {
            QuadcopterEntity drone = require(server, DRONE_B);
            ServerPlayer player = serverPlayer(server);
            AutonomyResult ready = ExampleFollow.hold(drone, player);
            ItemStack battery = drone.takeBattery();
            AutonomyResult armable = ExampleFollow.takeoff(drone, player, 5.0);
            if (ready != AutonomyResult.NOT_READY) {
                RESULT.set("ohne takeoff am Boden erwartet NOT_READY, war " + ready);
            } else if (armable != AutonomyResult.NOT_ARMABLE || AddonAutonomy.flying(drone)) {
                RESULT.set("ohne Akku erwartet NOT_ARMABLE, war " + armable);
            }
            if (!battery.isEmpty()) {
                drone.insertBattery(drone.build().battery(), 1.0F);
            }
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("am Boden ohne takeoff NOT_READY, ohne Akku NOT_ARMABLE");
        return StepResult.pass();
    }

    private static StepResult angleLimitStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null || DRONE_B.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx, DRONE_B);
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Neigungsgrenze", server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                ServerPlayer player = serverPlayer(server);
                teleport(player, drone.position().add(VIEW_DX, 0.0, 0.0), -90.0F);
                double before = PropwashApi.drones().view(drone).map(DroneView::angleLimitDeg).orElse(Double.NaN);
                drone.setTune(drone.tune().with(TuneMapping.ANGLE_LIMIT, TILT_LIMIT));
                double after = PropwashApi.drones().view(drone).map(DroneView::angleLimitDeg).orElse(Double.NaN);
                TiltProbe probe = new TiltProbe();
                PROBE.set(probe);
                if (before != FlightState.DEFAULT_ANGLE_LIMIT_DEG || after != TILT_LIMIT) {
                    RESULT.set(String.format(Locale.ROOT, "DroneView am Server: vorher %.1f°, mit Tune %.1f°", before, after));
                    return;
                }
                AutonomyResult result = PropwashApi.drones().autonomy().start(drone, player, probe, AutonomyOptions.of(TILT).label("TILT").takeoff(HOVER));
                if (result != AutonomyResult.STARTED) {
                    RESULT.set("Start: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            ctx.log("DroneView am Server: 60° ohne Tune, 30° mit Tune");
            next(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            double client = clientAngleLimit(DRONE_B.get());
            if (client != TILT_LIMIT) {
                return since(ctx) > 100 ? StepResult.fail(String.format(Locale.ROOT, "DroneView am Client: %.1f° statt 30°", client)) : StepResult.pending();
            }
            ctx.log(String.format(Locale.ROOT, "DroneView am Client: %.1f°", client));
            next(ctx, 2);
            return StepResult.pending();
        }
        TiltProbe probe = PROBE.get();
        if (phase == 2) {
            if (probe == null || !probe.done) {
                return since(ctx) > 600 ? StepResult.fail("Messung endet nicht: " + describe(snap(DRONE_B))) : StepResult.pending();
            }
            ctx.onServer(server -> {
                QuadcopterEntity drone = require(server, DRONE_B);
                PropwashApi.drones().autonomy().stop(drone);
                drone.setTune(drone.tune().without(TuneMapping.ANGLE_LIMIT));
            });
            String result = String.format(Locale.ROOT, "FlightState#angleLimitDeg %.1f°, größte Neigung bei vollem Knüppel %.1f°", probe.limit, probe.maxTilt);
            ctx.log(result);
            if (probe.limit != TILT_LIMIT || Math.abs(probe.maxTilt - TILT_LIMIT) > TILT_TOLERANCE) {
                return StepResult.fail(result);
            }
            next(ctx, 3);
            return StepResult.pending();
        }
        Snap snap = snap(DRONE_B);
        if (snap == null || !snap.present() || snap.armed() || !snap.phase().isEmpty()) {
            return since(ctx) > 800 ? StepResult.fail("landet nach stop() nicht: " + describe(snap)) : StepResult.pending();
        }
        ctx.log("nach stop() gelandet: " + describe(snap));
        return StepResult.pass();
    }

    private static double clientAngleLimit(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Double.NaN;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && drone.getUUID().equals(id)) {
                return PropwashApi.drones().view(drone).map(DroneView::angleLimitDeg).orElse(Double.NaN);
            }
        }
        return Double.NaN;
    }

    private static final class TiltProbe implements FlightAssist {
        private static final double SETTLE = 1.5;
        private static final double PUSH = 0.6;
        volatile double limit = Double.NaN;
        volatile double maxTilt;
        volatile boolean done;
        private double start = Double.NaN;
        private double x;
        private double y;
        private double z;

        @Override
        public void update(FlightState state, AssistOutput output) {
            this.limit = state.angleLimitDeg();
            if (Double.isNaN(this.start)) {
                this.start = state.time();
                this.x = state.x();
                this.y = state.y();
                this.z = state.z();
            }
            double t = state.time() - this.start;
            output.holdAltitude(this.y);
            output.label("TILT");
            if (t >= SETTLE && t < SETTLE + PUSH) {
                output.sticks(0.0, 1.0, 0.0, 0.5);
                output.mode(StabilizationMode.ANGLE);
                this.maxTilt = Math.max(this.maxTilt, Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, state.upY())))));
                return;
            }
            output.holdPosition(this.x, this.z);
            this.done = t >= SETTLE + PUSH;
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult wait = server(ctx, "Aufräumen", server -> {
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            for (AtomicReference<UUID> which : List.of(DRONE_A, DRONE_B, DRONE_PAD)) {
                QuadcopterEntity drone = drone(server, which);
                if (drone != null) {
                    ExampleFollow.stop(drone);
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.setReturnPlan(null);
                    drone.discard();
                }
            }
            removeZones(server);
            resetBudgetRules(server);
            for (Map.Entry<BlockPos, BlockState> entry : ORIGINAL.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            }
            ORIGINAL.clear();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            double[] back = PLAYER_HOME.getAndSet(null);
            if (back != null) {
                player.teleportTo(level, back[0], back[1], back[2], Set.of(), (float) back[3], (float) back[4], true);
            }
        });
        if (wait != null) {
            return wait;
        }
        PilotSession.INSTANCE.clearTestInput();
        restoreInput();
        DRONE_A.set(null);
        DRONE_B.set(null);
        DRONE_PAD.set(null);
        ARENA.set(null);
        return StepResult.pass();
    }
}
