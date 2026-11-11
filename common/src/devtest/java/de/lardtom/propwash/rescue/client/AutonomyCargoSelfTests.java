package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleFollow;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AddonAutonomy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class AutonomyCargoSelfTests {
    public static final String STEP = AutonomySelfTests.GROUP + " – Fracht ohne Pilot";
    public static final Identifier MISSION = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "cargo");
    private static final int OFFSET_X = 6;
    private static final int A_DX = 4;
    private static final int B_DX = 10;
    private static final int PAD_SIDE = 3;
    private static final int PAD_BACK = 3;
    private static final int PAD_FRONT = 14;
    private static final int HEIGHT = 7;
    private static final int COBBLE = 4;
    private static final double COBBLE_KG = 0.2;
    private static final double MASS_TOLERANCE = 0.005;
    private static final double TAKEOFF = 1.5;
    private static final double LOW = 0.5;
    private static final double CARRY = 1.5;
    private static final double ARRIVED = 0.3;
    private static final double LEVEL = 0.2;
    private static final double SETTLED_SPEED = 0.3;
    private static final int SETTLED_TICKS = 20;
    private static final int HOVER_TICKS = 30;
    private static final double LANDED_RADIUS = 1.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String SETTLED_SINCE = "settled";
    private static final String BASE_MASS = "baseMass";
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicReference<Courier> COURIER = new AtomicReference<>();
    private static final AtomicReference<Probe> PROBE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();

    private record Probe(UUID id, boolean present, double x, double y, double z, double speed, boolean armed, String phase, String ended,
                         int cargo, float cargoGrams, double massKg, double height, int atA, int nearB, boolean nearBLanded) {
        String describe() {
            return this.present ? String.format(Locale.ROOT, "Drohne bei %.2f/%.2f/%.2f, %.2f m über dem Boden, %.2f m/s, scharf %s, Phase '%s', "
                            + "Ende %s, Fracht %d Items (%.0f g), Flugmodell %.3f kg, bei A %d, bei B %d Bruchstein%s", this.x, this.y, this.z, this.height,
                    this.speed, this.armed, this.phase, this.ended, this.cargo, this.cargoGrams, this.massKg, this.atA, this.nearB,
                    this.nearBLanded ? " am Boden" : "") : "Drohne nicht auf dem Server";
        }
    }

    static final class Courier implements FlightAssist {
        volatile double x;
        volatile double z;
        volatile double height;
        volatile boolean land;
        volatile double massKg = Double.NaN;

        Courier(double x, double z, double height) {
            this.x = x;
            this.z = z;
            this.height = height;
        }

        void target(double x, double z, double height) {
            this.x = x;
            this.z = z;
            this.height = height;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            this.massKg = state.massKg();
            output.holdPosition(this.x, this.z);
            output.maxSpeed(2.0);
            output.holdAltitude(state.y() + this.height - state.heightAboveGround());
            output.label("CARGO");
            if (this.land) {
                output.land();
            }
        }
    }

    private AutonomyCargoSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP + ": Add-on-Flug mit Greifer (Freestyle 5″) schwebt 0,5 m über 4 Bruchstein an Wegpunkt A und nimmt erst nach "
                + "grabCargo auf (Flugmodell 0,2 kg schwerer), trägt sie zu Wegpunkt B, releaseCargo legt sie dort ab, danach land()", 2400,
                AutonomyCargoSelfTests::step);
    }

    static DroneBuild build() {
        return DroneBuild.FREESTYLE.withoutAccessories().withAccessory(AccessoryType.GRIPPER);
    }

    private static ServerPlayer player(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server) {
        UUID id = DRONE.get();
        if (id == null) {
            return null;
        }
        return player(server).level().getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static QuadcopterEntity require(MinecraftServer server) {
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt");
        }
        return drone;
    }

    private static Vec3 point(BlockPos base, int dx) {
        return new Vec3(base.getX() + dx + 0.5, base.getY(), base.getZ() + 0.5);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state)) {
            return;
        }
        ORIGINAL.putIfAbsent(pos.immutable(), old);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static AABB arena(BlockPos base) {
        return new AABB(base.getX() - PAD_BACK, base.getY() - 1, base.getZ() - PAD_SIDE, base.getX() + PAD_FRONT + 1, base.getY() + HEIGHT,
                base.getZ() + PAD_SIDE + 1);
    }

    private static void setup(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        cleanup(server);
        BlockPos base = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ());
        for (int dx = -PAD_BACK; dx <= PAD_FRONT; dx++) {
            for (int dz = -PAD_SIDE; dz <= PAD_SIDE; dz++) {
                level.getChunk((base.getX() + dx) >> 4, (base.getZ() + dz) >> 4);
                set(level, base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < HEIGHT; dy++) {
                    set(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BASE.set(base);
        for (ItemEntity stray : level.getEntitiesOfClass(ItemEntity.class, arena(base))) {
            stray.discard();
        }
        Vec3 a = point(base, A_DX);
        ItemEntity cobble = new ItemEntity(level, a.x, a.y, a.z, new ItemStack(Items.COBBLESTONE, COBBLE), 0.0, 0.0, 0.0);
        cobble.setNoPickUpDelay();
        level.addFreshEntity(cobble);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build());
        drone.loadFromStack(stack);
        Vec3 start = point(base, 0).add(0.0, 0.1, 0.0);
        drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(start, new Quaternionf());
        DRONE.set(drone.getUUID());
        ExampleFollow.ENDED.removeIf(ended -> ended.drone().equals(drone.getUUID()));
    }

    private static void cleanup(MinecraftServer server) {
        QuadcopterEntity drone = drone(server);
        if (drone != null) {
            PropwashApi.drones().autonomy().stop(drone);
            drone.setReturnPlan(null);
            drone.discard();
        }
        DRONE.set(null);
        COURIER.set(null);
        ServerLevel level = player(server).level();
        BlockPos base = BASE.getAndSet(null);
        if (base != null) {
            for (ItemEntity stray : level.getEntitiesOfClass(ItemEntity.class, arena(base))) {
                stray.discard();
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : ORIGINAL.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        ORIGINAL.clear();
    }

    private static int cobbleNear(ServerLevel level, Vec3 point, double radius, boolean grounded) {
        int count = 0;
        AABB box = new AABB(point.x - radius, point.y - 0.5, point.z - radius, point.x + radius, point.y + HEIGHT, point.z + radius);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, item -> item.isAlive() && item.getItem().is(Items.COBBLESTONE))) {
            if (!grounded || item.onGround() && Math.abs(item.getY() - point.y) < 0.3) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }

    private static void poll(SelfTest.StepContext ctx) {
        UUID id = DRONE.get();
        BlockPos base = BASE.get();
        if (id == null || base == null) {
            return;
        }
        ctx.onServer(server -> {
            QuadcopterEntity drone = drone(server);
            if (drone == null) {
                PROBE.set(new Probe(id, false, 0, 0, 0, 0, false, "", "-", 0, 0.0F, Double.NaN, Double.NaN, 0, 0, false));
                return;
            }
            ServerLevel level = (ServerLevel) drone.level();
            Courier courier = COURIER.get();
            String ended = ExampleFollow.lastEnded(id).map(e -> e.id().getPath() + "/" + e.reason()).orElse("-");
            Vec3 b = point(base, B_DX);
            int nearB = cobbleNear(level, b, LANDED_RADIUS, false);
            PROBE.set(new Probe(id, true, drone.getX(), drone.getY(), drone.getZ(), drone.getDeltaMovement().length() * TICKS_PER_SECOND,
                    drone.isArmed(), AddonAutonomy.phase(drone).orElse(""), ended, drone.cargoHold().itemCount(), drone.cargoGrams(),
                    courier == null ? Double.NaN : courier.massKg, drone.getY() - base.getY(),
                    cobbleNear(level, point(base, A_DX), 0.75, false), nearB, nearB > 0 && cobbleNear(level, b, LANDED_RADIUS, true) == nearB));
        });
    }

    private static @Nullable Probe probe() {
        Probe probe = PROBE.get();
        return probe != null && probe.id().equals(DRONE.get()) ? probe : null;
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
        ctx.state().remove(SETTLED_SINCE);
        return StepResult.pending();
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
            return fail(ctx, what + ": " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        String result = RESULT.getAndSet(null);
        return result == null ? null : fail(ctx, what + ": " + result);
    }

    private static StepResult fail(SelfTest.StepContext ctx, String message) {
        ctx.onServer(AutonomyCargoSelfTests::cleanup);
        return StepResult.fail(message);
    }

    private static void expect(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            String miss = what + ": erwartet " + expected + ", war " + actual;
            RESULT.accumulateAndGet(miss, (old, add) -> old == null ? add : old + "; " + add);
        }
    }

    private static boolean settledAt(SelfTest.StepContext ctx, Probe probe, Vec3 target, double height) {
        boolean there = Math.hypot(probe.x() - target.x, probe.z() - target.z) <= ARRIVED && Math.abs(probe.height() - height) <= LEVEL
                && probe.speed() < SETTLED_SPEED;
        if (!there) {
            ctx.state().remove(SETTLED_SINCE);
            return false;
        }
        int first = (int) ctx.state().computeIfAbsent(SETTLED_SINCE, key -> ctx.tick());
        return ctx.tick() - first >= SETTLED_TICKS;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Prüfplatz, Drohne mit Greifer und 4 Bruchstein an A", AutonomyCargoSelfTests::setup);
            return wait != null ? wait : next(ctx, 1);
        }
        if (phase == 9) {
            StepResult wait = server(ctx, "Aufräumen", AutonomyCargoSelfTests::cleanup);
            return wait != null ? wait : StepResult.pass();
        }
        BlockPos base = BASE.get();
        if (base == null) {
            return fail(ctx, "Prüfplatz fehlt");
        }
        Vec3 a = point(base, A_DX);
        Vec3 b = point(base, B_DX);
        poll(ctx);
        Probe probe = probe();
        if (probe == null) {
            return since(ctx) > 200 ? fail(ctx, "kein Serverstand") : StepResult.pending();
        }
        if (!probe.present()) {
            return fail(ctx, probe.describe());
        }
        if (phase == 1) {
            if (since(ctx) < 40 || probe.speed() > 0.01) {
                return since(ctx) > 400 ? fail(ctx, "kommt nicht zur Ruhe: " + probe.describe()) : StepResult.pending();
            }
            StepResult wait = server(ctx, "Start mit takeoff", server -> {
                QuadcopterEntity drone = require(server);
                Courier courier = new Courier(a.x, a.z, LOW);
                COURIER.set(courier);
                AutonomyResult result = PropwashApi.drones().autonomy().start(drone, player(server), courier,
                        AutonomyOptions.of(MISSION).label("CARGO").takeoff(TAKEOFF));
                expect("Start", AutonomyResult.STARTED, result);
            });
            if (wait != null) {
                return wait;
            }
            ctx.log("Add-on-Flug ohne Pilot gestartet: " + probe.describe());
            return next(ctx, 2);
        }
        if (phase == 2) {
            if (since(ctx) < 40 || !"ASSIST".equals(probe.phase()) || !settledAt(ctx, probe, a, LOW)) {
                return since(ctx) > 900 ? fail(ctx, "erreicht Wegpunkt A in 0,5 m Höhe nicht: " + probe.describe()) : StepResult.pending();
            }
            ctx.log("über Wegpunkt A: " + probe.describe());
            if (probe.cargo() != 0) {
                return fail(ctx, "nimmt im Autopilot ohne grabCargo auf: " + probe.describe());
            }
            ctx.state().put(BASE_MASS, probe.massKg());
            return next(ctx, 3);
        }
        if (phase == 3) {
            if (since(ctx) < HOVER_TICKS) {
                if (probe.cargo() != 0) {
                    return fail(ctx, "nimmt im Autopilot ohne grabCargo auf: " + probe.describe());
                }
                return StepResult.pending();
            }
            StepResult wait = server(ctx, "grabCargo an Wegpunkt A", server -> {
                QuadcopterEntity drone = require(server);
                boolean accepted = PropwashApi.accessories().grabCargo(drone, player(server));
                expect("grabCargo angenommen", true, accepted);
            });
            if (wait != null) {
                return wait;
            }
            ctx.log(String.format(Locale.ROOT, "%d Ticks über A ohne grabCargo: keine Aufnahme; grabCargo angenommen", HOVER_TICKS));
            return next(ctx, 4);
        }
        double baseMass = (double) ctx.state().get(BASE_MASS);
        if (phase == 4) {
            if (probe.cargo() != COBBLE || Math.abs(probe.massKg() - baseMass - COBBLE_KG) > MASS_TOLERANCE) {
                return since(ctx) > 120 ? fail(ctx, String.format(Locale.ROOT, "nach grabCargo nicht %d Bruchstein und %.3f kg im Flugmodell "
                        + "(vorher %.3f kg): %s", COBBLE, baseMass + COBBLE_KG, baseMass, probe.describe())) : StepResult.pending();
            }
            if (probe.atA() != 0) {
                return fail(ctx, "Bruchstein liegt nach der Aufnahme noch an A: " + probe.describe());
            }
            StepResult wait = server(ctx, "Fracht lesen", server -> {
                List<ItemStack> cargo = PropwashApi.accessories().cargo(require(server));
                expect("cargo()", COBBLE + " Bruchstein", cargo.size() == 1 && cargo.getFirst().is(Items.COBBLESTONE)
                        ? cargo.getFirst().getCount() + " Bruchstein" : cargo.toString());
            });
            if (wait != null) {
                return wait;
            }
            ctx.log(String.format(Locale.ROOT, "an A aufgenommen, Flugmodell %.3f → %.3f kg: %s", baseMass, probe.massKg(), probe.describe()));
            COURIER.get().target(b.x, b.z, CARRY);
            return next(ctx, 5);
        }
        if (phase == 5) {
            if (!settledAt(ctx, probe, b, CARRY)) {
                return since(ctx) > 900 ? fail(ctx, "erreicht Wegpunkt B in 1,5 m Höhe nicht: " + probe.describe()) : StepResult.pending();
            }
            if (probe.cargo() != COBBLE) {
                return fail(ctx, "Fracht auf dem Weg verloren: " + probe.describe());
            }
            StepResult wait = server(ctx, "releaseCargo an Wegpunkt B", server -> {
                QuadcopterEntity drone = require(server);
                expect("releaseCargo", COBBLE, PropwashApi.accessories().releaseCargo(drone, player(server)));
                expect("cargo() nach releaseCargo", List.of(), PropwashApi.accessories().cargo(drone));
            });
            if (wait != null) {
                return wait;
            }
            ctx.log("über Wegpunkt B mit Fracht, releaseCargo: " + probe.describe());
            return next(ctx, 6);
        }
        if (phase == 6) {
            if (probe.cargo() != 0 || probe.nearB() != COBBLE || !probe.nearBLanded() || Math.abs(probe.massKg() - baseMass) > MASS_TOLERANCE
                    || since(ctx) < 40) {
                return since(ctx) > 200 ? fail(ctx, "Fracht liegt nicht bei B am Boden oder das Flugmodell bleibt schwer: " + probe.describe())
                        : StepResult.pending();
            }
            ctx.log(String.format(Locale.ROOT, "bei B abgelegt, 2 s ohne neuen Griff, Flugmodell wieder %.3f kg: %s", probe.massKg(),
                    probe.describe()));
            COURIER.get().land = true;
            return next(ctx, 7);
        }
        if (phase == 7) {
            if (probe.armed() || !probe.phase().isEmpty()) {
                return since(ctx) > 900 ? fail(ctx, "landet nach land() nicht: " + probe.describe()) : StepResult.pending();
            }
            if (!probe.ended().equals(MISSION.getPath() + "/" + AutonomyEnd.LANDED)) {
                return fail(ctx, "Ende nicht LANDED: " + probe.describe());
            }
            if (probe.cargo() != 0 || probe.nearB() != COBBLE) {
                return fail(ctx, "Fracht nach der Landung verändert: " + probe.describe());
            }
            ctx.log("gelandet: " + probe.describe());
            return next(ctx, 9);
        }
        return fail(ctx, "unbekannte Phase " + phase);
    }
}
