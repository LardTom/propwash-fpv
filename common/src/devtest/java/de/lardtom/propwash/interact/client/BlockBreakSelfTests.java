package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.interact.BreakMath;
import de.lardtom.propwash.interact.BreakRules;
import de.lardtom.propwash.interact.BreakableBlocks;
import de.lardtom.propwash.interact.DroneBreaks;
import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BlockBreakSelfTests {
    private static final String GROUP = "blockbruch: ";
    private static final double WALL_AHEAD = 3.0;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 4;
    private static final double WALKING = 4.317;
    private static final double SLOW = 10.0;
    private static final String CRASHES = "bb_crashes";
    private static final String REQUESTS = "bb_requests";
    private static final String PREDICTED = "bb_predicted";
    private static final String PASSED = "bb_passed";
    private static final String SPEED = "bb_speed";
    private static final String CHECKED = "bb_checked";
    private static final String CLEARS = "bb_clears";
    private static final AtomicBoolean GUARD = new AtomicBoolean();
    private static final List<Broken> BROKEN = new CopyOnWriteArrayList<>();
    private static final List<BreakRules.Outcome> DENIED = new CopyOnWriteArrayList<>();
    private static final AtomicReference<BreakRules.@Nullable Outcome> DIRECT = new AtomicReference<>();
    private static final int DIRECT_AT = 8;

    private static final Scenario RULE_OFF = new Scenario("Regel aus – die Glasscheibe hält auch bei 25 m/s", DroneBuild.FREESTYLE, 25.0, false,
            Wall.PANE, false, Boolean.TRUE, null, "block_break_rule_off");
    private static final Scenario WHOOP_SLOW = new Scenario("Whoop prallt im Schritttempo an der Scheibe ab", DroneBuild.WHOOP, WALKING, true,
            Wall.PANE, false, Boolean.FALSE, null, null);
    private static final Scenario FREESTYLE_SLOW = new Scenario("5-Zoll prallt mit 8 m/s an der Scheibe ab", DroneBuild.FREESTYLE, 8.0, true,
            Wall.PANE, false, null, null, null);
    private static final Scenario FREESTYLE_PANE = new Scenario("5-Zoll zerbricht die Scheibe mit 25 m/s und fliegt langsamer weiter",
            DroneBuild.FREESTYLE, 25.0, true, Wall.PANE, true, Boolean.FALSE, null, "block_break_pane");
    private static final Scenario FREESTYLE_GLASS = new Scenario("5-Zoll zerbricht einen Glasblock mit 30 m/s und stürzt dabei ab",
            DroneBuild.FREESTYLE, 30.0, true, Wall.GLASS, true, Boolean.TRUE, null, "block_break_glass_block");
    private static final Scenario ADVENTURE = new Scenario("Abenteuermodus – nichts zerbricht, der Server lehnt ab", DroneBuild.FREESTYLE, 25.0,
            true, Wall.PANE, false, null, GameType.ADVENTURE, null);
    private static final Scenario GUARDED = new Scenario("Geschützter Bereich – die Drohne prallt schon beim ersten Treffer ab, kein Block verschwindet",
            DroneBuild.FREESTYLE, 25.0, true, Wall.PANE, false, null, null, null);
    private static final Scenario POT = new Scenario("Verzierter Topf zerspringt im Überlebensmodus in Scherben und gibt den Inhalt frei",
            DroneBuild.FREESTYLE, 8.0, true, Wall.POT, true, Boolean.FALSE, GameType.SURVIVAL, "block_break_pot");

    private BlockBreakSelfTests() {
    }

    public static void register() {
        DroneBreaks.BROKEN.register((drone, pilot, pos, state, impact) -> BROKEN.add(new Broken(pos.immutable(), state, impact, pilot.getUUID())));
        DroneBreaks.DENIED.register((drone, pilot, pos, outcome) -> DENIED.add(outcome));
        DroneBreaks.GUARDS.register((level, pilot, pos) -> !GUARD.get());
        SelfTest.clientStep(GROUP + "Welt-Regel propwash:drones_break_blocks registriert, Standard aus, Block-Tag geladen", 100,
                BlockBreakSelfTests::ruleStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster für Bildschirmfotos", 100, ctx -> {
                StepResult result = Screenshots.resizeWindow(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    Screenshots.hideMarker(true);
                }
                return result;
            });
        }
        for (Scenario scenario : List.of(RULE_OFF, WHOOP_SLOW, FREESTYLE_SLOW, FREESTYLE_PANE, FREESTYLE_GLASS, ADVENTURE, GUARDED, POT)) {
            SelfTest.clientStep(GROUP + scenario.what(), 1200, ctx -> flight(ctx, scenario));
        }
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster zurück", 5, ctx -> {
                Screenshots.hideMarker(false);
                return Screenshots.restoreWindow(ctx);
            });
        }
    }

    private static StepResult ruleStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                Identifier id = BuiltInRegistries.GAME_RULE.getKey(InteractRules.breakRule());
                if (id == null || !"propwash:drones_break_blocks".equals(id.toString())) {
                    throw new IllegalStateException("Spielregel nicht registriert: " + id);
                }
                if (InteractRules.dronesBreakBlocks(server)) {
                    throw new IllegalStateException("Standardwert ist nicht aus");
                }
                ServerLevel level = InteractSelfTests.player(server).level();
                BlockPos probe = BlockPos.ZERO;
                for (BlockState state : List.of(Blocks.GLASS.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(),
                        Blocks.TINTED_GLASS.defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState(),
                        Blocks.STAINED_GLASS.pick(DyeColor.RED).defaultBlockState(), Blocks.DECORATED_POT.defaultBlockState())) {
                    if (!BreakableBlocks.candidate(level, probe, state)) {
                        throw new IllegalStateException("nicht zerbrechlich: " + state);
                    }
                }
                for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                        Blocks.ICE.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(), Blocks.AIR.defaultBlockState())) {
                    if (BreakableBlocks.candidate(level, probe, state)) {
                        throw new IllegalStateException("fälschlich zerbrechlich: " + state);
                    }
                }
                InteractSelfTests.SERVER_REPORT.set(InteractRules.breakRule().id() + " = " + server.getGameRules().getAsString(InteractRules.breakRule())
                        + ", Tag propwash:drone_breakable mit Glas, Scheiben, Topf, ohne Stein, Laub, Eis");
            }));
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Spielregel");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (!ClientWorldRules.INSTANCE.known() || ClientInteractRules.dronesBreakBlocks()) {
            return ctx.tick() > 80 ? StepResult.fail("Client kennt die Spielregel nicht oder sieht sie an") : StepResult.pending();
        }
        ctx.log(InteractSelfTests.SERVER_REPORT.get() + ", beim Client aus");
        return StepResult.pass();
    }

    private static StepResult flight(SelfTest.StepContext ctx, Scenario sc) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < 12) {
                    return StepResult.pending();
                }
                BROKEN.clear();
                DENIED.clear();
                PilotBreaks.INSTANCE.forget();
                state.put(CLEARS, PilotBreaks.INSTANCE.clearRequests());
                GUARD.set(sc == GUARDED);
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    server.getGameRules().set(InteractRules.breakRule(), sc.rule(), server);
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    build(player.level(), arena, sc.wall());
                    InteractSelfTests.fly(player, sc.build(), InteractSelfTests.start(arena));
                    if (sc.mode() != null) {
                        player.setGameMode(sc.mode());
                    }
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, sc.what(), null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, sc.what());
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                Minecraft mc = Minecraft.getInstance();
                GameType mode = mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
                boolean ready = ClientInteractRules.dronesBreakBlocks() == sc.rule() && (sc.mode() == null || sc.mode() == mode);
                if (!InteractSelfTests.settled(session, ctx) || !ready) {
                    return InteractSelfTests.since(ctx) > 240 ? StepResult.fail(sc.what() + ": schwebt nicht ruhig oder Regel/Spielmodus fehlt ("
                            + ClientInteractRules.dronesBreakBlocks() + ", " + mode + ")") : StepResult.pending();
                }
                state.put(CRASHES, session.crashCount());
                state.put(REQUESTS, PilotBreaks.INSTANCE.requests());
                state.put(PREDICTED, PilotBreaks.INSTANCE.predicted());
                InteractSelfTests.push(session.sim(), sc.speed(), 0.0, 0.0);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                return evaluate(ctx, sc);
            }
            case 6 -> {
                InteractSelfTests.hover(session);
                StepResult server = InteractSelfTests.await(ctx, sc.what());
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                ctx.log(InteractSelfTests.SERVER_REPORT.get());
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                InteractSelfTests.hover(session);
                if (!Screenshots.enabled() || sc.shot() == null) {
                    InteractSelfTests.next(ctx);
                    return StepResult.pending();
                }
                StepResult shot = InteractSelfTests.shot(ctx, sc.shot());
                if (shot.status() == StepResult.Status.PASS) {
                    state.remove("fov");
                    state.remove("shot");
                    InteractSelfTests.next(ctx);
                    return StepResult.pending();
                }
                return shot;
            }
            default -> {
                GUARD.set(false);
                return InteractSelfTests.stop(ctx, server -> {
                    server.getGameRules().set(InteractRules.breakRule(), false, server);
                    ServerPlayer player = InteractSelfTests.player(server);
                    player.setGameMode(GameType.CREATIVE);
                    int[] arena = InteractSelfTests.ARENA.get();
                    if (arena != null) {
                        clearWall(player.level(), arena);
                    }
                });
            }
        }
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, Scenario sc) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        InteractSelfTests.hover(session);
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = InteractSelfTests.ARENA.get();
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || sim == null || arena == null || mc.level == null) {
            return StepResult.fail(sc.what() + ": Sitzung verloren");
        }
        int wx = wallX(arena);
        double front = wx + sc.wall().front();
        double back = wx + sc.wall().back();
        AABB box = drone.getBoundingBox();
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        boolean passed = state.containsKey(PASSED);
        if (!passed && sc.speed() < SLOW && box.maxX < front - 0.15) {
            InteractSelfTests.push(sim, sc.speed(), 0.0, 0.0);
        }
        if (!passed && box.minX > back + 0.05) {
            state.put(PASSED, InteractSelfTests.since(ctx));
            state.put(SPEED, velocity.length());
            InteractSelfTests.push(sim, 0.0, 0.0, 0.0);
            passed = true;
        }
        BlockPos hit = hitPos(arena);
        if (sc.mode() == GameType.ADVENTURE && InteractSelfTests.since(ctx) == DIRECT_AT) {
            DIRECT.set(null);
            ctx.onServer(server -> DIRECT.set(DroneBreaks.attempt(InteractSelfTests.player(server).level(), InteractSelfTests.player(server),
                    InteractSelfTests.serverDrone(server), hit, new Vec3(sc.speed(), 0.0, 0.0), 0, true)));
        }
        int crashes = session.crashCount() - (int) state.get(CRASHES);
        int requests = PilotBreaks.INSTANCE.requests() - (int) state.get(REQUESTS);
        int predicted = PilotBreaks.INSTANCE.predicted() - (int) state.get(PREDICTED);
        if (sc.breaks()) {
            if (!passed || BROKEN.isEmpty()) {
                return InteractSelfTests.since(ctx) > 120 ? StepResult.fail(sc.what() + ": nicht durchgeflogen oder kein Bruch am Server (x="
                        + InteractSelfTests.fmt(box.minX - wx) + ", Vorhersagen " + predicted + ", Anfragen " + requests + ", abgelehnt " + DENIED + ")")
                        : StepResult.pending();
            }
            if (InteractSelfTests.since(ctx) - (int) state.get(PASSED) < 30) {
                return StepResult.pending();
            }
            Broken first = BROKEN.getFirst();
            double speedAfter = (double) state.get(SPEED);
            if (!first.pos().equals(hit) || mc.player == null || !first.pilot().equals(mc.player.getUUID())) {
                return StepResult.fail(sc.what() + ": falscher Bruch " + first + " statt " + hit.toShortString());
            }
            if (!(speedAfter < sc.speed() - 0.5) || !(first.impact().speedLost() > 0.0)) {
                return StepResult.fail(sc.what() + ": kein Tempo verloren (danach " + InteractSelfTests.fmt(speedAfter) + " m/s, Server "
                        + InteractSelfTests.fmt(first.impact().speedLost()) + ")");
            }
            if (!mc.level.getBlockState(hit).isAir()) {
                return StepResult.fail(sc.what() + ": Block beim Client noch da: " + mc.level.getBlockState(hit));
            }
            if (predicted < 1 || requests < 1) {
                return StepResult.fail(sc.what() + ": Client hat nicht vorhergesagt oder nicht gefragt (" + predicted + "/" + requests + ")");
            }
            ctx.log(String.format(Locale.ROOT, "Bruch %s bei %.1f m/s: Server %.1f J ≥ Schwelle, %.2f m/s verloren, danach %.1f m/s, Abstürze %d",
                    first.state().getBlock().getDescriptionId(), sc.speed(), first.impact().energy(), first.impact().speedLost(), speedAfter, crashes));
        } else {
            if (InteractSelfTests.since(ctx) < 60) {
                if (!BROKEN.isEmpty() || passed) {
                    return StepResult.fail(sc.what() + ": zerbrochen oder durchgeflogen trotz " + (sc.rule() ? "zu wenig Wucht/Recht" : "Regel aus"));
                }
                return StepResult.pending();
            }
            if (box.maxX > front + 0.02) {
                return StepResult.fail(sc.what() + ": Drohne steckt im Block (x=" + InteractSelfTests.fmt(box.maxX - front) + ")");
            }
            if (predicted != 0 || requests != 0) {
                return StepResult.fail(sc.what() + ": Client hat trotzdem einen Bruch vorhergesagt (" + predicted + "/" + requests + ")");
            }
            if (mc.level.getBlockState(hit).isAir()) {
                return StepResult.fail(sc.what() + ": Block beim Client verschwunden");
            }
            if (sc == GUARDED) {
                int clears = PilotBreaks.INSTANCE.clearRequests() - (int) state.get(CLEARS);
                if (clears < 1 || PilotBreaks.INSTANCE.isCleared(hit, mc.level.getGameTime()) || !DENIED.isEmpty()) {
                    return StepResult.fail(sc.what() + ": Freigabe nicht abgelehnt (Anfragen " + clears + ", frei "
                            + PilotBreaks.INSTANCE.isCleared(hit, mc.level.getGameTime()) + ", Ablehnungen am Server beim Bruch " + DENIED + ")");
                }
                ctx.log(sc.what() + ": Freigabe vorab abgelehnt (" + clears + " Anfragen), keine Bruch-Anfrage, Scheibe beim Client nie entfernt");
            }
            ctx.log(sc.what() + ": Drohne vor dem Block (" + InteractSelfTests.fmt(front - box.maxX) + " m), Abstürze " + crashes);
        }
        if (sc.crash() != null && sc.crash() != crashes > 0) {
            return StepResult.fail(sc.what() + ": Absturz " + (crashes > 0 ? "gezählt" : "fehlt") + " (erwartet " + sc.crash() + ")");
        }
        state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> serverCheck(server, sc)));
        InteractSelfTests.next(ctx);
        return StepResult.pending();
    }

    private static void serverCheck(MinecraftServer server, Scenario sc) {
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena == null) {
            throw new IllegalStateException("Arena fehlt");
        }
        BlockPos hit = hitPos(arena);
        int wx = wallX(arena);
        int missing = 0;
        if (sc.wall() == Wall.POT) {
            missing = level.getBlockState(hit).isAir() ? 1 : 0;
        } else {
            for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    if (level.getBlockState(new BlockPos(wx, arena[1] + dy, arena[2] + dz)).isAir()) {
                        missing++;
                    }
                }
            }
        }
        if (sc.breaks() != (missing == 1) || sc.breaks() != level.getBlockState(hit).isAir()) {
            throw new IllegalStateException("am Server fehlen " + missing + " Blöcke, Treffer " + level.getBlockState(hit));
        }
        String report = "Server: " + missing + " Block zerbrochen";
        AABB around = new AABB(hit).inflate(4.0);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, around);
        if (sc.wall() == Wall.POT) {
            int bricks = count(drops, Items.BRICK);
            int sherds = count(drops, Items.ANGLER_POTTERY_SHERD);
            int apples = count(drops, Items.APPLE);
            int pots = count(drops, Items.DECORATED_POT);
            if (bricks != 3 || sherds != 1 || apples != 1 || pots != 0) {
                throw new IllegalStateException("Topf nicht in Scherben zersprungen: " + bricks + " Ziegel, " + sherds + " Angler-Scherben, " + apples
                        + " Äpfel, " + pots + " ganze Töpfe");
            }
            report += ", 3 Ziegel, die Angler-Scherbe und der Apfel liegen am Boden";
        } else if (!drops.isEmpty()) {
            throw new IllegalStateException("Glas hat etwas fallen lassen: " + drops.getFirst().getItem());
        }
        if (sc.mode() == GameType.ADVENTURE) {
            BreakRules.Outcome direct = DIRECT.get();
            if (direct != BreakRules.Outcome.GAME_MODE || level.getBlockState(hit).isAir()) {
                throw new IllegalStateException("direkte Anfrage im Abenteuermodus: " + direct + ", Block " + level.getBlockState(hit));
            }
            report += ", direkte Anfrage abgelehnt (" + direct + ")";
        }
        InteractSelfTests.SERVER_REPORT.set(report);
    }

    private static int count(List<ItemEntity> drops, net.minecraft.world.item.Item item) {
        int sum = 0;
        for (ItemEntity drop : drops) {
            if (drop.getItem().is(item)) {
                sum += drop.getItem().getCount();
            }
        }
        return sum;
    }

    private static int wallX(int[] arena) {
        return (int) Math.floor(InteractSelfTests.start(arena).x + WALL_AHEAD);
    }

    private static BlockPos hitPos(int[] arena) {
        return new BlockPos(wallX(arena), arena[1], arena[2]);
    }

    private static void build(ServerLevel level, int[] arena, Wall wall) {
        int wx = wallX(arena);
        if (wall == Wall.POT) {
            BlockPos pos = hitPos(arena);
            level.setBlockAndUpdate(pos, Blocks.DECORATED_POT.defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot)) {
                throw new IllegalStateException("Topf ohne Block-Entity");
            }
            Optional<ItemStackTemplate> brick = Optional.of(new ItemStackTemplate(Items.BRICK));
            pot.applyComponentsFromItemStack(DecoratedPotBlockEntity.createDecoratedPotInstance(
                    new PotDecorations(brick, brick, brick, Optional.of(new ItemStackTemplate(Items.ANGLER_POTTERY_SHERD)))));
            pot.setTheItem(new ItemStack(Items.APPLE));
            return;
        }
        BlockState block = wall == Wall.GLASS ? Blocks.GLASS.defaultBlockState() : Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
        for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
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
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(hitPos(arena)).inflate(8.0))) {
            item.discard();
        }
    }

    private enum Wall {
        PANE(7.0 / 16.0, 9.0 / 16.0),
        GLASS(0.0, 1.0),
        POT(1.0 / 16.0, 15.0 / 16.0);

        private final double front;
        private final double back;

        Wall(double front, double back) {
            this.front = front;
            this.back = back;
        }

        double front() {
            return this.front;
        }

        double back() {
            return this.back;
        }
    }

    private record Scenario(String what, DroneBuild build, double speed, boolean rule, Wall wall, boolean breaks, @Nullable Boolean crash,
                            @Nullable GameType mode, @Nullable String shot) {
    }

    private record Broken(BlockPos pos, BlockState state, BreakMath.Impact impact, UUID pilot) {
    }
}
