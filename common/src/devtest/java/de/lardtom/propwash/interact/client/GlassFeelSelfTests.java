package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvJoltProbe;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.BreakMath;
import de.lardtom.propwash.interact.BreakRules;
import de.lardtom.propwash.interact.DroneBreaks;
import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.BreakReport;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.ImpactJolt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GlassFeelSelfTests {
    public static final String GROUP = "glasbruch: ";
    public static final String SHOT_DIR = "glass_feel/";
    private static final double WALL_AHEAD = 4.0;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 5;
    private static final int HIT_ROW = 2;
    private static final double FLIGHT_HEIGHT = 2.4;
    private static final double HOLD_GAIN = 0.3;
    private static final double HOLD_DAMPING = 0.1;
    private static final int MEASURE_TICKS = 2;
    private static final int RECOVER_TICKS = 50;
    private static final double EDGE_OFFSET = 1.0 / 16.0 + 0.05;
    private static final String BASE = "gf_";
    private static final List<BlockPos> BROKEN = new CopyOnWriteArrayList<>();
    private static final List<BreakRules.Outcome> DENIED = new CopyOnWriteArrayList<>();
    private static final Map<String, Result> RESULTS = new LinkedHashMap<>();
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static volatile @Nullable Vec3 serverDronePos;

    private static final Scenario PANE_SLOW = new Scenario("pane15", "Scheibe mit 15 m/s, 30° geneigt: harter Stoß kurz über dem Bruchtempo",
            Wall.PANE, 15.0, 30.0, 100, false, null);
    private static final Scenario PANE = new Scenario("pane25", "Scheibe mit 25 m/s, 35° geneigt: Tempo weg, Nase kippt, Kamera schlägt aus",
            Wall.PANE, 25.0, 35.0, 100, false, "glass_break_fpv_pane");
    private static final Scenario PANE_FAST = new Scenario("pane38", "Scheibe mit 38 m/s, 45° geneigt: kleinerer Anteil, gleicher Schlag",
            Wall.PANE, 38.0, 45.0, 100, false, null);
    private static final Scenario BLOCK = new Scenario("block25", "Glasblock mit 25 m/s, 35° geneigt: zu hart, die Drohne stürzt hindurch ab",
            Wall.BLOCK, 25.0, 35.0, 100, true, null);
    private static final Scenario BLOCK_FAST = new Scenario("block38", "Glasblock mit 38 m/s, 45° geneigt: durch, aber mit grobem Stoß",
            Wall.BLOCK, 38.0, 45.0, 100, false, "glass_break_fpv_block");
    private static final Scenario PANE_OFF = new Scenario("pane25_0", "Regel 0 %: Scheibe mit 25 m/s kostet nur die Bruchenergie, die Lage bleibt",
            Wall.PANE, 25.0, 35.0, 0, false, null);
    private static final Scenario PANE_DOUBLE = new Scenario("pane25_200", "Regel 200 %: Scheibe mit 25 m/s kippt die Drohne stärker",
            Wall.PANE, 25.0, 35.0, 200, false, null);
    private static final Scenario HALF_RIGHT = new Scenario("half_right", "Glas nur rechts der Drohne: der Stoß dreht sie um die Hochachse",
            Wall.PANE_RIGHT, 25.0, 0.0, 100, false, null);
    private static final Scenario HALF_LEFT = new Scenario("half_left", "Glas nur links der Drohne: der Stoß dreht sie andersherum",
            Wall.PANE_LEFT, 25.0, 0.0, 100, false, null);
    private static final List<Scenario> SCENARIOS = List.of(PANE_SLOW, PANE, PANE_FAST, BLOCK, BLOCK_FAST, PANE_OFF, PANE_DOUBLE, HALF_RIGHT, HALF_LEFT);

    private static final String RETRY = "retry";
    private static final String ATTEMPT = "attempt";
    private static final int MAX_ATTEMPTS = 3;
    private static final double MISS_DRIFT = 0.03;
    private static final double LATERAL_TOLERANCE = 0.008;
    private static final double LATERAL_SPEED = 0.2;
    private static final double LATERAL_GAIN = 4.0;
    private static final int SKY = 40;
    private static final double CLEARED_AHEAD = 15.0;
    private static final List<Approach> APPROACHES = List.of(
            new Approach("far30a", "Anflug aus 51 Blöcken mit 36 m/s bei 30 Bildern/s: Scheibe früh freigegeben, kein Wandtreffer", 36.5, 47.4, 30, false),
            new Approach("far30b", "Anflug aus 52 Blöcken mit 36 m/s bei 30 Bildern/s: Scheibe früh freigegeben, kein Wandtreffer", 36.5, 48.5, 30, false),
            new Approach("far30c", "Anflug aus 54 Blöcken mit 36 m/s bei 30 Bildern/s: Scheibe früh freigegeben, kein Wandtreffer", 36.5, 50.2, 30, false),
            new Approach("far60", "Anflug aus 51 Blöcken mit 36 m/s bei 60 Bildern/s: Scheibe früh freigegeben, kein Wandtreffer", 36.5, 47.2, 60, false),
            new Approach("tube", "Glasröhre mit 38 m/s: die Scheibe im Flugweg ist vor dem Glas ringsum freigegeben", 38.0, 46.0, 0, true));
    private static int @Nullable [] ground;

    private record Approach(String id, String what, double speed, double runUp, int fps, boolean tube) {
    }

    private GlassFeelSelfTests() {
    }

    public static void register() {
        DroneBreaks.BROKEN.register((drone, pilot, pos, state, impact) -> BROKEN.add(pos.immutable()));
        DroneBreaks.DENIED.register((drone, pilot, pos, outcome) -> DENIED.add(outcome));
        FpvJoltProbe.register();
        SelfTest.clientStep(GROUP + "Welt-Regel propwash:break_impact_scale registriert, Standard 100 %, beim Client angekommen", 100,
                GlassFeelSelfTests::ruleStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster für Bildschirmfotos", 100, ctx -> {
                StepResult result = Screenshots.resizeWindow(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    Screenshots.hideMarker(true);
                }
                return result;
            });
        }
        for (Scenario scenario : SCENARIOS) {
            SelfTest.clientStep(GROUP + scenario.what(), 1400, ctx -> flight(ctx, scenario));
        }
        for (Approach approach : APPROACHES) {
            SelfTest.clientStep(GROUP + approach.what(), 1600, ctx -> approach(ctx, approach));
        }
        SelfTest.clientStep(GROUP + "Vergleich: Scheibe gegen Glasblock, Tempo, Regelstärke und Trefferseite", 20, GlassFeelSelfTests::compareStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster zurück", 5, ctx -> {
                Screenshots.hideMarker(false);
                return Screenshots.restoreWindow(ctx);
            });
        }
    }

    private static StepResult ruleStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(InteractSelfTests.FUTURE)) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                if (WorldRules.rule(InteractRules.BREAK_IMPACT_SCALE).isEmpty()) {
                    throw new IllegalStateException("Welt-Regel " + InteractRules.BREAK_IMPACT_SCALE.id() + " nicht registriert");
                }
                int value = WorldRules.get(server, InteractRules.BREAK_IMPACT_SCALE);
                if (value != 100) {
                    throw new IllegalStateException("Standard ist " + value + " statt 100");
                }
            }));
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Welt-Regel");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (!ClientWorldRules.INSTANCE.known() || Math.abs(ClientInteractRules.breakImpact() - 1.0) > 1.0E-9) {
            return ctx.tick() > 80 ? StepResult.fail("Regel beim Client: " + ClientInteractRules.breakImpact()) : StepResult.pending();
        }
        String name = Component.translatable("gamerule.propwash.break_impact_scale").getString();
        if (name.startsWith("gamerule.")) {
            return StepResult.fail("Name der Regel fehlt in der Sprachdatei");
        }
        ctx.log("propwash:break_impact_scale = 100 % („" + name + "“), 0–300 % in 10er-Schritten");
        return StepResult.pass();
    }

    private static double wantedZ(Scenario sc) {
        int[] arena = InteractSelfTests.ARENA.get();
        return arena == null ? Double.NaN : InteractSelfTests.start(arena).z + sc.wall().lateral();
    }

    private static boolean missed(Map<String, Object> state, Scenario sc) {
        if (sc.wall().lateral() == 0.0 || !state.containsKey(BASE + "passZ") || !state.containsKey(BASE + "uncleared")) {
            return false;
        }
        boolean untouched = PilotBreaks.INSTANCE.predicted() == (int) state.get(BASE + "predicted")
                && PilotBreaks.INSTANCE.requests() == (int) state.get(BASE + "requests")
                && PilotBreaks.INSTANCE.uncleared() == (int) state.get(BASE + "uncleared") && BROKEN.isEmpty() && DENIED.isEmpty();
        return untouched && Math.abs((double) state.get(BASE + "passZ") - wantedZ(sc)) > MISS_DRIFT;
    }

    private static StepResult flight(SelfTest.StepContext ctx, Scenario sc) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        if (state.containsKey(RETRY)) {
            StepResult stopped = InteractSelfTests.stop(ctx, GlassFeelSelfTests::tidy);
            if (stopped.status() == StepResult.Status.PENDING) {
                return stopped;
            }
            if (stopped.status() == StepResult.Status.FAIL) {
                return stopped;
            }
            int attempt = (int) state.getOrDefault(ATTEMPT, 1);
            state.clear();
            state.put(ATTEMPT, attempt + 1);
            return StepResult.pending();
        }
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < 12) {
                    return StepResult.pending();
                }
                BROKEN.clear();
                DENIED.clear();
                PilotBreaks.INSTANCE.forget();
                FpvJoltProbe.intensity(1.0F);
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    server.getGameRules().set(InteractRules.breakRule(), true, server);
                    WorldRules.set(server, InteractRules.BREAK_IMPACT_SCALE, sc.percent());
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    build(player.level(), arena, sc.wall());
                    flyFpv(player, InteractSelfTests.start(arena).add(0.0, 0.0, sc.wall().lateral()));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, sc.what() + " (Start)");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != InteractSelfTests.DRONE_ID.get() || session.sim() == null) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail(sc.what() + ": keine FPV-Sitzung") : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                Telemetry t = telemetry(session);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), InteractSelfTests.since(ctx) >= 10, StabMode.ANGLE);
                if (t == null || !t.armed) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail(sc.what() + ": nicht scharf") : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                hold(session);
                double aside = centre(session, wantedZ(sc));
                Minecraft mc = Minecraft.getInstance();
                int[] arena = InteractSelfTests.ARENA.get();
                boolean cleared = arena != null && mc.level != null && PilotBreaks.INSTANCE.isCleared(hitPos(arena), mc.level.getGameTime());
                boolean rule = ClientInteractRules.dronesBreakBlocks() && Math.abs(ClientInteractRules.breakImpact() - sc.percent() / 100.0) < 1.0E-9;
                if (!settled(session) || !cleared || !rule || InteractSelfTests.since(ctx) < 30 || !(Math.abs(aside) <= LATERAL_TOLERANCE)) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail(sc.what() + ": schwebt nicht ruhig (" + height(session) + " m, "
                            + InteractSelfTests.fmt(aside) + " m neben der Anfluglinie) oder Regel " + rule + "/Freigabe " + cleared + " fehlt")
                            : StepResult.pending();
                }
                launch(session, state, sc);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                return fly(ctx, sc);
            }
            case 5 -> {
                return recover(ctx, sc);
            }
            case 6 -> {
                StepResult server = InteractSelfTests.await(ctx, sc.what() + " (Server)");
                if (server.status() == StepResult.Status.FAIL && missed(state, sc) && (int) state.getOrDefault(ATTEMPT, 1) < MAX_ATTEMPTS) {
                    ctx.log(String.format(Locale.ROOT, "Anflug %d flog an der halben Scheibe vorbei (z beim Durchgang %.3f statt %.3f, im Schweben %.3f "
                                    + "mit %.2f m/s seitlich, im ersten Tick des Anflugs %.3f, keine Vorhersage, kein ungeklärter Treffer) – Flug wird wiederholt",
                            (int) state.getOrDefault(ATTEMPT, 1), (double) state.get(BASE + "passZ"), wantedZ(sc), (double) state.get(BASE + "hoverZ"),
                            (double) state.get(BASE + "hoverVz"), (double) state.getOrDefault(BASE + "firstZ", Double.NaN)));
                    cleanup(session);
                    state.put(RETRY, true);
                    return StepResult.pending();
                }
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                StepResult verdict = judge(ctx, sc);
                if (verdict.status() == StepResult.Status.FAIL) {
                    cleanup(session);
                    return verdict;
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                FpvJoltProbe.stop();
                FpvJoltProbe.intensity(0.0F);
                return InteractSelfTests.stop(ctx, GlassFeelSelfTests::tidy);
            }
        }
    }

    private static int trackFrom(int[] arena, Approach approach) {
        return (int) Math.floor(InteractSelfTests.start(arena).x - approach.runUp()) - 3;
    }

    private static void track(ServerLevel level, int[] arena, Approach approach, boolean build) {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int from = trackFrom(arena, approach);
        if (!build) {
            for (int x = from; x <= wallX(arena); x++) {
                for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
                    for (int dy = -1; dy < WALL_HEIGHT; dy++) {
                        level.setBlockAndUpdate(new BlockPos(x, arena[1] + dy, arena[2] + dz), air);
                    }
                }
            }
            return;
        }
        for (int x = from; x < from + 7; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(new BlockPos(x, arena[1] - 1, arena[2] + dz), Blocks.STONE.defaultBlockState());
            }
        }
        if (!approach.tube()) {
            return;
        }
        for (int x = from; x < wallX(arena); x++) {
            for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
                level.setBlockAndUpdate(new BlockPos(x, arena[1], arena[2] + dz), glass);
                level.setBlockAndUpdate(new BlockPos(x, arena[1] + 4, arena[2] + dz), glass);
            }
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlockAndUpdate(new BlockPos(x, arena[1] + dy, arena[2] - WALL_HALF), glass);
                level.setBlockAndUpdate(new BlockPos(x, arena[1] + dy, arena[2] + WALL_HALF), glass);
            }
        }
    }

    private static void landed(MinecraftServer server, Approach approach) {
        tidy(server);
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena != null && ground != null && arena != ground) {
            track(InteractSelfTests.player(server).level(), arena, approach, false);
        }
        if (ground != null) {
            InteractSelfTests.ARENA.set(ground);
        }
        ground = null;
    }

    private static StepResult approach(SelfTest.StepContext ctx, Approach approach) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String what = approach.what();
        if (state.containsKey("failed")) {
            if (!state.containsKey("restored")) {
                state.put("restored", true);
                mc.options.framerateLimit().set((int) state.get("fps"));
                cleanup(session);
            }
            StepResult stopped = InteractSelfTests.stop(ctx, server -> landed(server, approach));
            return stopped.status() == StepResult.Status.PENDING ? stopped : StepResult.fail((String) state.get("failed"));
        }
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < 12) {
                    return StepResult.pending();
                }
                BROKEN.clear();
                DENIED.clear();
                PilotBreaks.INSTANCE.forget();
                state.put("fps", mc.options.framerateLimit().get());
                if (approach.fps() > 0) {
                    mc.options.framerateLimit().set(approach.fps());
                }
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    server.getGameRules().set(InteractRules.breakRule(), true, server);
                    WorldRules.set(server, InteractRules.BREAK_IMPACT_SCALE, 100);
                    ServerPlayer player = InteractSelfTests.player(server);
                    if (ground != null) {
                        InteractSelfTests.ARENA.set(ground);
                    }
                    int[] base = InteractSelfTests.arena(player);
                    ground = base;
                    int[] arena = {base[0], base[1] + SKY, base[2]};
                    InteractSelfTests.ARENA.set(arena);
                    track(player.level(), arena, approach, false);
                    track(player.level(), arena, approach, true);
                    build(player.level(), arena, Wall.PANE);
                    flyFpv(player, InteractSelfTests.start(arena).add(-approach.runUp(), approach.tube() ? 1.0 : 0.0, 0.0));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, what + " (Start)");
                if (server.status() == StepResult.Status.FAIL) {
                    return failed(state, server.message());
                }
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != InteractSelfTests.DRONE_ID.get() || session.sim() == null) {
                    return InteractSelfTests.since(ctx) > 200 ? failed(state, what + ": keine FPV-Sitzung") : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                Telemetry t = telemetry(session);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), InteractSelfTests.since(ctx) >= 10, StabMode.ANGLE);
                if (t == null || !t.armed) {
                    return InteractSelfTests.since(ctx) > 100 ? failed(state, what + ": nicht scharf") : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                hold(session);
                int[] arena = InteractSelfTests.ARENA.get();
                if (!settled(session) || !ClientInteractRules.dronesBreakBlocks() || arena == null || mc.level == null || InteractSelfTests.since(ctx) < 40) {
                    return InteractSelfTests.since(ctx) > 400 ? failed(state, what + ": schwebt nicht ruhig (" + height(session) + " m)")
                            : StepResult.pending();
                }
                if (PilotBreaks.INSTANCE.isCleared(hitPos(arena), mc.level.getGameTime())) {
                    return failed(state, what + ": Scheibe schon vor dem Anflug freigegeben, Anlauf zu kurz");
                }
                state.put("uncleared", PilotBreaks.INSTANCE.uncleared());
                state.put("clearRequests", PilotBreaks.INSTANCE.clearRequests());
                launch(session, state, new Scenario(approach.id(), what, Wall.PANE, approach.speed(), 45.0, 100, false, null));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                QuadcopterEntity drone = session.drone();
                DroneSim sim = session.sim();
                int[] arena = InteractSelfTests.ARENA.get();
                if (drone == null || sim == null || arena == null || mc.level == null) {
                    return failed(state, what + ": Sitzung verloren");
                }
                cruise(session);
                AABB box = drone.getBoundingBox();
                double distance = wallX(arena) + 7.0 / 16.0 - box.maxX;
                if (!state.containsKey("firstRequest") && PilotBreaks.INSTANCE.clearRequests() > (int) state.get("clearRequests")) {
                    state.put("firstRequest", distance);
                }
                if (!state.containsKey("clearedAt") && PilotBreaks.INSTANCE.isCleared(hitPos(arena), mc.level.getGameTime())) {
                    state.put("clearedAt", distance);
                }
                Vector3d velocity = new Vector3d();
                sim.getVelocity(velocity);
                if (distance > 2.5) {
                    InteractSelfTests.push(sim, approach.speed(), 0.0, 0.0);
                } else if (!state.containsKey("wallSpeed")) {
                    state.put("wallSpeed", velocity.length());
                }
                boolean passed = box.minX > wallX(arena) + 9.0 / 16.0 + 0.05;
                if (!passed && InteractSelfTests.since(ctx) < 70) {
                    return StepResult.pending();
                }
                int uncleared = PilotBreaks.INSTANCE.uncleared() - (int) state.get("uncleared");
                int crashes = session.crashCount() - (int) state.get(BASE + "crashes");
                double clearedAt = state.containsKey("clearedAt") ? (double) state.get("clearedAt") : Double.NaN;
                double firstRequest = state.containsKey("firstRequest") ? (double) state.get("firstRequest") : Double.NaN;
                String report = String.format(Locale.ROOT, "%s, %d Bilder/s: erste Anfrage %.2f m und Freigabe %.2f m vor der Scheibe, Anfragen %d, "
                                + "ungeklärte Treffer %d, Vorhersagen %d, Tempo an der Scheibe %.1f m/s, danach %.1f m/s, Abstürze %d, scharf %s",
                        passed ? "durch die Scheibe" : "an der Scheibe hängen geblieben", mc.getFps(), firstRequest, clearedAt,
                        PilotBreaks.INSTANCE.clearRequests() - (int) state.get("clearRequests"), uncleared,
                        PilotBreaks.INSTANCE.predicted() - (int) state.get(BASE + "predicted"),
                        state.containsKey("wallSpeed") ? (double) state.get("wallSpeed") : Double.NaN, velocity.length(), crashes, sim.telemetry().armed);
                ctx.log(report);
                FpvJoltProbe.stop();
                InteractSelfTests.push(sim, 0.0, 0.0, 0.0);
                if (!passed || uncleared > 0 || crashes > 0 || !sim.telemetry().armed) {
                    return failed(state, what + ": " + report);
                }
                if (!(clearedAt >= CLEARED_AHEAD)) {
                    return failed(state, what + ": Freigabe erst " + InteractSelfTests.fmt(clearedAt) + " m vor der Scheibe – " + report);
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                FpvJoltProbe.stop();
                FpvJoltProbe.intensity(0.0F);
                if (!state.containsKey("restored")) {
                    state.put("restored", true);
                    mc.options.framerateLimit().set((int) state.get("fps"));
                }
                return InteractSelfTests.stop(ctx, server -> landed(server, approach));
            }
        }
    }

    private static StepResult failed(Map<String, Object> state, String message) {
        state.put("failed", message);
        return StepResult.pending();
    }

    private static double centre(PilotSession session, double wantedZ) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (sim == null || drone == null || Double.isNaN(wantedZ)) {
            return Double.NaN;
        }
        double aside = drone.getZ() - wantedZ;
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vz = Math.max(-LATERAL_SPEED, Math.min(LATERAL_SPEED, -LATERAL_GAIN * aside));
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
        return aside;
    }

    private static void cleanup(PilotSession session) {
        FpvJoltProbe.stop();
        FpvJoltProbe.intensity(0.0F);
        session.clearTestInput();
    }

    private static void launch(PilotSession session, Map<String, Object> state, Scenario sc) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (sim == null || drone == null) {
            return;
        }
        Quaterniond attitude = new Quaterniond();
        sim.getAttitude(attitude);
        double angle = Math.toRadians(sc.pitchDeg());
        Quaterniond pitched = new Quaterniond(attitude).rotateX(angle);
        Vector3d nose = pitched.transform(new Vector3d(0.0, 0.0, 1.0));
        if (nose.y > 1.0E-6) {
            pitched = new Quaterniond(attitude).rotateX(-angle);
            nose = pitched.transform(new Vector3d(0.0, 0.0, 1.0));
        }
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        state.put(BASE + "hoverZ", drone.getZ());
        state.put(BASE + "hoverVz", snapshot.vz);
        snapshot.qw = pitched.w;
        snapshot.qx = pitched.x;
        snapshot.qy = pitched.y;
        snapshot.qz = pitched.z;
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena != null) {
            snapshot.pz = InteractSelfTests.start(arena).z + sc.wall().lateral();
        }
        snapshot.vx = sc.speed();
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
        state.put(BASE + "attitude", pitched);
        state.put(BASE + "noseX", nose.x);
        state.put(BASE + "breaks", sim.breakCount());
        state.put(BASE + "crashes", session.crashCount());
        state.put(BASE + "predicted", PilotBreaks.INSTANCE.predicted());
        state.put(BASE + "requests", PilotBreaks.INSTANCE.requests());
        state.put(BASE + "uncleared", PilotBreaks.INSTANCE.uncleared());
        state.put(BASE + "hits", FpvJoltProbe.hits());
        state.put(BASE + "shards", BreakEffects.shards());
        state.put(BASE + "sounds", BreakEffects.sounds());
        state.put(BASE + "maxX", drone.getX());
        state.put(BASE + "back", 0.0);
        if (Screenshots.enabled() && sc.shot() != null) {
            Screenshots.clearOverlays(Minecraft.getInstance());
            state.put(BASE + "shot2", Screenshots.capture(SHOT_DIR + sc.shot() + "_approach"));
        }
        FpvJoltProbe.start();
        cruise(session);
    }

    private static void cruise(PilotSession session) {
        Telemetry t = telemetry(session);
        float throttle = t == null ? 0.3F : (float) Math.min(1.0, t.hoverThrottleStick + 0.08);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ACRO);
    }

    private static StepResult fly(SelfTest.StepContext ctx, Scenario sc) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = InteractSelfTests.ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail(sc.what() + ": Sitzung verloren");
        }
        cruise(session);
        double x = drone.getX();
        if (!state.containsKey(BASE + "firstZ")) {
            state.put(BASE + "firstZ", drone.getZ());
        }
        double maxX = (double) state.get(BASE + "maxX");
        state.put(BASE + "back", Math.max((double) state.get(BASE + "back"), maxX - x));
        state.put(BASE + "maxX", Math.max(maxX, x));
        AABB box = drone.getBoundingBox();
        double back = wallX(arena) + sc.wall().back();
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        if (!state.containsKey(BASE + "passed")) {
            if (box.minX <= back + 0.05) {
                if (InteractSelfTests.since(ctx) == 1 && sim.breakCount() == (int) state.get(BASE + "breaks")) {
                    state.put(BASE + "calm", FpvJoltProbe.max());
                }
                return InteractSelfTests.since(ctx) > 60 ? StepResult.fail(sc.what() + ": nicht durchgeflogen (x=" + InteractSelfTests.fmt(box.minX - back)
                        + ", Vorhersagen " + (PilotBreaks.INSTANCE.predicted() - (int) state.get(BASE + "predicted")) + ", abgelehnt " + DENIED + ")")
                        : StepResult.pending();
            }
            state.put(BASE + "passed", InteractSelfTests.since(ctx));
            state.put(BASE + "passZ", drone.getZ());
            state.put(BASE + "speed", velocity.length());
            if (Screenshots.enabled() && sc.shot() != null) {
                state.put(BASE + "shot0", Screenshots.capture(SHOT_DIR + sc.shot() + "_pass"));
            }
            return StepResult.pending();
        }
        int after = InteractSelfTests.since(ctx) - (int) state.get(BASE + "passed");
        if (Screenshots.enabled() && sc.shot() != null && after == 1) {
            state.put(BASE + "shot1", Screenshots.capture(sc.shot()));
        }
        if (after < MEASURE_TICKS) {
            return StepResult.pending();
        }
        Quaterniond now = new Quaterniond();
        sim.getAttitude(now);
        Quaterniond start = (Quaterniond) state.get(BASE + "attitude");
        double dot = Math.abs(now.w * start.w + now.x * start.x + now.y * start.y + now.z * start.z);
        state.put(BASE + "deviation", Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot))));
        state.put(BASE + "armed", sim.telemetry().armed);
        state.put(BASE + "camera", FpvJoltProbe.stop());
        state.put(BASE + "frames", FpvJoltProbe.frames());
        InteractSelfTests.push(sim, 0.0, 0.0, 0.0);
        hold(session);
        InteractSelfTests.next(ctx);
        return StepResult.pending();
    }

    private static StepResult recover(SelfTest.StepContext ctx, Scenario sc) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null) {
            return StepResult.fail(sc.what() + ": Sitzung verloren");
        }
        hold(session);
        if (InteractSelfTests.since(ctx) < RECOVER_TICKS) {
            return StepResult.pending();
        }
        for (int i = 0; i <= 2; i++) {
            Object shot = state.get(BASE + "shot" + i);
            if (shot instanceof CompletableFuture<?> future) {
                if (!future.isDone()) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail(sc.what() + ": Bildschirmfoto wird nicht fertig") : StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail(sc.what() + ": Bildschirmfoto " + future.exceptionNow());
                }
            }
        }
        state.put(BASE + "tilt", sim.telemetry().tiltDeg);
        state.put(BASE + "armedAfter", sim.telemetry().armed);
        state.put(BASE + "clientPos", drone.position());
        serverDronePos = null;
        state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
            QuadcopterEntity onServer = InteractSelfTests.serverDrone(server);
            serverDronePos = onServer.position();
            int[] arena = InteractSelfTests.ARENA.get();
            if (arena == null) {
                throw new IllegalStateException("Arena fehlt");
            }
            BlockState at = InteractSelfTests.player(server).level().getBlockState(hitPos(arena));
            if (!at.isAir()) {
                throw new IllegalStateException("Block am Server noch da: " + at + ", zerbrochen " + BROKEN + ", abgelehnt " + DENIED
                        + ", Vorhersagen " + (PilotBreaks.INSTANCE.predicted() - (int) state.get(BASE + "predicted")) + ", Anfragen "
                        + (PilotBreaks.INSTANCE.requests() - (int) state.get(BASE + "requests")) + ", ungeklärte Treffer "
                        + (PilotBreaks.INSTANCE.uncleared() - (int) state.getOrDefault(BASE + "uncleared", 0)) + ", Drohne am Client " + state.get(BASE + "clientPos")
                        + ", am Server " + onServer.position());
            }
        }));
        InteractSelfTests.next(ctx);
        return StepResult.pending();
    }

    private static StepResult judge(SelfTest.StepContext ctx, Scenario sc) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        DroneSim sim = session.sim();
        int[] arena = InteractSelfTests.ARENA.get();
        Minecraft mc = Minecraft.getInstance();
        if (sim == null || arena == null || mc.level == null) {
            return StepResult.fail(sc.what() + ": Sitzung verloren");
        }
        BreakReport report = sim.lastBreak();
        int breaks = sim.breakCount() - (int) state.get(BASE + "breaks");
        if (report == null || breaks != 1) {
            return StepResult.fail(sc.what() + ": " + breaks + " Durchbrüche in der Sim statt 1");
        }
        BlockPos hit = hitPos(arena);
        if (!BROKEN.contains(hit) || BROKEN.size() != 1 || !DENIED.isEmpty()) {
            return StepResult.fail(sc.what() + ": Server hat " + BROKEN + " zerbrochen, abgelehnt " + DENIED + ", erwartet " + hit.toShortString());
        }
        if (!mc.level.getBlockState(hit).isAir()) {
            return StepResult.fail(sc.what() + ": Block beim Client wieder da");
        }
        int predicted = PilotBreaks.INSTANCE.predicted() - (int) state.get(BASE + "predicted");
        int requests = PilotBreaks.INSTANCE.requests() - (int) state.get(BASE + "requests");
        if (predicted != 1 || requests != 1) {
            return StepResult.fail(sc.what() + ": Vorhersagen " + predicted + ", Anfragen " + requests + " statt je 1");
        }
        double mass = sim.params().massKg;
        double joules = sc.wall().joules();
        double fracture = report.approach() - Math.sqrt(Math.max(0.0, report.approach() * report.approach() - 2.0 * joules / mass));
        if (Math.abs(fracture - report.lostFracture()) > 1.0E-6) {
            return StepResult.fail(sc.what() + ": Bruchanteil " + report.lostFracture() + " statt " + fracture);
        }
        if (report.approach() < 0.85 * sc.speed() || report.approach() > 1.08 * sc.speed()) {
            return StepResult.fail(sc.what() + ": Anflug mit " + report.approach() + " statt etwa " + sc.speed() + " m/s");
        }
        double speedAfter = (double) state.get(BASE + "speed");
        if (Math.abs(speedAfter - report.speedAfter()) > 2.0) {
            return StepResult.fail(sc.what() + ": Tempo nach dem Durchbruch " + speedAfter + " statt " + report.speedAfter());
        }
        double deviation = (double) state.get(BASE + "deviation");
        double camera = (double) state.get(BASE + "camera");
        double calm = (double) state.getOrDefault(BASE + "calm", 0.0);
        double amplitude = ImpactJolt.amplitude(report.lost());
        int hits = FpvJoltProbe.hits() - (int) state.get(BASE + "hits");
        int shards = BreakEffects.shards() - (int) state.get(BASE + "shards");
        int sounds = BreakEffects.sounds() - (int) state.get(BASE + "sounds");
        int crashes = session.crashCount() - (int) state.get(BASE + "crashes");
        double tilt = (double) state.get(BASE + "tilt");
        boolean armedAfter = (boolean) state.get(BASE + "armedAfter");
        double back = (double) state.get(BASE + "back");
        Vec3 client = (Vec3) state.get(BASE + "clientPos");
        Vec3 server = serverDronePos;
        double gap = server == null ? Double.NaN : server.distanceTo(client);
        ctx.log(String.format(Locale.ROOT, "%s: seitlich z %.3f im Schweben (%.2f m/s), %.3f im ersten Tick, %.3f am Glas, soll %.3f", sc.id(),
                (double) state.get(BASE + "hoverZ"), (double) state.get(BASE + "hoverVz"), (double) state.getOrDefault(BASE + "firstZ", Double.NaN),
                (double) state.getOrDefault(BASE + "passZ", Double.NaN), wantedZ(sc)));
        ctx.log(String.format(Locale.ROOT, "%s: Anflug %.1f m/s, Bruch −%.2f, Scherben −%.2f (%.0f g auf %.0f cm², %.0f %% der Hülle), danach %.1f m/s; "
                        + "Drehstoß %.1f rad/s (Nick %.1f, Gier %.1f, Roll %.1f), Props getroffen %d, Lage nach %d Ticks %.1f° daneben, nach %d Ticks %.1f° "
                        + "Neigung, scharf %s, Abstürze %d; Kameraschlag %.2f° erwartet, im Bild bis %.2f° (vorher %.2f°, %d Bilder), Scherben %d, "
                        + "Geräusch %d (%.2f), rückwärts %.2f m, Server %.2f m neben dem Client",
                sc.id(), report.approach(), report.lostFracture(), report.lostShards(), report.plugKg() * 1000.0, report.areaM2() * 10000.0,
                report.share() * 100.0, speedAfter, report.rateChange(), report.rateX(), report.rateY(), report.rateZ(), report.struckCount(),
                MEASURE_TICKS, deviation, RECOVER_TICKS, tilt, armedAfter, crashes, amplitude, camera, calm, (int) state.get(BASE + "frames"), shards,
                sounds, BreakEffects.lastVolume(), back, gap));
        if (report.crash() != sc.crash() || sc.crash() != crashes > 0) {
            return StepResult.fail(sc.what() + ": Absturz " + report.crash() + "/" + crashes + ", erwartet " + sc.crash());
        }
        if (sc.percent() == 0) {
            if (report.lostShards() != 0.0 || report.rateChange() != 0.0 || report.struck() != 0 || deviation > 3.0) {
                return StepResult.fail(sc.what() + ": bei 0 % trotzdem Stoß (" + report.lostShards() + " m/s, " + report.rateChange() + " rad/s, "
                        + deviation + "°)");
            }
        } else {
            if (!(report.lostShards() > 0.1) || !(report.rateChange() > 0.5)) {
                return StepResult.fail(sc.what() + ": Stoß zu schwach (Scherben " + report.lostShards() + " m/s, Drehstoß " + report.rateChange() + " rad/s)");
            }
            if (!sc.crash() && !(deviation > 1.0 && deviation < 35.0)) {
                return StepResult.fail(sc.what() + ": Lage-Störung " + deviation + "° außerhalb 1–35°");
            }
        }
        if (!sc.crash() && (!armedAfter || tilt > 8.0)) {
            return StepResult.fail(sc.what() + ": fängt sich nicht (scharf " + armedAfter + ", Neigung " + tilt + "°)");
        }
        if (hits != 1 || Math.abs(FpvJoltProbe.lastAmplitude() - amplitude) > 1.0E-9 || !(amplitude > 0.5)) {
            return StepResult.fail(sc.what() + ": Kameraschlag " + hits + "× mit " + FpvJoltProbe.lastAmplitude() + "° statt " + amplitude + "°");
        }
        if (!(camera > 0.3 * amplitude) || !(camera > calm + 0.25) || calm > 0.8) {
            return StepResult.fail(sc.what() + ": Kameraschlag nicht im Bild (" + camera + "° von " + amplitude + "°, vorher " + calm + "°)");
        }
        int expectedShards = BreakEffects.count(mc.options.particles().get(), report.share());
        if (shards != expectedShards || sounds != 1 || (mc.options.particles().get() == ParticleStatus.ALL && shards < 8)) {
            return StepResult.fail(sc.what() + ": Scherben " + shards + " statt " + expectedShards + ", Geräusche " + sounds);
        }
        if (back > 0.3 || !(gap < 3.0)) {
            return StepResult.fail(sc.what() + ": Drohne sprang zurück (" + back + " m) oder Server folgt nicht (" + gap + " m)");
        }
        RESULTS.put(sc.id(), new Result(report, deviation, camera));
        return StepResult.pass();
    }

    private static StepResult compareStep(SelfTest.StepContext ctx) {
        for (Scenario sc : SCENARIOS) {
            if (!RESULTS.containsKey(sc.id())) {
                return StepResult.fail("Messung " + sc.id() + " fehlt");
            }
        }
        BreakReport slow = RESULTS.get(PANE_SLOW.id()).report();
        BreakReport pane = RESULTS.get(PANE.id()).report();
        BreakReport fast = RESULTS.get(PANE_FAST.id()).report();
        BreakReport block = RESULTS.get(BLOCK.id()).report();
        BreakReport blockFast = RESULTS.get(BLOCK_FAST.id()).report();
        BreakReport off = RESULTS.get(PANE_OFF.id()).report();
        BreakReport twice = RESULTS.get(PANE_DOUBLE.id()).report();
        BreakReport right = RESULTS.get(HALF_RIGHT.id()).report();
        BreakReport left = RESULTS.get(HALF_LEFT.id()).report();
        double shareSlow = slow.lost() / slow.approach();
        double sharePane = pane.lost() / pane.approach();
        double shareFast = fast.lost() / fast.approach();
        if (!(shareSlow > sharePane && sharePane > shareFast)) {
            return StepResult.fail("Tempoverlust der Scheibe fällt nicht mit dem Tempo: " + shareSlow + ", " + sharePane + ", " + shareFast);
        }
        if (!(block.lost() > 2.0 * pane.lost() && blockFast.lost() > 2.0 * fast.lost() && blockFast.rateChange() > fast.rateChange())) {
            return StepResult.fail("Glasblock nicht deutlich härter als die Scheibe: " + block.lost() + "/" + pane.lost() + ", " + blockFast.lost() + "/"
                    + fast.lost() + ", Drehstoß " + blockFast.rateChange() + "/" + fast.rateChange());
        }
        if (!(off.lost() < pane.lost() && pane.lostShards() < twice.lostShards() && pane.rateChange() < twice.rateChange())) {
            return StepResult.fail("Regelstärke wirkt nicht: 0 % " + off.lost() + ", 100 % " + pane.lost() + "/" + pane.rateChange() + ", 200 % "
                    + twice.lost() + "/" + twice.rateChange());
        }
        if (!(right.rateY() * left.rateY() < 0.0 && Math.abs(right.rateY()) > 0.5 && Math.abs(left.rateY()) > 0.5 && right.share() < 0.9
                && left.share() < 0.9 && right.plugKg() < pane.plugKg())) {
            return StepResult.fail("einseitiger Treffer giert nicht gegensinnig: rechts " + right.rateY() + " rad/s (" + right.share() + "), links "
                    + left.rateY() + " rad/s (" + left.share() + ")");
        }
        ctx.log(String.format(Locale.ROOT, "Scheibe: %.0f %% / %.0f %% / %.0f %% Tempo bei 15 / 25 / 38 m/s; Glasblock %.1f gegen %.1f m/s bei 25 und "
                        + "%.1f gegen %.1f m/s bei 38 m/s; Regel 0 / 100 / 200 %%: Drehstoß %.1f / %.1f / %.1f rad/s, Lage %.1f / %.1f / %.1f°; Glas rechts "
                        + "%.1f, links %.1f rad/s um die Hochachse",
                shareSlow * 100.0, sharePane * 100.0, shareFast * 100.0, block.lost(), pane.lost(), blockFast.lost(), fast.lost(), off.rateChange(),
                pane.rateChange(), twice.rateChange(), RESULTS.get(PANE_OFF.id()).deviation(), RESULTS.get(PANE.id()).deviation(),
                RESULTS.get(PANE_DOUBLE.id()).deviation(), right.rateY(), left.rateY()));
        return StepResult.pass();
    }

    private static @Nullable Telemetry telemetry(PilotSession session) {
        DroneSim sim = session.sim();
        return sim == null ? null : sim.telemetry();
    }

    private static double height(PilotSession session) {
        QuadcopterEntity drone = session.drone();
        int[] arena = InteractSelfTests.ARENA.get();
        return drone == null || arena == null ? Double.NaN : drone.getY() - arena[1];
    }

    private static boolean settled(PilotSession session) {
        DroneSim sim = session.sim();
        if (sim == null) {
            return false;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        return Math.abs(height(session) - FLIGHT_HEIGHT) < 0.12 && velocity.length() < 0.3 && sim.telemetry().tiltDeg < 2.0;
    }

    private static void hold(PilotSession session) {
        DroneSim sim = session.sim();
        if (sim == null) {
            return;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        double error = FLIGHT_HEIGHT - height(session);
        double throttle = sim.telemetry().hoverThrottleStick + HOLD_GAIN * error - HOLD_DAMPING * velocity.y;
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.max(0.0, Math.min(1.0, throttle))), true, StabMode.ANGLE);
    }

    private static void flyFpv(ServerPlayer player, Vec3 pos) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, InteractSelfTests.upright());
        InteractSelfTests.DRONE_ID.set(drone.getId());
        player.setGameMode(GameType.CREATIVE);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void tidy(MinecraftServer server) {
        server.getGameRules().set(InteractRules.breakRule(), false, server);
        WorldRules.set(server, InteractRules.BREAK_IMPACT_SCALE, InteractRules.BREAK_IMPACT_SCALE.defaultValue());
        ServerPlayer player = InteractSelfTests.player(server);
        ItemStack head = previousHead;
        ItemStack hand = previousHand;
        if (head != null) {
            player.setItemSlot(EquipmentSlot.HEAD, head);
        }
        if (hand != null) {
            player.setItemSlot(EquipmentSlot.MAINHAND, hand);
        }
        previousHead = null;
        previousHand = null;
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena != null) {
            clearWall(player.level(), arena);
        }
    }

    private static int wallX(int[] arena) {
        return (int) Math.floor(InteractSelfTests.start(arena).x + WALL_AHEAD);
    }

    private static BlockPos hitPos(int[] arena) {
        return new BlockPos(wallX(arena), arena[1] + HIT_ROW, arena[2]);
    }

    private static void build(ServerLevel level, int[] arena, Wall wall) {
        int wx = wallX(arena);
        BlockState block = wall == Wall.BLOCK ? Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState()
                : Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
        int from = wall == Wall.PANE_RIGHT ? 0 : -WALL_HALF;
        int to = wall == Wall.PANE_LEFT ? 0 : WALL_HALF;
        for (int dz = from; dz <= to; dz++) {
            for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                level.setBlockAndUpdate(new BlockPos(wx, arena[1] + dy, arena[2] + dz), block);
            }
        }
    }

    private static void clearWall(ServerLevel level, int[] arena) {
        int wx = wallX(arena);
        for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
            for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                level.setBlockAndUpdate(new BlockPos(wx, arena[1] + dy, arena[2] + dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private enum Wall {
        PANE(9.0 / 16.0, BreakMath.threshold(0.3, 0.125), 0.0),
        PANE_RIGHT(9.0 / 16.0, BreakMath.threshold(0.3, 0.125), -EDGE_OFFSET),
        PANE_LEFT(9.0 / 16.0, BreakMath.threshold(0.3, 0.125), EDGE_OFFSET),
        BLOCK(1.0, BreakMath.threshold(0.3, 1.0), 0.0);

        private final double back;
        private final double joules;
        private final double lateral;

        Wall(double back, double joules, double lateral) {
            this.back = back;
            this.joules = joules;
            this.lateral = lateral;
        }

        double lateral() {
            return this.lateral;
        }

        double back() {
            return this.back;
        }

        double joules() {
            return this.joules;
        }
    }

    private record Scenario(String id, String what, Wall wall, double speed, double pitchDeg, int percent, boolean crash, @Nullable String shot) {
    }

    private record Result(BreakReport report, double deviation, double camera) {
    }
}
