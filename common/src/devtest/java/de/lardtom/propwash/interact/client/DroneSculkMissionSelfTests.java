package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DroneSculkMissionSelfTests {
    static final String GROUP = DroneSculkSelfTests.GROUP + "Mission ";
    static final Identifier MISSION = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "sculk_mission");
    private static final int SITE_DX = -36;
    private static final int SITE_DZ = -24;
    private static final int LENGTH = 9;
    private static final int WIDTH = 4;
    private static final int HEIGHT = 9;
    private static final int PAD_DX = -6;
    private static final double ALTITUDE = 3.0;
    private static final double ARRIVED = 0.35;
    private static final double SETTLED_SPEED = 0.4;
    private static final int SPAWN_TICKS = 20;
    private static final int FLIGHT_LIMIT = 1600;
    private static final int TRAVEL_TICKS = 10;
    private static final int QUIET_TICKS = 200;
    private static final int LOG_LINES = 8;
    private static final String STAGE = "mission_stage";
    private static final String MARK = "mission_mark";
    private static final String FUTURE = "mission_future";
    private static final AtomicReference<@Nullable Rig> RIG = new AtomicReference<>();

    private DroneSculkMissionSelfTests() {
    }

    public static void register() {
        ServerBus.TICK_END.register(DroneSculkMissionSelfTests::sample);
        PropwashEvents.AUTONOMY_ENDED.register(DroneSculkMissionSelfTests::ended);
        SelfTest.clientStep(GROUP + "Wollkammer mit Feld aus Sculk-Sensoren aufbauen", 400, DroneSculkMissionSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Cinewhoop fliegt autonom über die Sensoren und landet zwischen ihnen: danach 10 s still, entschärft, Motoren 0",
                FLIGHT_LIMIT + 600, ctx -> missionStep(ctx, DroneBuild.CINEWHOOP, 3, 0, 0.5, 0.5, false));
        SelfTest.clientStep(GROUP + "Freestyle fliegt autonom über die Sensoren und landet auf der Kante eines Sensors: danach 10 s still, "
                + "entschärft, Motoren 0", FLIGHT_LIMIT + 600, ctx -> missionStep(ctx, DroneBuild.FREESTYLE, 1, 1, 1.0, 0.5, false));
        SelfTest.clientStep(GROUP + "Racer fliegt autonom über die Sensoren, Propwash fliegt ihn heim und landet auf der Startfläche: danach 10 s "
                + "still, entschärft, Motoren 0", FLIGHT_LIMIT + 600, ctx -> missionStep(ctx, DroneBuild.RACER, 1, 0, 0.5, 0.5, true));
        SelfTest.clientStep(GROUP + "Wollkammer abbauen", 300, DroneSculkMissionSelfTests::cleanupStep);
    }

    private static final class Rig {
        final BlockPos center;
        final int floor;
        final LinkedHashMap<BlockPos, BlockState> original = new LinkedHashMap<>();
        final List<BlockPos> sensors = new ArrayList<>();
        final Map<BlockPos, SculkSensorPhase> phases = new HashMap<>();
        final List<String> heard = new CopyOnWriteArrayList<>();
        final List<String> sources = new CopyOnWriteArrayList<>();
        final List<String> leftovers = new CopyOnWriteArrayList<>();
        volatile @Nullable UUID drone;
        volatile boolean home;
        volatile boolean homeFlying;
        volatile @Nullable AutonomyEnd handover;
        volatile @Nullable AutonomyEnd reason;
        volatile long endTick = Long.MIN_VALUE;
        volatile long now;
        volatile int flightActivations;
        volatile int quietActivations;
        volatile double endFall = Double.NaN;
        volatile String state = "unbekannt";

        Rig(BlockPos center, int floor) {
            this.center = center;
            this.floor = floor;
        }

        Vec3 pad() {
            return new Vec3(this.center.getX() + PAD_DX + 0.5, this.floor + 1.0, this.center.getZ() + 0.5);
        }

        AABB box() {
            return new AABB(this.center.getX() - LENGTH, this.floor - 1, this.center.getZ() - WIDTH, this.center.getX() + LENGTH + 1,
                    this.floor + HEIGHT + 1, this.center.getZ() + WIDTH + 1);
        }

        boolean ended() {
            return this.endTick != Long.MIN_VALUE;
        }

        boolean watching() {
            return this.ended() && this.now >= this.endTick + TRAVEL_TICKS && this.now < this.endTick + TRAVEL_TICKS + QUIET_TICKS;
        }

        boolean watched() {
            return this.ended() && this.now >= this.endTick + TRAVEL_TICKS + QUIET_TICKS;
        }

        void restart(UUID drone, boolean home) {
            this.drone = drone;
            this.home = home;
            this.homeFlying = false;
            this.handover = null;
            this.reason = null;
            this.endTick = Long.MIN_VALUE;
            this.flightActivations = 0;
            this.quietActivations = 0;
            this.endFall = Double.NaN;
            this.heard.clear();
            this.sources.clear();
            this.leftovers.clear();
        }
    }

    static final class Route implements FlightAssist {
        final double x;
        final double y;
        final double z;
        final boolean home;
        boolean arrived;

        Route(double x, double y, double z, boolean home) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.home = home;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            output.label("SCULK");
            output.maxSpeed(4.0);
            output.holdAltitude(this.y);
            output.holdPosition(this.x, this.z);
            if (Math.hypot(state.x() - this.x, state.z() - this.z) < ARRIVED && Math.hypot(state.vx(), state.vz()) < SETTLED_SPEED) {
                this.arrived = true;
            }
            if (this.arrived && this.home) {
                output.requestReturnHome();
            } else if (this.arrived) {
                output.land();
            }
        }
    }

    private static void ended(Entity drone, UUID owner, Identifier id, AutonomyEnd reason) {
        Rig rig = RIG.get();
        if (rig == null || !MISSION.equals(id) || !drone.getUUID().equals(rig.drone) || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        if (rig.home && rig.handover == null) {
            rig.handover = reason;
            return;
        }
        rig.reason = reason;
        rig.endTick = level.getServer().getTickCount();
    }

    private static void sample(MinecraftServer server) {
        Rig rig = RIG.get();
        if (rig == null) {
            return;
        }
        ServerLevel level = server.overworld();
        rig.now = server.getTickCount();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, rig.box())) {
            mob.discard();
        }
        QuadcopterEntity drone = rig.drone == null || !(level.getEntity(rig.drone) instanceof QuadcopterEntity d) ? null : d;
        rig.state = describe(drone);
        if (rig.home && rig.handover != null && !rig.ended() && drone != null) {
            if (drone.returnPlan() != null || AutonomousReturns.flying(drone)) {
                rig.homeFlying = true;
            } else {
                rig.reason = rig.homeFlying && !drone.isArmed() && Math.abs(drone.getY() - rig.pad().y) < 0.3 ? AutonomyEnd.LANDED : rig.handover;
                rig.endTick = rig.now;
            }
        }
        if (rig.ended() && Double.isNaN(rig.endFall) && drone != null) {
            rig.endFall = drone.fallDistance;
        }
        boolean watching = rig.watching();
        for (BlockPos pos : rig.sensors) {
            if (!(level.getBlockEntity(pos) instanceof SculkSensorBlockEntity sensor)) {
                continue;
            }
            VibrationInfo info = sensor.getVibrationData().getCurrentVibration();
            if (watching && info != null && rig.sources.size() < LOG_LINES) {
                Entity source = info.uuid() == null ? null : level.getEntity(info.uuid());
                String line = info.gameEvent().getRegisteredName() + " von " + (source == null ? "unbekannt" : source.getType().toShortString()
                        + (source.getUUID().equals(rig.drone) ? " (Testdrohne)" : "")) + String.format(Locale.ROOT, " bei %.2f/%.2f/%.2f",
                        info.pos().x, info.pos().y, info.pos().z);
                if (!rig.sources.contains(line)) {
                    rig.sources.add(line);
                }
            }
            SculkSensorPhase phase = SculkSensorBlock.getPhase(sensor.getBlockState());
            SculkSensorPhase before = rig.phases.put(pos, phase);
            if (phase != SculkSensorPhase.ACTIVE || before == SculkSensorPhase.ACTIVE) {
                continue;
            }
            if (watching) {
                rig.quietActivations++;
                if (rig.heard.size() < LOG_LINES) {
                    rig.heard.add(String.format(Locale.ROOT, "%d Ticks nach dem Ende Sensor %s mit Frequenz %d", rig.now - rig.endTick,
                            pos.toShortString(), sensor.getLastVibrationFrequency()));
                }
            } else if (!rig.ended()) {
                rig.flightActivations++;
            }
        }
        if (watching && drone != null && rig.leftovers.size() < LOG_LINES && (drone.isArmed() || drone.motorOutput() != 0.0F
                || drone.motorOutputsPacked() != 0 || AddonAutonomy.flying(drone) || AutonomousReturns.flying(drone))) {
            rig.leftovers.add((rig.now - rig.endTick) + " Ticks nach dem Ende: " + rig.state);
        }
    }

    static String describe(@Nullable QuadcopterEntity drone) {
        if (drone == null) {
            return "weg";
        }
        return String.format(Locale.ROOT, "y=%.7f scharf=%s Motoren=%.3f Boden=%s Fallhöhe=%.4f Flug ohne Pilot=%s", drone.getY(), drone.isArmed(),
                drone.motorOutput(), drone.onGround(), drone.fallDistance, AddonAutonomy.flying(drone) || AutonomousReturns.flying(drone));
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void set(ServerLevel level, Rig rig, BlockPos pos, BlockState state) {
        rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void prepare(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x0 = player.getBlockX() + SITE_DX;
        int z0 = player.getBlockZ() + SITE_DZ;
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        Rig rig = new Rig(new BlockPos(x0, floor, z0), floor);
        BlockState wool = Blocks.WOOL.white().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        for (int dx = -LENGTH; dx <= LENGTH; dx++) {
            for (int dz = -WIDTH; dz <= WIDTH; dz++) {
                for (int dy = -1; dy <= HEIGHT; dy++) {
                    boolean wall = Math.abs(dx) == LENGTH || Math.abs(dz) == WIDTH;
                    BlockState state = wall || dy < 0 || dy == HEIGHT ? wool : dy == 0 ? stone : air;
                    if (dy == HEIGHT && !wall && Math.floorMod(dx + dz, 3) == 0) {
                        state = light;
                    }
                    set(level, rig, new BlockPos(x0 + dx, floor + dy, z0 + dz), state);
                }
            }
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, rig.box())) {
            mob.discard();
        }
        for (int dx = -3; dx <= LENGTH - 2; dx++) {
            for (int dz = -WIDTH + 2; dz <= WIDTH - 2; dz++) {
                if (Math.floorMod(dx + dz, 2) != 0) {
                    continue;
                }
                BlockPos pos = new BlockPos(x0 + dx, floor + 1, z0 + dz);
                set(level, rig, pos, Blocks.SCULK_SENSOR.defaultBlockState());
                rig.sensors.add(pos);
            }
        }
        DroneSculkSelfTests.rule().ifPresent(spec -> {
            if (WorldRules.get(server, spec) != 1) {
                throw new IllegalStateException("Welt-Regel " + DroneSculkSelfTests.RULE + " ist aus");
            }
        });
        RIG.set(rig);
    }

    private static int stage(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(STAGE, 0);
    }

    private static void stage(SelfTest.StepContext ctx, int next) {
        ctx.state().put(STAGE, next);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static @Nullable StepResult pending(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Serverauftrag: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return null;
    }

    private static void server(SelfTest.StepContext ctx, Consumer<MinecraftServer> action) {
        ctx.state().put(FUTURE, ctx.onServer(action));
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (stage(ctx) == 0) {
            server(ctx, DroneSculkMissionSelfTests::prepare);
            stage(ctx, 1);
            return StepResult.pending();
        }
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.fail("kein Aufbau");
        }
        ctx.log("Wollkammer bei " + rig.center.toShortString() + " mit " + rig.sensors.size() + " Sculk-Sensoren, Startfläche "
                + BlockPos.containing(rig.pad()).toShortString());
        return StepResult.check(!rig.sensors.isEmpty(), "keine Sensoren gesetzt");
    }

    private static StepResult missionStep(SelfTest.StepContext ctx, DroneBuild build, int targetDx, int targetDz, double offsetX, double offsetZ,
                                          boolean home) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.fail("kein Aufbau");
        }
        String name = build.frame().name();
        switch (stage(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    ServerLevel level = server.overworld();
                    if (rig.drone != null && level.getEntity(rig.drone) instanceof QuadcopterEntity old) {
                        old.discard();
                    }
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                    stack.set(CoreComponents.DRONE_BUILD.get(), build);
                    drone.loadFromStack(stack);
                    Vec3 pad = rig.pad();
                    drone.snapTo(pad.x, pad.y, pad.z, -90.0F, 0.0F);
                    drone.setBatteryCharge(1.0F);
                    if (!level.addFreshEntity(drone)) {
                        throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
                    }
                    rig.restart(drone.getUUID(), home);
                });
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < SPAWN_TICKS) {
                    return StepResult.pending();
                }
                double x = rig.center.getX() + targetDx + offsetX;
                double z = rig.center.getZ() + targetDz + offsetZ;
                double y = rig.floor + 1.0 + ALTITUDE;
                server(ctx, server -> {
                    if (rig.drone == null || !(server.overworld().getEntity(rig.drone) instanceof QuadcopterEntity drone)) {
                        throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
                    }
                    AutonomyResult result = PropwashApi.drones().autonomy().start(drone, player(server), new Route(x, y, z, home),
                            AutonomyOptions.of(MISSION).label("SCULK").takeoff(ALTITUDE));
                    if (result != AutonomyResult.STARTED) {
                        throw new IllegalStateException("Mission startet nicht: " + result);
                    }
                });
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (!rig.ended()) {
                    return since(ctx) > FLIGHT_LIMIT ? StepResult.fail(name + ": Mission endet nicht, Drohne " + rig.state) : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "%s: %s nach %d Ticks mit %s beendet, %d Sensor-Auslösungen im Flug, Drohne %s", name,
                        home ? "Heimflug nach " + rig.handover : "Mission", since(ctx), rig.reason, rig.flightActivations, rig.state));
                stage(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                if (!rig.watched()) {
                    return StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "%s: Fallhöhe am Ende %.3g m, %d bis %d Ticks nach dem Ende %d Sensor-Auslösungen, Drohne %s", name,
                        rig.endFall, TRAVEL_TICKS, TRAVEL_TICKS + QUIET_TICKS, rig.quietActivations, rig.state));
                rig.heard.forEach(line -> ctx.log("  gehört: " + line));
                rig.sources.forEach(line -> ctx.log("  Quelle: " + line));
                rig.leftovers.forEach(line -> ctx.log("  noch im Flug: " + line));
                List<String> problems = new ArrayList<>();
                if (home && rig.handover != AutonomyEnd.STOPPED) {
                    problems.add("Übergabe an den Heimflug mit " + rig.handover + " statt STOPPED");
                }
                if (rig.reason != AutonomyEnd.LANDED) {
                    problems.add((home ? "Heimflug" : "Mission") + " endet mit " + rig.reason + " statt LANDED");
                }
                if (rig.flightActivations == 0) {
                    problems.add("im Flug über dem Feld hat kein Sensor ausgelöst");
                }
                if (!rig.leftovers.isEmpty()) {
                    problems.add("nach der Landung scharf, Motoren an oder Flug ohne Pilot: " + rig.leftovers.getFirst());
                }
                if (rig.quietActivations > 0) {
                    problems.add(rig.quietActivations + " Sensor-Auslösungen nach der Landung, " + (rig.heard.isEmpty() ? ""
                            : "zuerst " + rig.heard.getFirst() + ", ") + "Quellen " + rig.sources);
                }
                if (!(rig.endFall == 0.0)) {
                    problems.add("nach der Landung bleibt eine Fallhöhe von " + rig.endFall + " m aus dem Flug stehen, beim nächsten Aufsetzen meldet "
                            + "Vanilla daraus hit_ground");
                }
                return problems.isEmpty() ? StepResult.pass() : StepResult.fail(name + ": " + String.join("; ", problems));
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage(ctx));
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (stage(ctx) == 0) {
            server(ctx, server -> {
                ServerLevel level = server.overworld();
                if (rig.drone != null && level.getEntity(rig.drone) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
                RIG.compareAndSet(rig, null);
                for (Map.Entry<BlockPos, BlockState> entry : rig.original.reversed().entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
                }
            });
            stage(ctx, 1);
            return StepResult.pending();
        }
        return StepResult.pass();
    }
}
