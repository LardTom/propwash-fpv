package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateCollisions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceLobby;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.RecordGhosts;
import de.lardtom.propwash.race.StartLights;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

public final class RaceSelfTests {
    private static final int GATE_COUNT = 3;
    private static final int SPACING = 10;
    private static final long MS = 1_000_000L;
    private static final String FUTURE = "future";
    private static final AtomicIntegerArray GATE_IDS = new AtomicIntegerArray(new int[]{-1, -1, -1});
    private static final AtomicInteger COURSE = new AtomicInteger(GateEntity.NO_COURSE);
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<String> SERVER_RESULT = new AtomicReference<>();
    private static final AtomicReference<List<UUID>> FAKE_PILOTS = new AtomicReference<>();
    private static final AtomicInteger LOBBY_DRONE = new AtomicInteger(-1);
    private static final AtomicReference<String> LOBBY_DEBUG = new AtomicReference<>("");
    private static final AtomicBoolean HEAT_SEEN = new AtomicBoolean();
    private static final String GHOST_FRAME = "race_5";

    private RaceSelfTests() {
    }

    static int gateId(int index) {
        return GATE_IDS.get(index);
    }

    static int courseId() {
        return COURSE.get();
    }

    static int gateCount() {
        return GATE_COUNT;
    }

    public static void register() {
        SelfTest.clientStep("race: Rezepte für Gates und Kurs-Werkzeug", 100, RaceSelfTests::recipeStep);
        SelfTest.clientStep("race: Gates platzieren und nummerieren", 200, RaceSelfTests::placeStep);
        SelfTest.clientStep("race: Gate-Markierungen folgen der Nummerierung", 300, RaceLineSelfTests::markingStep);
        SelfTest.clientStep("race: Kollision Pfosten und Öffnung", 20, RaceSelfTests::collisionStep);
        SelfTest.clientStep("race: Zielen durch die Gate-Öffnung", 20, RaceSelfTests::pickStep);
        SelfTest.clientStep("race: Gates nur mit Schleichen abbauen", 100, RaceSelfTests::breakStep);
        SelfTest.clientStep("race: Runden, Splits und Gate-Farben", 20, RaceSelfTests::lapStep);
        SelfTest.clientStep("race: Geist der Bestrunde spielt ab", 20, RaceSelfTests::ghostReplayStep);
        SelfTest.clientStep("race: Ideallinie nur im gestarteten Rennen", 20, RaceLineSelfTests::raceLineStep);
        SelfTest.clientStep("race: Bestenliste und Plausibilität", 200, RaceSelfTests::leaderboardStep);
        SelfTest.clientStep("race: Bestenliste sortiert Klassen und Zeiten", 100, RaceSelfTests::rankingStep);
        SelfTest.clientStep("race: Bestenliste im Theme-Fenster", 200, RaceSelfTests::leaderboardScreenStep);
        SelfTest.clientStep("race: Startaufstellung, Ampel und Frühstart", 400, RaceSelfTests::lobbyStep);
        SelfTest.clientStep("race: Rennuhr steht in der Pause", 200, RaceSelfTests::pauseStep);
        SelfTest.clientStep("race: Aufräumen", 200, RaceSelfTests::cleanupStep);
        WorldCourseSelfTests.register();
        GateStackSelfTests.register();
        GateMountSelfTests.register();
        GateKeySelfTests.register();
        GateFreeSelfTests.register();
        CourseElementSelfTests.register();
        FreeformGateSelfTests.register();
        GateRedstoneSelfTests.register();
        GatePoleSelfTests.register();
        CourseEditSelfTests.register();
        SelfTest.afterLeaveStep("race: Zustand nach dem Verlassen", 20, ctx -> StepResult.check(
                !RaceState.INSTANCE.racing() && RaceState.INSTANCE.courseId() == LapTracker.NO_COURSE && ClientCourses.all().isEmpty(),
                "Rennzustand nach dem Verlassen nicht zurückgesetzt: racing=" + RaceState.INSTANCE.racing() + " kurs="
                        + RaceState.INSTANCE.courseId() + " kurse=" + ClientCourses.all().size()));
    }

    private static StepResult future(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static StepResult recipeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                List<String> missing = new ArrayList<>();
                for (Identifier id : List.of(ContentIds.GATE_SMALL, ContentIds.GATE_MEDIUM, ContentIds.GATE_LARGE, ContentIds.RACE_FLAG, ContentIds.SLALOM_POLE,
                        ContentIds.DIVE_CUBE, ContentIds.FREEFORM_GATE, ContentIds.GATE_POLE, ContentIds.COURSE_TOOL)) {
                    if (server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).isEmpty()) {
                        missing.add("Rezept " + id);
                    }
                    String folder = id.equals(ContentIds.COURSE_TOOL) ? "tools" : "building_blocks";
                    if (server.getAdvancements().get(Constants.id("recipes/" + folder + "/" + id.getPath())) == null) {
                        missing.add("Freischaltung " + id);
                    }
                }
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("fehlt: " + missing);
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        return waiting != null ? waiting : StepResult.pass();
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(RaceSelfTests::placeGates));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return StepResult.pending();
        }
        int course = COURSE.get();
        String problem = null;
        for (int i = 0; i < GATE_COUNT; i++) {
            Entity entity = minecraft.level.getEntity(GATE_IDS.get(i));
            if (!(entity instanceof GateEntity gate)) {
                problem = "Gate " + i + " nicht auf dem Client";
            } else if (gate.courseId() != course || gate.gateIndex() != i || gate.size() != GateSize.MEDIUM || gate.facingYaw() != 0.0F) {
                problem = "Gate " + i + " falsch synchronisiert: kurs=" + gate.courseId() + " index=" + gate.gateIndex() + " größe="
                        + gate.size() + " yaw=" + gate.facingYaw();
            }
        }
        CourseData data = ClientCourses.get(course);
        if (problem == null && (data == null || data.gates().size() != GATE_COUNT)) {
            problem = "Kurs " + course + " nicht synchronisiert: " + data;
        }
        if (problem != null) {
            return ctx.tick() > 150 ? StepResult.fail(problem) : StepResult.pending();
        }
        ctx.log("Kurs " + course + " mit " + GATE_COUNT + " Gates auf dem Client");
        return StepResult.pass();
    }

    private static void placeGates(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        ItemStack previous = player.getMainHandItem().copy();
        player.setYRot(0.0F);
        player.setShiftKeyDown(false);
        ItemStack gates = new ItemStack(RaceModule.GATE_MEDIUM.get(), GATE_COUNT);
        player.setItemInHand(InteractionHand.MAIN_HAND, gates);
        int x = player.getBlockX() + 12;
        int z0 = player.getBlockZ() + 6;
        AABB area = new AABB(x - 4, level.getMinY(), z0 - 4, x + 5, level.getMaxY(), z0 + SPACING * GATE_COUNT + 4);
        for (GateEntity old : level.getEntitiesOfClass(GateEntity.class, area)) {
            old.discard();
        }
        List<GateEntity> placed = new ArrayList<>();
        try {
            for (int i = 0; i < GATE_COUNT; i++) {
                int z = z0 + i * SPACING;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, top - 1, z);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
                InteractionResult result = gates.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) {
                    throw new IllegalStateException("Gate " + i + " nicht platziert: " + result);
                }
                List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(ground.above()).inflate(0.6));
                if (found.size() != 1) {
                    throw new IllegalStateException("Gate " + i + " nicht gefunden: " + found.size());
                }
                placed.add(found.getFirst());
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        CourseToolItem.useOnGate(tool, player, placed.get(1));
        int course = CourseToolItem.courseOf(tool);
        CourseRegistry.Course data = registry.course(course);
        if (data == null || data.gates.size() != GATE_COUNT) {
            throw new IllegalStateException("Kurs " + course + " hat nicht " + GATE_COUNT + " Gates");
        }
        for (int i = 0; i < GATE_COUNT; i++) {
            GateEntity gate = placed.get(i);
            if (gate.courseId() != course || gate.gateIndex() != i || !data.gates.get(i).uuid.equals(gate.getUUID().toString())) {
                throw new IllegalStateException("Gate " + i + " falsch nummeriert: " + gate.courseId() + "/" + gate.gateIndex());
            }
            GATE_IDS.set(i, gate.getId());
        }
        COURSE.set(course);
    }

    private static StepResult collisionStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(GATE_IDS.get(0)) instanceof GateEntity gate)) {
            return StepResult.fail("Gate 0 fehlt auf dem Client");
        }
        if (gate.canBeCollidedWith(null)) {
            return StepResult.fail("Gate meldet canBeCollidedWith = true (Öffnung wäre zu)");
        }
        GateGeometry geometry = gate.geometry();
        double distance = 4.0;
        AABB opening = droneBox(geometry.x(), geometry.centerY(), geometry.z() - 2.0);
        double free = sweepZ(minecraft, opening, distance);
        if (Math.abs(free - distance) > 1.0E-6) {
            return StepResult.fail("Drohne durch die Öffnung gebremst: " + free);
        }
        double postOffset = geometry.width() * 0.5 - geometry.frame() * 0.5;
        double post = sweepZ(minecraft, opening.move(-postOffset, 0.0, 0.0), distance);
        double expected = 2.0 - 0.25 - geometry.depth() * 0.5;
        if (Math.abs(post - expected) > 1.0E-3) {
            return StepResult.fail("Drohne nicht am Pfosten gestoppt: " + post + " statt " + expected);
        }
        double bar = sweepZ(minecraft, opening.move(0.0, geometry.height() * 0.5 - geometry.frame() * 0.5, 0.0), distance);
        if (bar > expected + 1.0E-3) {
            return StepResult.fail("Drohne nicht am oberen Balken gestoppt: " + bar);
        }
        List<AABB> boxes = new ArrayList<>();
        gate.collectColliders(gate.getBoundingBox().inflate(1.0), boxes);
        if (boxes.size() != 4) {
            return StepResult.fail("Erwartet 4 Kollisionsboxen, bekommen " + boxes.size());
        }
        ctx.log("Öffnung frei (" + free + "), Pfosten stoppt nach " + post);
        return StepResult.pass();
    }

    private static StepResult pickStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !(minecraft.level.getEntity(GATE_IDS.get(0)) instanceof GateEntity gate)) {
            return StepResult.fail("Gate 0 fehlt auf dem Client");
        }
        GateGeometry geometry = gate.geometry();
        Vec3 from = new Vec3(geometry.x(), geometry.centerY(), geometry.z() - 3.0);
        Vec3 to = new Vec3(geometry.x(), geometry.centerY(), geometry.z() + 3.0);
        EntityHitResult opening = pick(minecraft, from, to);
        if (opening != null && opening.getEntity() instanceof GateEntity) {
            return StepResult.fail("Blick durch die Öffnung trifft das Gate bei " + opening.getLocation());
        }
        double postOffset = geometry.width() * 0.5 - geometry.frame() * 0.5;
        EntityHitResult post = pick(minecraft, from.add(-postOffset, 0.0, 0.0), to.add(-postOffset, 0.0, 0.0));
        if (post == null || post.getEntity() != gate) {
            return StepResult.fail("Blick auf den Pfosten trifft das Gate nicht: " + (post == null ? "nichts" : post.getEntity()));
        }
        double expectedZ = geometry.z() - geometry.depth() * 0.5;
        if (Math.abs(post.getLocation().z - expectedZ) > 1.0E-3) {
            return StepResult.fail("Treffer nicht auf der Pfostenfläche: " + post.getLocation());
        }
        return StepResult.pass();
    }

    private static EntityHitResult pick(Minecraft minecraft, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(minecraft.player, from, to, box, EntitySelector.CAN_BE_PICKED, from.distanceToSqr(to));
    }

    private static StepResult breakStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> SERVER_RESULT.set(checkBreaking(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = SERVER_RESULT.get();
        return result == null ? StepResult.pass() : StepResult.fail(result);
    }

    private static String checkBreaking(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            return "Gate nicht erzeugt";
        }
        gate.setSize(GateSize.SMALL);
        gate.snapTo(player.getX() - 12.0, player.getY(), player.getZ() - 12.0, 0.0F, 0.0F);
        if (!level.addFreshEntity(gate)) {
            return "Gate nicht gespawnt";
        }
        ItemStack previous = player.getMainHandItem().copy();
        boolean sneaking = player.isShiftKeyDown();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            if (gate.hurtServer(level, level.damageSources().playerAttack(player), 1.0F) || gate.isRemoved()) {
                return "Gate ohne Schleichen abgebaut";
            }
            player.setShiftKeyDown(true);
            if (!gate.hurtServer(level, level.damageSources().playerAttack(player), 1.0F) || !gate.isRemoved()) {
                return "Gate mit Schleichen nicht abgebaut";
            }
            return null;
        } finally {
            player.setShiftKeyDown(sneaking);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            if (!gate.isRemoved()) {
                gate.discard();
            }
        }
    }

    private static AABB droneBox(double x, double y, double z) {
        return new AABB(x - 0.25, y - 0.075, z - 0.25, x + 0.25, y + 0.075, z + 0.25);
    }

    private static double sweepZ(Minecraft minecraft, AABB box, double distance) {
        List<AABB> boxes = new ArrayList<>();
        GateCollisions.collect(minecraft.level, box.expandTowards(0.0, 0.0, distance), boxes);
        List<VoxelShape> shapes = new ArrayList<>();
        for (AABB b : boxes) {
            shapes.add(Shapes.create(b));
        }
        return Shapes.collide(Direction.Axis.Z, box, shapes, distance);
    }

    private static final class Flight {
        private final ClientRaceTracker tracker;
        long now;
        Vec3 position;

        Flight(ClientRaceTracker tracker, long now, Vec3 start) {
            this.tracker = tracker;
            this.now = now;
            this.position = start;
            tracker.feed(now, start);
        }

        void to(double x, double y, double z, double speed) {
            Vec3 target = new Vec3(x, y, z);
            Vec3 from = this.position;
            double length = from.distanceTo(target);
            int steps = Math.max(1, (int) Math.ceil(length / 0.25));
            long stepNanos = Math.round(length / speed / steps * 1.0E9);
            for (int i = 1; i <= steps; i++) {
                this.now += stepNanos;
                this.position = from.lerp(target, i / (double) steps);
                this.tracker.feed(this.now, this.position);
            }
        }
    }

    private static StepResult lapStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        CourseData course = ClientCourses.get(COURSE.get());
        if (course == null || minecraft.level == null) {
            return StepResult.fail("Kurs fehlt auf dem Client");
        }
        List<GateGeometry> gates = ClientCourses.geometry(course);
        GateEntity[] entities = new GateEntity[GATE_COUNT];
        for (int i = 0; i < GATE_COUNT; i++) {
            if (!(minecraft.level.getEntity(GATE_IDS.get(i)) instanceof GateEntity gate)) {
                return StepResult.fail("Gate " + i + " fehlt auf dem Client");
            }
            entities[i] = gate;
        }
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean report = config.reportResults;
        config.reportResults = false;
        List<LapTracker.Event> events = new ArrayList<>();
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        tracker.setEventListener(events::add);
        try {
            GateGeometry first = gates.getFirst();
            GateGeometry last = gates.getLast();
            double x = first.x();
            double y = first.centerY();
            double zStart = first.z() - 4.0;
            double zEnd = last.z() + 4.0;
            Vec3 start = new Vec3(x, y, zStart);
            tracker.tracker().setLapTarget(2);
            tracker.selectCourseAt(minecraft, start);
            if (tracker.courseId() != course.id()) {
                return StepResult.fail("Kurs nicht gewählt: " + tracker.courseId());
            }
            tracker.ghosts().setPersist(false);
            tracker.ghosts().clear();
            tracker.syncGhostsFor(minecraft, GHOST_FRAME);
            Flight flight = new Flight(tracker, tracker.now(), start);
            double[] speeds = {10.0, 15.0, 10.0};
            String colorProblem = null;
            for (int loop = 0; loop < speeds.length; loop++) {
                double speed = speeds[loop];
                flight.to(x, y, gates.get(1).z() + 1.0, speed);
                if (loop == 1) {
                    int next = GateRenderer.lightColor(entities[2], 0.0F);
                    int passed = GateRenderer.lightColor(entities[1], 0.0F);
                    if (next != GateRenderer.LIGHT_GREEN || passed != GateRenderer.LIGHT_WHITE || RaceState.INSTANCE.nextGate() != 2) {
                        colorProblem = "Gate-Farben falsch: nächstes=" + Integer.toHexString(next) + " passiert=" + Integer.toHexString(passed)
                                + " nextGate=" + RaceState.INSTANCE.nextGate();
                    }
                }
                flight.to(x, y, zEnd, speed);
                flight.to(x + 8.0, y, zEnd, speed);
                flight.to(x + 8.0, y, zStart, speed);
                flight.to(x, y, zStart, speed);
            }
            if (colorProblem != null) {
                return StepResult.fail(colorProblem);
            }
            double loopLength = (zEnd - zStart) * 2.0 + 16.0;
            double approach = first.z() - zStart;
            long lap1 = Math.round(((loopLength - approach) / 10.0 + approach / 15.0) * 1.0E9);
            long lap2 = Math.round(((loopLength - approach) / 15.0 + approach / 10.0) * 1.0E9);
            List<LapTracker.Event> laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).toList();
            long finishes = events.stream().filter(e -> e.type() == LapTracker.EventType.FINISH).count();
            long starts = events.stream().filter(e -> e.type() == LapTracker.EventType.START).count();
            List<LapTracker.Event> gateEvents = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).toList();
            if (starts != 1 || laps.size() != 2 || finishes != 1 || gateEvents.size() != 4) {
                return StepResult.fail("Ereignisse falsch: start=" + starts + " runden=" + laps.size() + " ziel=" + finishes + " gates="
                        + gateEvents.size());
            }
            if (Math.abs(laps.get(0).valueNanos() - lap1) > MS || Math.abs(laps.get(1).valueNanos() - lap2) > MS) {
                return StepResult.fail("Rundenzeiten falsch: " + laps.get(0).valueNanos() + "/" + laps.get(1).valueNanos() + " erwartet "
                        + lap1 + "/" + lap2);
            }
            if (gateEvents.get(2).deltaNanos() >= 0L || gateEvents.get(3).deltaNanos() >= 0L || laps.get(1).deltaNanos() >= 0L) {
                return StepResult.fail("Split-Deltas der schnelleren Runde nicht negativ: " + gateEvents.get(2).deltaNanos() + ", "
                        + gateEvents.get(3).deltaNanos() + ", " + laps.get(1).deltaNanos());
            }
            RaceState state = RaceState.INSTANCE;
            if (state.racing() || !state.finished() || Math.abs(state.bestLapNanos() - lap2) > MS || Math.abs(state.lastLapNanos() - lap2) > MS
                    || Math.abs(state.totalNanos() - (lap1 + lap2)) > 2 * MS) {
                return StepResult.fail("RaceState nach dem Ziel falsch: racing=" + state.racing() + " beste=" + state.bestLapNanos()
                        + " letzte=" + state.lastLapNanos() + " gesamt=" + state.totalNanos());
            }
            GhostTrack personal = tracker.ghosts().personal();
            if (personal == null || Math.abs(personal.lapNanos() - lap2) > MS || personal.samples() < 10 || !GHOST_FRAME.equals(personal.frame())
                    || personal.layout() != course.layoutHash()) {
                return StepResult.fail("Geist der Bestrunde fehlt: " + (personal == null ? "keiner" : personal.lapNanos() + " ns, "
                        + personal.samples() + " Proben, Klasse " + personal.frame()));
            }
            ctx.log("Runden " + laps.get(0).valueNanos() / 1.0E9 + " s und " + laps.get(1).valueNanos() / 1.0E9 + " s, Ziel erreicht");
            return StepResult.pass();
        } finally {
            tracker.setEventListener(null);
            config.reportResults = report;
        }
    }

    private static StepResult ghostReplayStep(SelfTest.StepContext ctx) {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        CourseData course = ClientCourses.get(COURSE.get());
        GhostTrack personal = tracker.ghosts().personal();
        if (course == null || personal == null) {
            return StepResult.fail("Geist oder Kurs fehlt");
        }
        List<GateGeometry> gates = ClientCourses.geometry(course);
        double[] position = new double[3];
        Quaternionf rotation = new Quaternionf();
        GateGeometry first = gates.getFirst();
        if (!personal.pose(0L, position, rotation) || Math.abs(position[2] - first.z()) > 0.5 || Math.abs(position[0] - first.x()) > 0.1) {
            return StepResult.fail("Geist beginnt nicht am Start-Gate: " + position[0] + "/" + position[2]);
        }
        long[] splits = personal.splits(gates);
        if (!GhostTrack.completeSplits(splits) || !(splits[1] < splits[2]) || splits[0] != personal.lapNanos()) {
            return StepResult.fail("Zwischenzeiten des Geists falsch: " + Arrays.toString(splits));
        }
        if (!personal.pose(splits[1], position, rotation) || Math.abs(position[2] - gates.get(1).z()) > 0.3) {
            return StepResult.fail("Geist steht bei Zwischenzeit 1 nicht am Gate 2: z=" + position[2] + " statt " + gates.get(1).z());
        }
        try {
            GhostTrack decoded = GhostCodec.decode(GhostCodec.encode(personal));
            decoded.pose(splits[2], position, rotation);
            if (Math.abs(position[2] - gates.get(2).z()) > 0.3 || decoded.samples() != personal.samples()) {
                return StepResult.fail("Geist nach dem Kodieren verschoben: z=" + position[2]);
            }
        } catch (IOException e) {
            return StepResult.fail("Geist nicht dekodierbar: " + e.getMessage());
        }
        GhostLook shown = GhostRenderer.lookFor(personal);
        if (personal.look() == null ? !shown.build().frame().key().equals(GHOST_FRAME) : !shown.equals(personal.look())) {
            return StepResult.fail("Geist zeigt die falsche Drohne: " + shown.build().frame().key() + " statt " + GHOST_FRAME);
        }
        GhostLook whoop = GhostLook.of(DroneBuild.WHOOP, DronePaint.DEFAULT.withAllProps(0xFF7A00));
        try {
            GhostTrack withLook = GhostCodec.decode(GhostCodec.encode(personal.withLook(whoop)));
            if (!whoop.equals(withLook.look()) || GhostRenderer.lookFor(withLook) != withLook.look()) {
                return StepResult.fail("Bauplan des Geists geht beim Kodieren verloren: " + withLook.look());
            }
        } catch (IOException e) {
            return StepResult.fail("Geist mit Bauplan nicht dekodierbar: " + e.getMessage());
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("propwash-ghosts");
            GhostStore.overrideRoot(dir);
            Minecraft minecraft = Minecraft.getInstance();
            Path file = GhostStore.file(minecraft, course.id(), GHOST_FRAME);
            if (file == null) {
                return StepResult.fail("Kein Speicherort für Geister in dieser Welt");
            }
            GhostStore.write(file, GhostCodec.encode(personal));
            GhostTrack loaded = GhostStore.load(minecraft, course.id(), GHOST_FRAME);
            if (loaded == null || loaded.lapNanos() != personal.lapNanos()) {
                return StepResult.fail("Geist nicht von der Platte geladen");
            }
        } catch (IOException e) {
            return StepResult.fail("Temporärer Ordner fehlt: " + e.getMessage());
        } finally {
            GhostStore.overrideRoot(null);
        }
        long[] reference = tracker.ghosts().referenceSplits();
        if (reference.length != gates.size() || reference[0] != personal.lapNanos()) {
            return StepResult.fail("Split-Referenz nicht aus dem Geist: " + Arrays.toString(reference));
        }
        ctx.log("Geist " + personal.samples() + " Proben, " + GhostCodec.encode(personal).length + " Bytes, Splits " + Arrays.toString(splits));
        return StepResult.pass();
    }

    private static StepResult leaderboardStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> SERVER_RESULT.set(checkLeaderboard(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = SERVER_RESULT.get();
        return result == null ? StepResult.pass() : StepResult.fail(result);
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos, long total) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, total);
    }

    private static String checkLeaderboard(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        int id = COURSE.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(id);
        if (course == null) {
            return "Kurs " + id + " fehlt auf dem Server";
        }
        long base = System.nanoTime() + 60_000L * MS;
        RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, base);
        if (verdict != RaceServer.Verdict.NOT_PILOTING) {
            return "Meldung ohne Drohne nicht abgelehnt: " + verdict;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        drone.snapTo(start.x, start.y + 1.5, start.z - 3.0, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        DRONE.set(drone.getId());
        try {
            return verifyReports(registry, course, player, base);
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    private static void passGate(ServerPlayer player, GateGeometry gate, long nanos) {
        Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
        Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
        RaceServer.onDroneMoved(player, center.subtract(normal), center.add(normal), nanos);
    }

    private static long flyLap(ServerPlayer player, List<GateGeometry> gates, long startNanos, long lapNanos) {
        passGate(player, gates.getFirst(), startNanos);
        for (int i = 1; i < gates.size(); i++) {
            passGate(player, gates.get(i), startNanos + lapNanos * i / gates.size());
        }
        passGate(player, gates.getFirst(), startNanos + lapNanos);
        return startNanos + lapNanos;
    }

    private static String verifyReports(CourseRegistry registry, CourseRegistry.Course course, ServerPlayer player, long base) {
        int id = course.id;
        registry.setLaps(course, 1);
        List<GateGeometry> gates = course.toData().geometry();
        long minimum = RaceServer.minimumLapNanos(course, player);
        long lap = Math.max(minimum * 3L, 6_000L * MS);
        long t = base + 5_000L * MS;
        RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Start abgelehnt: " + verdict;
        }
        t += lap + 200L * MS;
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L), player, t);
        if (verdict != RaceServer.Verdict.PENDING) {
            return "Nicht geflogene Runde nicht zurückgehalten: " + verdict;
        }
        t += RaceServer.PENDING_TIMEOUT_NANOS + 100L * MS;
        verdict = RaceServer.flush(player, t);
        if (verdict != RaceServer.Verdict.UNVERIFIED || !registry.board(course).top(null, 10).isEmpty()) {
            return "Nicht geflogene Runde angenommen: " + verdict;
        }
        t += 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        long crossed = flyLap(player, gates, t + 100L * MS, lap);
        t = crossed + 100L * MS;
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L), player, t);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Geflogene Runde abgelehnt: " + verdict + " (minimum " + minimum + ")";
        }
        List<Leaderboard.Entry> ranking = registry.board(course).top(null, 10);
        if (ranking.isEmpty() || ranking.getFirst().nanos() != lap || !ranking.getFirst().player().equals(player.getUUID())) {
            return "Bestenliste nicht aktualisiert";
        }
        QuadcopterEntity piloted = RaceServer.pilotedDrone(player);
        String frame = piloted == null ? "" : piloted.build().frame().key();
        if (!ranking.getFirst().frame().equals(frame) || ranking.getFirst().dateMillis() <= 0L) {
            return "Klasse oder Datum fehlen: " + ranking.getFirst();
        }
        RecordGhosts.Stored stored = registry.ghosts().get(id, frame);
        if (stored == null || stored.nanos() != lap || stored.layout() != course.toData().layoutHash()) {
            return "Rekord-Geist nicht gespeichert: " + stored;
        }
        try {
            GhostTrack ghost = GhostCodec.decode(stored.data());
            double[] pos = new double[3];
            if (ghost.samples() < 10 || !ghost.pose(0L, pos, new Quaternionf()) || Math.abs(pos[2] - gates.getFirst().z()) > 1.5) {
                return "Rekord-Geist unbrauchbar: " + ghost.samples() + " Proben, Start z=" + pos[2];
            }
            if (piloted != null && !GhostLook.of(piloted.build(), piloted.paint()).equals(ghost.look())) {
                return "Rekord-Geist ohne den Bauplan der Drohne: " + ghost.look();
            }
        } catch (IOException e) {
            return "Rekord-Geist nicht lesbar: " + e.getMessage();
        }
        crossed = flyLap(player, gates, crossed, lap);
        t = crossed + 100L * MS;
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 2, lap / 2L, 0L), player, t);
        if (verdict != RaceServer.Verdict.IMPLAUSIBLE_TIME) {
            return "Geschönte Rundenzeit nicht abgelehnt: " + verdict;
        }
        t += 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        t += Math.max(1L, minimum / 2L);
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, Math.max(1L, minimum / 2L), 0L), player, t);
        if (verdict != RaceServer.Verdict.IMPLAUSIBLE_TIME) {
            return "Zu schnelle Runde nicht abgelehnt: " + verdict;
        }
        t += 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        t += lap;
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 3, lap, 0L), player, t);
        if (verdict != RaceServer.Verdict.OUT_OF_SEQUENCE) {
            return "Runde außer der Reihe nicht abgelehnt: " + verdict;
        }
        t += 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        crossed = flyLap(player, gates, t + 100L * MS, lap + 1_000L * MS);
        t = crossed + 100L * MS;
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap + 1_000L * MS, 0L), player, t);
        if (verdict != RaceServer.Verdict.ACCEPTED || registry.board(course).top(null, 1).getFirst().nanos() != lap) {
            return "Langsamere Runde falsch verarbeitet: " + verdict;
        }
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.FINISH, id, 1, 0L, lap + 1_000L * MS), player, t);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Zielmeldung abgelehnt: " + verdict;
        }
        t += 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        crossed = flyLap(player, gates, t + 100L * MS, lap / 2L + lap / 4L);
        t = crossed + 100L * MS;
        verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, id, 1, lap / 2L + lap / 4L, 0L,
                RacePayloads.RaceReportC2S.FLAG_ASSISTED, 0L), player, t);
        if (verdict != RaceServer.Verdict.ACCEPTED || registry.board(course).top(null, 1).getFirst().nanos() != lap) {
            return "Runde mit Flughilfe in der Bestenliste: " + verdict;
        }
        t += 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
        crossed = flyLap(player, gates, t + 100L * MS, lap);
        t = crossed + 100L * MS;
        verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, id, 1, lap + LapTracker.FALSE_START_PENALTY_NANOS, 0L,
                0, 1_000L * MS), player, t);
        if (verdict != RaceServer.Verdict.IMPLAUSIBLE_TIME) {
            return "Erfundene Frühstart-Strafe nicht abgelehnt: " + verdict;
        }
        t += 10_000L * MS;
        RaceServer.Verdict last = RaceServer.Verdict.ACCEPTED;
        for (int i = 0; i < 12; i++) {
            last = RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t + i);
        }
        if (last != RaceServer.Verdict.RATE_LIMITED) {
            return "Meldungsflut nicht begrenzt: " + last;
        }
        registry.markDirty();
        registry.save();
        try {
            String json = Files.readString(registry.path());
            if (!json.contains(player.getUUID().toString())) {
                return "Kurs nicht gespeichert";
            }
        } catch (IOException e) {
            return "Renn-Daten nicht lesbar: " + e.getMessage();
        }
        Tag tag = RaceRecords.TYPE.codec().encodeStart(NbtOps.INSTANCE, registry.records()).getOrThrow();
        RaceRecords copy = RaceRecords.TYPE.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
        Leaderboard.Entry saved = copy.board(id).best(player.getUUID(), null);
        if (saved == null || saved.nanos() != lap) {
            return "Bestenliste nicht speicherbar: " + saved;
        }
        player.level().getServer().getDataStorage().saveAndJoin();
        Path file = player.level().getServer().getWorldPath(LevelResource.DATA).resolve("propwash").resolve("race_records.dat");
        if (!Files.isRegularFile(file)) {
            return "Weltdaten der Bestenliste fehlen: " + file;
        }
        return null;
    }

    private static StepResult rankingStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> SERVER_RESULT.set(checkRanking(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = SERVER_RESULT.get();
        return result == null ? StepResult.pass() : StepResult.fail(result);
    }

    private static String checkRanking(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(COURSE.get());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        Leaderboard board = registry.board(course);
        long own = board.best(player.getUUID(), null) == null ? -1L : board.best(player.getUUID(), null).nanos();
        if (own <= 0L) {
            return "Eigene Zeit fehlt";
        }
        long now = System.currentTimeMillis();
        List<UUID> fakes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID fake = new UUID(0x5E1F7E57L, i);
            fakes.add(fake);
            board.submit(fake, "Test" + i, i % 2 == 0 ? "whoop_65" : "race_5", own + (i - 3) * 250L * MS + 1L, now - i);
        }
        registry.records().changed();
        List<Leaderboard.Entry> top = board.top(null, Leaderboard.TOP);
        for (int i = 1; i < top.size(); i++) {
            if (top.get(i).nanos() < top.get(i - 1).nanos()) {
                return "Bestenliste nicht nach Zeit sortiert: " + top;
            }
        }
        if (top.size() != Leaderboard.TOP || !top.getFirst().name().equals("Test0")) {
            return "Rekordhalter falsch: " + (top.isEmpty() ? "keiner" : top.getFirst().name());
        }
        int rank = board.rank(player.getUUID(), null);
        if (rank != 4) {
            return "Eigener Platz " + rank + " statt 4";
        }
        for (Leaderboard.Entry entry : board.top("whoop_65", Leaderboard.TOP)) {
            if (!entry.frame().equals("whoop_65")) {
                return "Klassenfilter liefert " + entry.frame();
            }
        }
        RacePayloads.LeaderboardS2C payload = RaceServer.leaderboardPayload(board, course, player.getUUID(), false);
        if (payload.rows().stream().noneMatch(RacePayloads.BoardRow::self) || payload.rows().stream().noneMatch(r -> r.overallRank() == 1)) {
            return "Bestenlisten-Paket ohne eigene Zeile oder Platz 1";
        }
        if (!RaceServer.sendLeaderboard(player, course.id, false)) {
            return "Bestenliste nicht an den Client geschickt";
        }
        FAKE_PILOTS.set(fakes);
        return null;
    }

    private static StepResult leaderboardScreenStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        int course = COURSE.get();
        if (ctx.tick() == 0) {
            minecraft.gui.setScreen(new LeaderboardScreen(null, course));
            return StepResult.pending();
        }
        LeaderboardScreen screen = LeaderboardScreen.active();
        if (screen == null || ClientLeaderboards.get(course) == null || screen.frames() < 2) {
            return ctx.tick() > 150 ? StepResult.fail("Bestenliste nicht geladen: fenster=" + (screen != null) + " daten="
                    + (ClientLeaderboards.get(course) != null)) : StepResult.pending();
        }
        if (!(minecraft.gui.screen() instanceof ThemedScreen)) {
            return StepResult.fail("Bestenliste ist kein Theme-Fenster");
        }
        List<RacePayloads.BoardRow> rows = screen.shownRows();
        if (rows.size() != Leaderboard.TOP) {
            minecraft.gui.setScreen(null);
            return StepResult.fail("Bestenliste zeigt " + rows.size() + " Zeilen");
        }
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).nanos() < rows.get(i - 1).nanos() || rows.get(i).overallRank() != i + 1) {
                minecraft.gui.setScreen(null);
                return StepResult.fail("Bestenliste im Fenster nicht sortiert");
            }
        }
        if (rows.stream().noneMatch(RacePayloads.BoardRow::self)) {
            minecraft.gui.setScreen(null);
            return StepResult.fail("Eigene Zeit nicht in der Liste");
        }
        screen.selectFilter("whoop_65");
        List<RacePayloads.BoardRow> whoop = screen.shownRows();
        minecraft.gui.setScreen(null);
        if (whoop.isEmpty() || whoop.stream().anyMatch(r -> !r.frame().equals("whoop_65"))) {
            return StepResult.fail("Klassenfilter im Fenster falsch: " + whoop.size());
        }
        return StepResult.pass();
    }

    private static StepResult lobbyStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            HEAT_SEEN.set(false);
            LOBBY_DEBUG.set("");
            ctx.state().put(FUTURE, ctx.onServer(RaceSelfTests::joinLobby));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (SERVER_RESULT.get() != null) {
            return StepResult.fail(SERVER_RESULT.get());
        }
        if (ctx.state().get("joined") == null) {
            QuadcopterEntity own = PilotSession.INSTANCE.isActive() ? PilotSession.INSTANCE.drone() : null;
            if (own == null || own.getId() != LOBBY_DRONE.get()) {
                return ctx.tick() > 100 ? StepResult.fail("Client übernimmt die Testdrohne nicht") : StepResult.pending();
            }
            ctx.state().put("joined", Boolean.TRUE);
            ctx.state().put("joinedAt", ctx.tick());
            ctx.state().put(FUTURE, ctx.onServer(RaceSelfTests::enterLobby));
            return StepResult.pending();
        }
        ClientRaceTracker client = ClientRaceTracker.INSTANCE;
        if (client.lobbyMember()) {
            ctx.state().put("seenLobby", Boolean.TRUE);
        }
        if (PilotSession.INSTANCE.isActive()) {
            ctx.state().put("piloted", Boolean.TRUE);
        }
        if (client.countdownActive() && client.countdownTotalNanos() == 3L * LapTracker.SECOND && client.courseId() == COURSE.get()) {
            ctx.state().put("countdown", Boolean.TRUE);
        }
        if (ctx.state().get("heat") == null) {
            if (HEAT_SEEN.get()) {
                ctx.state().put("heat", Boolean.TRUE);
                return StepResult.pending();
            }
            if (ctx.tick() - (Integer) ctx.state().get("joinedAt") > 200) {
                return StepResult.fail("Startaufstellung hat das Rennen nicht gestartet: " + LOBBY_DEBUG.get());
            }
            ctx.onServer(server -> {
                RaceLobby.Heat heat = RaceServer.heat(COURSE.get());
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                RaceLobby lobby = RaceServer.lobby(COURSE.get());
                if (heat != null && heat.pilots().contains(player.getUUID()) && lobby == null) {
                    HEAT_SEEN.set(true);
                }
                QuadcopterEntity piloted = RaceServer.pilotedDrone(player);
                String line = "Aufstellung " + (lobby == null ? "keine" : lobby.members().size()) + ", Rennen " + (heat == null ? "keins" : heat.pilots().size())
                        + ", Drohne " + (piloted == null ? "keine" : piloted.getId() + "@" + piloted.blockPosition()) + ", erwartet " + LOBBY_DRONE.get();
                if (!LOBBY_DEBUG.get().endsWith(line)) {
                    LOBBY_DEBUG.set(LOBBY_DEBUG.get() + " → " + line);
                }
            });
            return StepResult.pending();
        }
        if (ctx.state().get("piloted") != null && ctx.state().get("countdown") == null && ctx.state().get("cleaned") == null) {
            Integer since = (Integer) ctx.state().computeIfAbsent("heatTick", k -> ctx.tick());
            if (ctx.tick() - since < 60) {
                return StepResult.pending();
            }
            ctx.state().put("cleaned", ctx.onServer(RaceSelfTests::leaveLobby));
            return StepResult.fail("Client hat nach dem Start der Aufstellung keinen 3-s-Countdown begonnen");
        }
        if (ctx.state().get("cleaned") == null) {
            ctx.state().put("cleaned", ctx.onServer(RaceSelfTests::leaveLobby));
            return StepResult.pending();
        }
        CompletableFuture<?> cleaned = (CompletableFuture<?>) ctx.state().get("cleaned");
        if (!cleaned.isDone()) {
            return StepResult.pending();
        }
        LapTracker lights = new LapTracker();
        lights.setCourse(COURSE.get(), ClientCourses.geometry(ClientCourses.get(COURSE.get())), 0L);
        GateGeometry start = ClientCourses.geometry(ClientCourses.get(COURSE.get())).getFirst();
        lights.update(0L, start.x(), start.centerY(), start.z() - 2.0);
        lights.beginCountdown(0L, 3L * LapTracker.SECOND);
        if (StartLights.lit(3L * LapTracker.SECOND, lights.countdownRemainingNanos(0L)) != 1) {
            return StepResult.fail("Startampel beginnt nicht mit einer roten Lampe");
        }
        lights.update(1_000L * MS, start.x(), start.centerY(), start.z() + 1.0);
        List<LapTracker.Event> events = lights.drainEvents();
        if (events.stream().noneMatch(e -> e.type() == LapTracker.EventType.FALSE_START) || !lights.falseStarted()) {
            return StepResult.fail("Frühstart nicht erkannt: " + events);
        }
        lights.update(3_100L * MS, start.x(), start.centerY(), start.z() + 2.0);
        if (!lights.racing() || lights.penaltyNanos() != LapTracker.FALSE_START_PENALTY_NANOS || lights.nextGate() != 1) {
            return StepResult.fail("Frühstart-Strafe fehlt: " + lights.penaltyNanos());
        }
        if (ctx.state().get("seenLobby") == null) {
            ctx.log("Aufstellung auf dem Client nicht gesehen (Start kam vor dem Paket)");
        }
        return StepResult.pass();
    }

    private static void joinLobby(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(COURSE.get());
        if (course == null) {
            SERVER_RESULT.set("Kurs fehlt auf dem Server");
            return;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            SERVER_RESULT.set("Drohne nicht erzeugt");
            return;
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        drone.snapTo(start.x, start.y + 1.5, start.z - 3.0, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            SERVER_RESULT.set("Drohne nicht gespawnt");
            return;
        }
        LOBBY_DRONE.set(drone.getId());
    }

    private static void enterLobby(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(COURSE.get());
        if (course == null) {
            SERVER_RESULT.set("Kurs fehlt auf dem Server");
            return;
        }
        if (!RaceServer.joinLobby(player, course.id, 3, System.nanoTime())) {
            SERVER_RESULT.set("Aufstellen abgelehnt");
        } else if (RaceServer.lobby(course.id) == null || !RaceServer.lobby(course.id).contains(player.getUUID())) {
            SERVER_RESULT.set("Pilot nicht in der Startaufstellung");
        }
    }

    private static void leaveLobby(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity drone = level.getEntity(LOBBY_DRONE.get());
            if (drone instanceof QuadcopterEntity quad) {
                quad.setPilot(null);
                drone.discard();
            }
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(COURSE.get());
        List<UUID> fakes = FAKE_PILOTS.get();
        if (course != null && fakes != null) {
            for (int i = 0; i < fakes.size(); i++) {
                registry.board(course).removePlayer("Test" + i);
            }
            registry.records().changed();
        }
    }

    private static StepResult pauseStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        if (ctx.tick() == 0) {
            minecraft.pauseGame(false);
            return StepResult.pending();
        }
        Object frozen = ctx.state().get("frozen");
        if (frozen == null) {
            if (minecraft.isPaused() && tracker.paused()) {
                ctx.state().put("frozen", tracker.now());
                ctx.state().put("since", ctx.tick());
            } else if (ctx.tick() > 60) {
                minecraft.gui.setScreen(null);
                return StepResult.fail("Spiel nicht pausiert: isPaused=" + minecraft.isPaused() + " uhr=" + tracker.paused());
            }
            return StepResult.pending();
        }
        long frozenAt = (Long) frozen;
        int since = (Integer) ctx.state().get("since");
        if (ctx.state().get("resumed") == null) {
            if (tracker.now() != frozenAt) {
                minecraft.gui.setScreen(null);
                return StepResult.fail("Rennuhr läuft in der Pause weiter: " + (tracker.now() - frozenAt) / MS + " ms");
            }
            if (ctx.tick() - since < 20) {
                return StepResult.pending();
            }
            minecraft.gui.setScreen(null);
            ctx.state().put("resumed", Boolean.TRUE);
            return StepResult.pending();
        }
        if (!tracker.paused() && tracker.now() > frozenAt) {
            long drift = tracker.now() - frozenAt;
            return drift < 450L * MS ? StepResult.pass() : StepResult.fail("Pausenzeit mitgezählt: " + drift / MS + " ms");
        }
        return StepResult.pending();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ClientRaceTracker.INSTANCE.ghosts().setPersist(true);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    for (int i = 0; i < GATE_COUNT; i++) {
                        Entity gate = level.getEntity(GATE_IDS.get(i));
                        if (gate != null) {
                            gate.discard();
                        }
                    }
                    Entity drone = level.getEntity(DRONE.get());
                    if (drone != null) {
                        drone.discard();
                    }
                }
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course course = registry == null ? null : registry.course(COURSE.get());
                if (course == null || !course.gates.isEmpty()) {
                    throw new IllegalStateException("Entfernte Gates stehen noch im Kurs");
                }
                registry.deleteCourse(COURSE.get());
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean gone = minecraft.level != null;
        for (int i = 0; i < GATE_COUNT && gone; i++) {
            gone = minecraft.level.getEntity(GATE_IDS.get(i)) == null;
        }
        if (!gone || ClientCourses.get(COURSE.get()) != null) {
            return ctx.tick() > 150 ? StepResult.fail("Gates oder Kurs nach dem Aufräumen noch auf dem Client") : StepResult.pending();
        }
        return StepResult.pass();
    }
}
