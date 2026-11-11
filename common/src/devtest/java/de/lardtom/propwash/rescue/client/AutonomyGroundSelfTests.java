package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAccessories;
import de.lardtom.propwash.dev.example.ExampleFollow;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.rescue.GroundContact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class AutonomyGroundSelfTests {
    public static final String STEP = AutonomySelfTests.GROUP + " – Start aus dem Stand auf hohem Zubehör";
    private static final int OFFSET_X = 6;
    private static final int PAD = 3;
    private static final int HEIGHT = 9;
    private static final double DROP = 0.3;
    private static final double TALL = 0.08;
    private static final double HOVER = 0.05;
    private static final double TAKEOFF = 5.0;
    private static final double TAKEOFF_TOLERANCE = 0.6;
    private static final double STILL = 0.01;
    private static final int STILL_TICKS = 20;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String STILL_SINCE = "still";
    private static final String REST_Y = "restY";
    private static final String TOP = "top";
    private static final String ASSIST_AT = "assistAt";
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<Vec3> SPOT = new AtomicReference<>();
    private static final AtomicReference<Probe> PROBE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();

    private record Probe(UUID id, boolean present, double y, double hitbox, double contact, double speed, boolean armed, String phase,
                         String ended) {
        String describe() {
            return this.present ? String.format(Locale.ROOT, "Fuß %.3f, Hitbox %.3f m und Kollisionsform %.3f m über dem Boden, %.3f m/s, "
                    + "scharf %s, Phase '%s', Ende %s", this.y, this.hitbox, this.contact, this.speed, this.armed, this.phase, this.ended)
                    : "Drohne nicht auf dem Server";
        }
    }

    private AutonomyGroundSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP + " (Freestyle 5″ mit Speer unten, Hitbox über 8 cm über dem Boden): 5 cm darüber schwebend NOT_ON_GROUND, "
                + "liegend ohne takeoff NO_TAKEOFF, mit takeoff STARTED auf 5 m, nach land() liegt sie wieder auf dem Speer und gilt als am Boden",
                2400, AutonomyGroundSelfTests::step);
    }

    static DroneBuild build() {
        AccessoryType spear = AccessoryType.byId(ExampleAccessories.SPEAR)
                .orElseThrow(() -> new IllegalStateException("Beispiel-Zubehör fehlt: " + ExampleAccessories.SPEAR));
        return DroneBuild.FREESTYLE.withoutAccessories().withAccessory(spear);
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

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state)) {
            return;
        }
        ORIGINAL.putIfAbsent(pos.immutable(), old);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void setup(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        cleanup(server);
        BlockPos base = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ());
        for (int dx = -PAD; dx <= PAD; dx++) {
            for (int dz = -PAD; dz <= PAD; dz++) {
                level.getChunk((base.getX() + dx) >> 4, (base.getZ() + dz) >> 4);
                set(level, base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < HEIGHT; dy++) {
                    set(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        Vec3 spot = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        SPOT.set(spot);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build());
        drone.loadFromStack(stack);
        Vec3 start = spot.add(0.0, DROP, 0.0);
        drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(start, new Quaternionf());
        DRONE.set(drone.getUUID());
        ExampleFollow.ENDED.removeIf(ended -> ended.drone().equals(drone.getUUID()));
    }

    private static void cleanup(MinecraftServer server) {
        QuadcopterEntity drone = drone(server);
        if (drone != null) {
            ExampleFollow.stop(drone);
            drone.setReturnPlan(null);
            drone.discard();
        }
        DRONE.set(null);
        ServerLevel level = player(server).level();
        for (Map.Entry<BlockPos, BlockState> entry : ORIGINAL.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        ORIGINAL.clear();
    }

    private static void poll(SelfTest.StepContext ctx) {
        UUID id = DRONE.get();
        Vec3 spot = SPOT.get();
        if (id == null || spot == null) {
            return;
        }
        ctx.onServer(server -> {
            QuadcopterEntity drone = drone(server);
            if (drone == null) {
                PROBE.set(new Probe(id, false, 0.0, 0.0, 0.0, 0.0, false, "", "-"));
                return;
            }
            String ended = ExampleFollow.lastEnded(id).map(e -> e.id().getPath() + "/" + e.reason()).orElse("-");
            PROBE.set(new Probe(id, true, drone.getY() - spot.y, drone.supportGap(GroundContact.PROBE), GroundContact.gap(drone, GroundContact.PROBE),
                    drone.getDeltaMovement().length() * TICKS_PER_SECOND, drone.isArmed(), AddonAutonomy.phase(drone).orElse(""), ended));
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
        ctx.state().remove(STILL_SINCE);
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
        ctx.onServer(AutonomyGroundSelfTests::cleanup);
        return StepResult.fail(message);
    }

    private static boolean still(SelfTest.StepContext ctx, Probe probe) {
        if (!(probe.speed() < STILL)) {
            ctx.state().remove(STILL_SINCE);
            return false;
        }
        int first = (int) ctx.state().computeIfAbsent(STILL_SINCE, key -> ctx.tick());
        return ctx.tick() - first >= STILL_TICKS;
    }

    private static void expect(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            String miss = what + ": erwartet " + expected + ", war " + actual;
            RESULT.accumulateAndGet(miss, (old, add) -> old == null ? add : old + "; " + add);
        }
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult wait = server(ctx, "Prüfplatz und Drohne", AutonomyGroundSelfTests::setup);
            return wait != null ? wait : next(ctx, 1);
        }
        if (phase == 6) {
            StepResult wait = server(ctx, "Aufräumen", AutonomyGroundSelfTests::cleanup);
            return wait != null ? wait : StepResult.pass();
        }
        poll(ctx);
        Probe probe = probe();
        if (probe == null) {
            return since(ctx) > 200 ? fail(ctx, "kein Serverstand") : StepResult.pending();
        }
        if (!probe.present()) {
            return fail(ctx, probe.describe());
        }
        if (phase == 1) {
            if (since(ctx) < 40 || !still(ctx, probe)) {
                return since(ctx) > 400 ? fail(ctx, "kommt nicht zur Ruhe: " + probe.describe()) : StepResult.pending();
            }
            ctx.log("liegt auf dem Speer: " + probe.describe());
            if (!(probe.hitbox() >= TALL)) {
                return fail(ctx, "Speer trägt die Hitbox nicht 8 cm hoch, Prüfung ohne Wirkung: " + probe.describe());
            }
            if (!(probe.contact() < GroundContact.TOUCH)) {
                return fail(ctx, "Kollisionsform berührt den Boden nicht: " + probe.describe());
            }
            ctx.state().put(REST_Y, probe.y());
            return next(ctx, 2);
        }
        if (phase == 2) {
            StepResult wait = server(ctx, "Start aus dem Stand", server -> {
                QuadcopterEntity drone = require(server);
                ServerPlayer player = player(server);
                Vec3 rest = drone.position();
                drone.setPos(rest.x, rest.y + HOVER, rest.z);
                Optional<AutonomyNotReady> hovering = ExampleFollow.notReady(drone, TAKEOFF);
                AutonomyResult hoverStart = ExampleFollow.takeoff(drone, player, TAKEOFF);
                drone.setPos(rest.x, rest.y, rest.z);
                Optional<AutonomyNotReady> lying = ExampleFollow.notReady(drone, 0.0);
                AutonomyResult hold = ExampleFollow.hold(drone, player);
                Optional<AutonomyNotReady> ready = ExampleFollow.notReady(drone, TAKEOFF);
                AutonomyResult takeoff = ExampleFollow.takeoff(drone, player, TAKEOFF);
                expect("5 cm über dem Boden schwebend, Grund", Optional.of(AutonomyNotReady.NOT_ON_GROUND), hovering);
                expect("5 cm über dem Boden schwebend, Start", AutonomyResult.NOT_READY, hoverStart);
                expect("liegend ohne takeoff, Grund", Optional.of(AutonomyNotReady.NO_TAKEOFF), lying);
                expect("liegend ohne takeoff, Start", AutonomyResult.NOT_READY, hold);
                expect("liegend mit takeoff, Grund", Optional.empty(), ready);
                expect("liegend mit takeoff, Start", AutonomyResult.STARTED, takeoff);
            });
            if (wait != null) {
                return wait;
            }
            ctx.log("5 cm darüber NOT_ON_GROUND und NOT_READY, liegend ohne takeoff NO_TAKEOFF und NOT_READY, mit takeoff STARTED");
            ctx.state().put(TOP, 0.0);
            return next(ctx, 3);
        }
        double restY = (double) ctx.state().get(REST_Y);
        if (phase == 3) {
            double height = probe.y() - restY;
            ctx.state().put(TOP, Math.max((double) ctx.state().get(TOP), height));
            if (!"ASSIST".equals(probe.phase())) {
                return since(ctx) > 500 ? fail(ctx, "Steigen endet nicht: " + probe.describe()) : StepResult.pending();
            }
            int assistAt = (int) ctx.state().computeIfAbsent(ASSIST_AT, key -> since(ctx));
            if (since(ctx) < assistAt + 20) {
                return StepResult.pending();
            }
            double top = (double) ctx.state().get(TOP);
            ctx.log(String.format(Locale.ROOT, "aus dem Stand gestiegen: %.2f m über dem Startpunkt, %s", top, probe.describe()));
            if (Math.abs(top - TAKEOFF) > TAKEOFF_TOLERANCE) {
                return fail(ctx, String.format(Locale.ROOT, "Starthöhe %.2f statt %.1f m: %s", top, TAKEOFF, probe.describe()));
            }
            ctx.onServer(server -> ExampleFollow.land(require(server)));
            return next(ctx, 4);
        }
        if (phase == 4) {
            if (probe.armed() || !probe.phase().isEmpty() || !still(ctx, probe)) {
                return since(ctx) > 900 ? fail(ctx, "landet nach land() nicht: " + probe.describe()) : StepResult.pending();
            }
            if (!probe.ended().equals("hold/" + AutonomyEnd.LANDED)) {
                return fail(ctx, "Ende nicht LANDED: " + probe.describe());
            }
            ctx.log("nach land() gelandet: " + probe.describe());
            if (Math.abs(probe.y() - restY) > 0.02 || !(probe.contact() < GroundContact.TOUCH)) {
                return fail(ctx, "liegt nach der Landung nicht wieder auf dem Speer: " + probe.describe() + String.format(Locale.ROOT,
                        ", vor dem Start Fuß %.3f", restY));
            }
            return next(ctx, 5);
        }
        if (phase == 5) {
            StepResult wait = server(ctx, "wieder am Boden", server -> {
                QuadcopterEntity drone = require(server);
                expect("nach der Landung mit takeoff, Grund", Optional.empty(), ExampleFollow.notReady(drone, TAKEOFF));
                expect("nach der Landung ohne takeoff, Grund", Optional.of(AutonomyNotReady.NO_TAKEOFF), ExampleFollow.notReady(drone, 0.0));
            });
            if (wait != null) {
                return wait;
            }
            ctx.log("nach der Landung wieder am Boden: mit takeoff startbereit, ohne takeoff NO_TAKEOFF");
            return next(ctx, 6);
        }
        return fail(ctx, "unbekannte Phase " + phase);
    }
}
