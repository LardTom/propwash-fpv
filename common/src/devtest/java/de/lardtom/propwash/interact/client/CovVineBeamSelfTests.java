package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.CovVineArena;
import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovVineBeamSelfTests {
    static final String GROUP = "drone: Ranken-Balken – ";
    static final int SCENE_DX = CovVineArena.SCENE_DX;
    static final int BEAM_DX = CovVineArena.BEAM_DX;
    static final int BEAM_DY = CovVineArena.BEAM_DY;
    private static final int BEAM_HALF = CovVineArena.BEAM_HALF;
    private static final double LAUNCH_DX = -2.0;
    static final double PENETRATION = 0.01;
    private static final int WATCH_TICKS = 80;
    private static final int RESET_TICKS = 60;
    private static final int EMBED_TICKS = 60;
    private static final String BEAM_SHOT = "vine_beam_approach";
    private static final String RUN = "run";
    static final AtomicReference<int @Nullable []> BASE = new AtomicReference<>();

    private CovVineBeamSelfTests() {
    }

    enum Approach {
        MIDDLE("mitten auf den Balken", 0.5, 0.0, 0.0, 0.0),
        UNDER("unter der Balkenkante durch die hängenden Ranken", -0.12, 0.0, 0.0, 0.0),
        FROM_BELOW("von unten schräg gegen die Unterseite", -1.6, 20.0, 0.0, 0.0),
        FROM_ABOVE("von oben schräg auf die Oberkante", 2.2, -20.0, 0.0, 0.0),
        OBLIQUE("schräg von der Seite", 0.5, 0.0, 35.0, 0.0),
        PITCHED("mit 40° Neigung auf die Unterkante", -0.3, 0.0, 0.0, 40.0),
        ALONG("unter dem Balken entlang durch die Ranken", -0.14, -3.0, 90.0, 25.0);

        final String what;
        final double height;
        final double climbDeg;
        final double yawDeg;
        final double pitchDeg;

        Approach(String what, double height, double climbDeg, double yawDeg, double pitchDeg) {
            this.what = what;
            this.height = height;
            this.climbDeg = climbDeg;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
        }
    }

    record Rules(boolean breakBlocks, boolean crashDisarm) {
        String what() {
            return "Blockbruch " + (this.breakBlocks ? "an" : "aus") + ", Absturz entschärft " + (this.crashDisarm ? "an" : "aus");
        }
    }

    record Flight(Approach approach, double speed) {
    }

    enum Embed {
        GROUND("Drohne 3 cm im Boden, Gas 0 – kommt nach oben heraus statt im Erdreich zu versinken", -0.03, "vine_embedded_ground"),
        BLOCK("Steinblock in die schwebende Drohne gesetzt – weicht zur nächsten freien Seite aus", 2.4, "vine_embedded_block"),
        DEEP("Drohne 0,6 m tief im Grasblock, Gas 0 – steht danach auf dem Gras statt unter der Erde zu wandern", -0.6, "vine_embedded_deep");

        final String what;
        final double height;
        final String shot;

        Embed(String what, double height, String shot) {
            this.what = what;
            this.height = height;
            this.shot = shot;
        }
    }

    public static void register() {
        List<Rules> combos = List.of(new Rules(false, true), new Rules(true, false), new Rules(false, false), new Rules(true, true));
        List<Flight> flights = new ArrayList<>();
        for (double speed : new double[]{10.0, 18.0, 25.0}) {
            for (Approach approach : Approach.values()) {
                flights.add(new Flight(approach, speed));
            }
        }
        for (Rules rules : combos) {
            SelfTest.clientStep(GROUP + rules.what() + ": Anflug mit 10, 18 und 25 m/s aus sieben Richtungen, Gas 0 – nie im Block, "
                    + "FPV-Auge nie im Block", 20 * 400, ctx -> run(ctx, rules, List.copyOf(flights)));
        }
        Rules standard = new Rules(false, false);
        for (Embed embed : Embed.values()) {
            SelfTest.clientStep(GROUP + embed.what, 20 * 40, ctx -> run(ctx, standard, List.of(embed)));
        }
    }

    private static StepResult run(SelfTest.StepContext ctx, Rules rules, List<?> tasks) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(rules, tasks));
        return run.tick(ctx);
    }

    static double penetration(Level level, QuadcopterEntity drone) {
        AABB box = drone.getBoundingBox();
        if (level.noBlockCollision(drone, box.deflate(PENETRATION))) {
            return 0.0;
        }
        double worst = 0.0;
        for (VoxelShape shape : level.getBlockCollisions(drone, box)) {
            for (AABB part : shape.toAabbs()) {
                if (!part.intersects(box)) {
                    continue;
                }
                double dx = Math.min(box.maxX - part.minX, part.maxX - box.minX);
                double dy = Math.min(box.maxY - part.minY, part.maxY - box.minY);
                double dz = Math.min(box.maxZ - part.minZ, part.maxZ - box.minZ);
                worst = Math.max(worst, Math.min(dx, Math.min(dy, dz)));
            }
        }
        return worst;
    }

    static boolean eyeInside(Level level, Vec3 eye) {
        BlockPos pos = BlockPos.containing(eye);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        for (AABB part : state.getCollisionShape(level, pos).toAabbs()) {
            AABB placed = part.move(pos).deflate(0.002);
            if (placed.contains(eye)) {
                return true;
            }
        }
        return false;
    }

    static BlockPos blockAt(Level level, QuadcopterEntity drone) {
        AABB box = drone.getBoundingBox();
        for (VoxelShape shape : level.getBlockCollisions(drone, box.deflate(PENETRATION))) {
            AABB bounds = shape.bounds();
            return BlockPos.containing(bounds.getCenter());
        }
        return BlockPos.containing(box.getCenter());
    }

    static Vec3 launch(int[] base, Approach approach) {
        if (approach == Approach.ALONG) {
            return new Vec3(base[0] + BEAM_DX + 0.5, base[1] + BEAM_DY + approach.height, base[2] - BEAM_HALF + 0.5);
        }
        return new Vec3(base[0] + 0.5 + LAUNCH_DX, base[1] + BEAM_DY + approach.height, base[2] + 0.5);
    }

    private static final class Run {
        final Rules rules;
        final List<?> flights;
        final List<String> findings = new ArrayList<>();
        int stage;
        int mark;
        int index;
        double worst;
        int worstTick = -1;
        @Nullable String worstWhere;
        int eyeTick = -1;
        int eyeTicks;
        @Nullable String eyeWhere;
        int crashes;
        @Nullable CompletableFuture<Void> future;

        double lowest;
        int epoch;
        @Nullable CompletableFuture<String> shot;

        Run(Rules rules, List<?> flights) {
            this.rules = rules;
            this.flights = flights;
        }

        StepResult tick(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            CompletableFuture<Void> waiting = this.future;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return this.finish(ctx, StepResult.fail("Server: " + waiting.exceptionNow().getMessage()));
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.future = null;
            }
            int since = ctx.tick() - this.mark;
            switch (this.stage) {
                case 0 -> {
                    if (ctx.tick() < 10) {
                        return StepResult.pending();
                    }
                    Rules r = this.rules;
                    this.future = ctx.onServer(server -> {
                        ServerPlayer player = InteractSelfTests.player(server);
                        ServerLevel level = player.level();
                        int[] base = BASE.get();
                        if (base == null) {
                            int x = player.getBlockX() + SCENE_DX;
                            int z = player.getBlockZ();
                            base = new int[]{x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z};
                            BASE.set(base);
                        }
                        WorldRules.set(server, InteractRules.breakRule(), r.breakBlocks());
                        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, r.crashDisarm() ? 1 : 0);
                        CovVineArena.build(level, base);
                        player.teleportTo(level, base[0] - 6.5, base[1], base[2] - 5.5, Set.of(), -60.0F, 15.0F, true);
                        InteractSelfTests.ARENA.set(new int[]{base[0], base[1], base[2]});
                        InteractSelfTests.fly(player, DroneBuild.FREESTYLE, new Vec3(base[0] + 0.5 + LAUNCH_DX, base[1], base[2] + 0.5));
                        QuadcopterEntity drone = InteractSelfTests.serverDrone(server);
                        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElseThrow();
                        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                        player.setItemSlot(EquipmentSlot.HEAD, goggles);
                    });
                    state.put(InteractSelfTests.FUTURE, this.future);
                    this.next(ctx, 1);
                }
                case 1 -> {
                    QuadcopterEntity flown = session.drone();
                    if (!session.isFpv() || flown == null || flown.getId() != InteractSelfTests.DRONE_ID.get() || session.sim() == null) {
                        return since > 200 ? this.finish(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                    }
                    if (ClientInteractRules.dronesBreakBlocks() != this.rules.breakBlocks()
                            || ClientWorldRules.INSTANCE.on(WorldRuleSpecs.CRASH_DISARM) != this.rules.crashDisarm()) {
                        return since > 100 ? this.finish(ctx, StepResult.fail("Welt-Regeln kommen nicht beim Client an")) : StepResult.pending();
                    }
                    this.next(ctx, 2);
                }
                case 2 -> {
                    DroneSim sim = session.sim();
                    if (sim == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= 10, StabMode.ANGLE);
                    if (!sim.telemetry().armed) {
                        return since > 100 ? this.finish(ctx, StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers)) : StepResult.pending();
                    }
                    this.next(ctx, 3);
                }
                case 3 -> {
                    if (this.index >= this.flights.size()) {
                        this.next(ctx, 6);
                        return StepResult.pending();
                    }
                    int[] base = BASE.get();
                    if (this.flights.get(this.index) instanceof Embed embed) {
                        QuadcopterEntity drone = session.drone();
                        if (drone == null) {
                            return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                        }
                        this.epoch = drone.resetEpoch();
                        Vec3 at = embedAt(base, embed);
                        this.future = ctx.onServer(server -> InteractSelfTests.serverDrone(server).resetTo(at, InteractSelfTests.upright()));
                        this.next(ctx, 10);
                        return StepResult.pending();
                    }
                    Flight flight = (Flight) this.flights.get(this.index);
                    Vec3 at = launch(base, flight.approach());
                    this.future = ctx.onServer(server -> InteractSelfTests.serverDrone(server).resetTo(at, InteractSelfTests.upright()));
                    this.next(ctx, 4);
                }
                case 4 -> {
                    QuadcopterEntity drone = session.drone();
                    DroneSim sim = session.sim();
                    if (drone == null || sim == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    Flight flight = (Flight) this.flights.get(this.index);
                    Vec3 at = launch(BASE.get(), flight.approach());
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) sim.telemetry().hoverThrottleStick), true, StabMode.ANGLE);
                    if (drone.position().distanceTo(at) > 0.2) {
                        return since > RESET_TICKS ? this.finish(ctx, StepResult.fail("Rücksetzen auf den Startpunkt kommt nicht an")) : StepResult.pending();
                    }
                    launchFlight(sim, flight);
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                    this.worst = 0.0;
                    this.worstTick = -1;
                    this.worstWhere = null;
                    this.eyeTick = -1;
                    this.eyeTicks = 0;
                    this.eyeWhere = null;
                    this.crashes = session.crashCount();
                    this.next(ctx, 5);
                }
                case 5 -> {
                    QuadcopterEntity drone = session.drone();
                    Minecraft mc = Minecraft.getInstance();
                    if (drone == null || mc.level == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                    Vec3 eye = mc.gameRenderer.mainCamera().position();
                    if (this.eyeTick < 0 && eyeInside(mc.level, eye)) {
                        this.eyeTick = since;
                        BlockPos eyePos = BlockPos.containing(eye);
                        this.eyeWhere = mc.level.getBlockState(eyePos).getBlock().getName().getString() + " " + eyePos.toShortString() + " Auge "
                                + fmt(eye.x) + " " + fmt(eye.y) + " " + fmt(eye.z) + " Drohne " + fmt(drone.getX()) + " " + fmt(drone.getY()) + " "
                                + fmt(drone.getZ());
                    }
                    if (eyeInside(mc.level, eye)) {
                        this.eyeTicks++;
                    }
                    double depth = penetration(mc.level, drone);
                    if (depth > this.worst) {
                        this.worst = depth;
                        this.worstTick = since;
                        BlockPos pos = blockAt(mc.level, drone);
                        this.worstWhere = mc.level.getBlockState(pos).getBlock().getName().getString() + " " + pos.toShortString() + " bei "
                                + fmt(drone.getX()) + " " + fmt(drone.getY()) + " " + fmt(drone.getZ());
                    }
                    if (this.index == 1 && since == 3 && this.shot == null) {
                        Screenshots.clearOverlays(mc);
                        this.shot = Screenshots.capture(Screenshots.enabled() ? BEAM_SHOT : "screenshots/" + BEAM_SHOT);
                    }
                    if (since < WATCH_TICKS) {
                        return StepResult.pending();
                    }
                    Flight flight = (Flight) this.flights.get(this.index);
                    DroneSim sim = session.sim();
                    int[] base = BASE.get();
                    String line = String.format(Locale.ROOT, "%s mit %.0f m/s: Ende %.2f %.2f %.2f (Boden %+.2f), Abstürze %d, scharf %s, tiefstes Eindringen %.3f m",
                            flight.approach().what, flight.speed(), drone.getX() - base[0], drone.getY() - base[1], drone.getZ() - base[2],
                            drone.getBoundingBox().minY - base[1], session.crashCount() - this.crashes, sim != null && sim.telemetry().armed, this.worst);
                    line += ", Auge im Block " + this.eyeTicks + " Ticks";
                    if (this.eyeTick >= 0) {
                        line += " (zuerst nach " + this.eyeTick + " Ticks in " + this.eyeWhere + ")";
                    }
                    if (this.worst > PENETRATION) {
                        line += " nach " + this.worstTick + " Ticks in " + this.worstWhere;
                    }
                    if (this.worst > PENETRATION || this.eyeTicks > 0) {
                        this.findings.add(line);
                    }
                    ctx.log(line);
                    this.index++;
                    this.next(ctx, 3);
                }
                case 6 -> {
                    CompletableFuture<String> picture = this.shot;
                    if (picture != null && !picture.isDone()) {
                        return since > 100 ? this.finish(ctx, StepResult.fail("Bildschirmfoto kommt nicht")) : StepResult.pending();
                    }
                    if (picture != null && picture.isCompletedExceptionally()) {
                        this.findings.add("Bildschirmfoto: " + picture.exceptionNow().getMessage());
                    }
                    return this.finish(ctx, this.findings.isEmpty() ? StepResult.pass()
                            : StepResult.fail(this.findings.size() + " Fälle enden im Block: " + String.join(" | ", this.findings)));
                }
                case 10, 11 -> {
                    QuadcopterEntity drone = session.drone();
                    DroneSim sim = session.sim();
                    if (drone == null || sim == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    Embed embed = (Embed) this.flights.get(this.index);
                    hold(session, sim, drone, embed, embedAt(BASE.get(), embed));
                    if (this.stage == 10) {
                        if (drone.resetEpoch() == this.epoch || since < 4) {
                            return since > RESET_TICKS ? this.finish(ctx, StepResult.fail("Rücksetzen kommt nicht an")) : StepResult.pending();
                        }
                        if (embed == Embed.BLOCK) {
                            if (since < 26) {
                                return StepResult.pending();
                            }
                            BlockPos cell = BlockPos.containing(drone.getBoundingBox().getCenter());
                            this.future = ctx.onServer(server -> InteractSelfTests.player(server).level().setBlockAndUpdate(cell,
                                    Blocks.STONE.defaultBlockState()));
                            this.next(ctx, 11);
                            return StepResult.pending();
                        }
                    } else if (since < 2) {
                        return StepResult.pending();
                    }
                    this.lowest = Double.POSITIVE_INFINITY;
                    this.worst = 0.0;
                    this.worstTick = -1;
                    this.worstWhere = null;
                    this.next(ctx, 12);
                }
                case 12 -> {
                    QuadcopterEntity drone = session.drone();
                    DroneSim sim = session.sim();
                    Minecraft mc = Minecraft.getInstance();
                    if (drone == null || sim == null || mc.level == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    Embed embed = (Embed) this.flights.get(this.index);
                    int[] base = BASE.get();
                    hold(session, sim, drone, embed, embedAt(base, embed));
                    this.lowest = Math.min(this.lowest, drone.getBoundingBox().minY - base[1]);
                    double depth = penetration(mc.level, drone);
                    if (depth > this.worst) {
                        this.worst = depth;
                        this.worstTick = since;
                        BlockPos pos = blockAt(mc.level, drone);
                        this.worstWhere = mc.level.getBlockState(pos).getBlock().getName().getString() + " " + pos.toShortString();
                    }
                    if (since == 20 && this.shot == null) {
                        Screenshots.clearOverlays(mc);
                        this.shot = Screenshots.capture(Screenshots.enabled() ? embed.shot : "screenshots/" + embed.shot);
                    }
                    if (since < EMBED_TICKS) {
                        return StepResult.pending();
                    }
                    double end = penetration(mc.level, drone);
                    double bottom = drone.getBoundingBox().minY - base[1];
                    String line = String.format(Locale.ROOT, "%s: tiefstes Eindringen %.3f m%s, am Ende %.3f m, Unterkante am Ende %+.3f m, tiefste %+.3f m "
                            + "über der Grasoberkante, Ausweichen %d, scharf %s", embed.what, this.worst,
                            this.worstWhere == null ? "" : " (" + this.worstWhere + " nach " + this.worstTick + " Ticks)", end, bottom, this.lowest,
                            sim.pushOuts(), sim.telemetry().armed);
                    ctx.log(line);
                    boolean sunk = embed != Embed.BLOCK && (bottom < -0.005 || this.lowest < embed.height - 0.02);
                    if (end > PENETRATION || sunk) {
                        this.findings.add(line);
                    }
                    this.index++;
                    this.next(ctx, 3);
                }
                default -> {
                    return StepResult.fail("unbekannter Schritt " + this.stage);
                }
            }
            return StepResult.pending();
        }

        private StepResult finish(SelfTest.StepContext ctx, StepResult result) {
            if (!ctx.state().containsKey("result")) {
                ctx.state().put("result", result);
                ctx.state().put(InteractSelfTests.PHASE, 0);
            }
            StepResult stopped = InteractSelfTests.stop(ctx, server -> {
                WorldRules.set(server, InteractRules.breakRule(), false);
                WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 1);
                int[] base = BASE.get();
                if (base != null) {
                    CovVineArena.clear(InteractSelfTests.player(server).level(), base);
                }
                InteractSelfTests.ARENA.set(null);
                InteractSelfTests.player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            });
            if (stopped.status() == StepResult.Status.PENDING) {
                return stopped;
            }
            StepResult wanted = (StepResult) ctx.state().get("result");
            return stopped.status() == StepResult.Status.FAIL && wanted.status() == StepResult.Status.PASS ? stopped : wanted;
        }

        private void next(SelfTest.StepContext ctx, int stage) {
            this.stage = stage;
            this.mark = ctx.tick();
        }
    }

    static Vec3 embedAt(int[] base, Embed embed) {
        return new Vec3(base[0] + 0.5 + LAUNCH_DX, base[1] + embed.height, base[2] + 0.5);
    }

    static void hold(PilotSession session, DroneSim sim, QuadcopterEntity drone, Embed embed, Vec3 at) {
        if (embed != Embed.BLOCK) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
            return;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        double throttle = sim.telemetry().hoverThrottleStick + 0.3 * (at.y - drone.getY()) - 0.1 * velocity.y;
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.max(0.0, Math.min(1.0, throttle))), true, StabMode.ANGLE);
    }

    static void launchFlight(DroneSim sim, Flight flight) {
        Approach a = flight.approach();
        double climb = Math.toRadians(a.climbDeg);
        double yaw = Math.toRadians(a.yawDeg);
        double horizontal = Math.cos(climb) * flight.speed();
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = horizontal * Math.cos(yaw);
        snapshot.vy = Math.sin(climb) * flight.speed();
        snapshot.vz = horizontal * Math.sin(yaw);
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = false;
        snapshot.armed = true;
        if (a.pitchDeg != 0.0) {
            Quaterniond q = new Quaterniond(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw);
            Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
            q.transform(forward);
            double sign = forward.x > 0.0 ? 1.0 : -1.0;
            Quaterniond pitched = new Quaterniond(q).rotateX(Math.toRadians(a.pitchDeg));
            Vector3d check = new Vector3d(0.0, 0.0, sign);
            pitched.transform(check);
            if (check.y > 0.0) {
                pitched = new Quaterniond(q).rotateX(-Math.toRadians(a.pitchDeg));
            }
            snapshot.qw = pitched.w;
            snapshot.qx = pitched.x;
            snapshot.qy = pitched.y;
            snapshot.qz = pitched.z;
        }
        sim.copyStateFrom(snapshot);
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
